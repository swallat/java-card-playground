package playground.sign;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.OwnerPIN;
import javacard.framework.Util;
import javacard.security.ECKey;
import javacard.security.ECPrivateKey;
import javacard.security.ECPublicKey;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import javacard.security.Signature;

/**
 * Uebung 3: Schluesselpaar auf der Karte erzeugen und signieren.
 *
 * Kommandos (angelehnt an ISO 7816-8):
 *   00 20 00 81 Lc <PIN>      VERIFY
 *   00 46 80 00 00            GENERATE ASYMMETRIC KEY PAIR -> oeffentlicher Schluessel (04 || X || Y)
 *   00 46 81 00 00            vorhandenen oeffentlichen Schluessel lesen
 *   00 2A 9E 9A Lc <Daten> 00 PSO: COMPUTE DIGITAL SIGNATURE -> ECDSA-SHA256 (DER)
 *
 * Es gibt KEIN Kommando, das den privaten Schluessel ausgibt.
 */
public class SignApplet extends Applet {

    private static final byte INS_VERIFY = (byte) 0x20;
    private static final byte INS_GENERATE_KEY_PAIR = (byte) 0x46;
    private static final byte INS_PSO = (byte) 0x2A;

    private static final byte P1_GENERATE = (byte) 0x80;
    private static final byte P1_READ_PUBLIC = (byte) 0x81;
    private static final short P1P2_COMPUTE_SIGNATURE = (short) 0x9E9A; // 9E = Signatur ausgeben, 9A = zu signierende Daten

    private static final byte[] INITIAL_PIN = {'1', '2', '3', '4'};

    // --- NIST P-256 (secp256r1). Die Karte kennt die Kurve nicht von selbst. ---
    private static final byte[] P256_P = {
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00, 0x01,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final byte[] P256_A = {
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00, 0x01,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFC};
    private static final byte[] P256_B = {
        0x5A, (byte) 0xC6, 0x35, (byte) 0xD8, (byte) 0xAA, 0x3A, (byte) 0x93, (byte) 0xE7,
        (byte) 0xB3, (byte) 0xEB, (byte) 0xBD, 0x55, 0x76, (byte) 0x98, (byte) 0x86, (byte) 0xBC,
        0x65, 0x1D, 0x06, (byte) 0xB0, (byte) 0xCC, 0x53, (byte) 0xB0, (byte) 0xF6,
        0x3B, (byte) 0xCE, 0x3C, 0x3E, 0x27, (byte) 0xD2, 0x60, 0x4B};
    private static final byte[] P256_G = {  // Basispunkt, unkomprimiert: 04 || X || Y
        0x04,
        0x6B, 0x17, (byte) 0xD1, (byte) 0xF2, (byte) 0xE1, 0x2C, 0x42, 0x47,
        (byte) 0xF8, (byte) 0xBC, (byte) 0xE6, (byte) 0xE5, 0x63, (byte) 0xA4, 0x40, (byte) 0xF2,
        0x77, 0x03, 0x7D, (byte) 0x81, 0x2D, (byte) 0xEB, 0x33, (byte) 0xA0,
        (byte) 0xF4, (byte) 0xA1, 0x39, 0x45, (byte) 0xD8, (byte) 0x98, (byte) 0xC2, (byte) 0x96,
        0x4F, (byte) 0xE3, 0x42, (byte) 0xE2, (byte) 0xFE, 0x1A, 0x7F, (byte) 0x9B,
        (byte) 0x8E, (byte) 0xE7, (byte) 0xEB, 0x4A, 0x7C, 0x0F, (byte) 0x9E, 0x16,
        0x2B, (byte) 0xCE, 0x33, 0x57, 0x6B, 0x31, 0x5E, (byte) 0xCE,
        (byte) 0xCB, (byte) 0xB6, 0x40, 0x68, 0x37, (byte) 0xBF, 0x51, (byte) 0xF5};
    private static final byte[] P256_N = {  // Ordnung des Basispunkts
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00,
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
        (byte) 0xBC, (byte) 0xE6, (byte) 0xFA, (byte) 0xAD, (byte) 0xA7, 0x17, (byte) 0x9E, (byte) 0x84,
        (byte) 0xF3, (byte) 0xB9, (byte) 0xCA, (byte) 0xC2, (byte) 0xFC, 0x63, 0x25, 0x51};

    // --- persistent ---
    private final OwnerPIN pin;
    private final ECPrivateKey privateKey;   // verlaesst die Karte nie
    private final ECPublicKey publicKey;
    private final KeyPair keyPair;
    private boolean keyGenerated;

    // --- Krypto-Engine und RAM-Puffer fuer die Signatur ---
    private final Signature ecdsa;
    private final byte[] sigBuffer;

    private SignApplet() {
        pin = new OwnerPIN((byte) 3, (byte) 8);
        pin.update(INITIAL_PIN, (short) 0, (byte) INITIAL_PIN.length);

        // Leere Schluesselobjekte anlegen und mit den Kurvenparametern versehen
        privateKey = (ECPrivateKey) KeyBuilder.buildKey(KeyBuilder.TYPE_EC_FP_PRIVATE, KeyBuilder.LENGTH_EC_FP_256, false);
        publicKey = (ECPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_EC_FP_PUBLIC, KeyBuilder.LENGTH_EC_FP_256, false);
        setP256(privateKey);
        setP256(publicKey);
        keyPair = new KeyPair(publicKey, privateKey);

        ecdsa = Signature.getInstance(Signature.ALG_ECDSA_SHA_256, false);
        sigBuffer = JCSystem.makeTransientByteArray((short) 80, JCSystem.CLEAR_ON_DESELECT);
    }

    /** Setzt die Kurvenparameter von NIST P-256 (auch von den Aufgaben-Applets genutzt). */
    public static void setP256(ECKey key) {
        key.setFieldFP(P256_P, (short) 0, (short) P256_P.length);
        key.setA(P256_A, (short) 0, (short) P256_A.length);
        key.setB(P256_B, (short) 0, (short) P256_B.length);
        key.setG(P256_G, (short) 0, (short) P256_G.length);
        key.setR(P256_N, (short) 0, (short) P256_N.length);
        key.setK((short) 1);   // Kofaktor
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new SignApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void deselect() {
        pin.reset();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_CLA] != 0x00) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
        switch (buf[ISO7816.OFFSET_INS]) {
            case INS_VERIFY:            verify(apdu); break;
            case INS_GENERATE_KEY_PAIR: generateOrRead(apdu); break;
            case INS_PSO:               computeSignature(apdu); break;
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

    private void generateOrRead(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        byte p1 = buf[ISO7816.OFFSET_P1];

        if (p1 == P1_GENERATE) {
            if (!pin.isValidated()) {
                ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
            }
            // Hier entsteht der private Schluessel: aus dem Zufallszahlengenerator der Karte,
            // direkt im Schluesselobjekt. Er existiert zu keinem Zeitpunkt ausserhalb der Karte.
            keyPair.genKeyPair();
            keyGenerated = true;
        } else if (p1 == P1_READ_PUBLIC) {
            if (!keyGenerated) {
                ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
            }
        } else {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }

        // Nur der oeffentliche Punkt W = d * G geht raus (65 Bytes: 04 || X || Y)
        short len = publicKey.getW(buf, (short) 0);
        apdu.setOutgoingAndSend((short) 0, len);
    }

    private void computeSignature(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        if (Util.getShort(buf, ISO7816.OFFSET_P1) != P1P2_COMPUTE_SIGNATURE) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED); // 6982
        }
        if (!keyGenerated) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);      // 6985
        }
        short len = apdu.setIncomingAndReceive();

        // Die Karte hasht (SHA-256) und signiert in einem Schritt. Ausgabe in einen eigenen
        // Puffer, damit Ein- und Ausgabe im APDU-Buffer sich nicht ueberschreiben.
        ecdsa.init(privateKey, Signature.MODE_SIGN);
        short sigLen = ecdsa.sign(buf, apdu.getOffsetCdata(), len, sigBuffer, (short) 0);

        Util.arrayCopyNonAtomic(sigBuffer, (short) 0, buf, (short) 0, sigLen);
        apdu.setOutgoingAndSend((short) 0, sigLen);
    }
}
