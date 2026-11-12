package playground.aufgaben.uebung05;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.OwnerPIN;
import javacard.framework.Util;
import javacard.security.ECPrivateKey;
import javacard.security.ECPublicKey;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import javacard.security.Signature;
import playground.sign.SignApplet;

/**
 * Aufgaben-Kopie von SignApplet (Aufgabenzettel Uebung 3 - Signatur).
 * Eingabestellen: Suche nach ">>> AUFGABE".
 * Pruefen:  mise exec -- mvn test -Dtest=Uebung05Aufgaben
 *
 *   00 20 00 81 Lc <PIN>          VERIFY
 *   00 46 80 00 00                GENERATE KEY PAIR (P-256) -> 04 || X || Y
 *   00 2A 9E 9A Lc <Daten> 00     signieren: Karte hasht selbst (SHA-256)
 *   00 2A 9E 90 20 <Hash 32> 00   signieren: Host hat gehasht (P2 90 = Hashwert)  (Aufgabe 3)
 */
public class SignAufgabeApplet extends Applet {

    private static final short P1P2_SIGN_DATA = (short) 0x9E9A;
    private static final short P1P2_SIGN_HASH = (short) 0x9E90;

    private final OwnerPIN pin;
    private final ECPrivateKey privateKey;
    private final ECPublicKey publicKey;
    private final KeyPair keyPair;
    private boolean keyGenerated;
    private final Signature ecdsa;
    private final byte[] sigBuffer;

    private SignAufgabeApplet() {
        pin = new OwnerPIN((byte) 3, (byte) 8);
        byte[] initial = {'1', '2', '3', '4'};
        pin.update(initial, (short) 0, (byte) initial.length);
        privateKey = (ECPrivateKey) KeyBuilder.buildKey(KeyBuilder.TYPE_EC_FP_PRIVATE, KeyBuilder.LENGTH_EC_FP_256, false);
        publicKey = (ECPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_EC_FP_PUBLIC, KeyBuilder.LENGTH_EC_FP_256, false);
        SignApplet.setP256(privateKey);
        SignApplet.setP256(publicKey);
        keyPair = new KeyPair(publicKey, privateKey);
        ecdsa = Signature.getInstance(Signature.ALG_ECDSA_SHA_256, false);
        sigBuffer = JCSystem.makeTransientByteArray((short) 80, JCSystem.CLEAR_ON_DESELECT);
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new SignAufgabeApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void deselect() {
        pin.reset();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        switch (buf[ISO7816.OFFSET_INS]) {
            case (byte) 0x20: verify(apdu); break;
            case (byte) 0x46: generate(apdu); break;
            case (byte) 0x2A: pso(apdu); break;
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void verify(APDU apdu) {
        if (pin.getTriesRemaining() == 0) {
            ISOException.throwIt((short) 0x6983);
        }
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        if (!pin.check(buf, apdu.getOffsetCdata(), (byte) len)) {
            ISOException.throwIt((short) (0x63C0 | pin.getTriesRemaining()));
        }
    }

    private void generate(APDU apdu) {
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        keyPair.genKeyPair();
        keyGenerated = true;
        byte[] buf = apdu.getBuffer();
        short len = publicKey.getW(buf, (short) 0);
        apdu.setOutgoingAndSend((short) 0, len);
    }

    private void pso(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short p1p2 = Util.getShort(buf, ISO7816.OFFSET_P1);
        if (p1p2 != P1P2_SIGN_DATA && p1p2 != P1P2_SIGN_HASH) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        if (!keyGenerated) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        short len = apdu.setIncomingAndReceive();
        short cdata = apdu.getOffsetCdata();
        short sigLen = 0;

        ecdsa.init(privateKey, Signature.MODE_SIGN);
        if (p1p2 == P1P2_SIGN_DATA) {
            sigLen = ecdsa.sign(buf, cdata, len, sigBuffer, (short) 0);
        } else {
            if (len != 32) {
                ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            }
            // >>> AUFGABE 3: Den 32-Byte-Hash (ab buf[cdata]) signieren, Ergebnis nach sigBuffer,
            //     Laenge in sigLen. Hilfe: ecdsa.signPreComputedHash(...). Danach diese Zeile entfernen:
            ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            // <<< AUFGABE 3
        }

        Util.arrayCopyNonAtomic(sigBuffer, (short) 0, buf, (short) 0, sigLen);
        apdu.setOutgoingAndSend((short) 0, sigLen);

        // >>> AUFGABE 2: PIN pro Signatur - nach jeder Signatur die Freischaltung zuruecksetzen

        // <<< AUFGABE 2
    }
}
