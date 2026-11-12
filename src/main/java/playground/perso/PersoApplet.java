package playground.perso;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.OwnerPIN;
import javacard.framework.Util;
import javacard.security.AESKey;
import javacard.security.KeyBuilder;
import javacard.security.Signature;
import javacardx.crypto.Cipher;

/**
 * Zwischenuebung: vereinfachte Personalisierung.
 *
 * Lebenszyklus des Applets:
 *   PRE-PERSO     nach der Installation. Kennt nur Seriennummer + kartenindividuelle
 *                 Transportschluessel (K_ENC, K_MAC). Nutzerfunktionen sind gesperrt.
 *   PERSONALIZED  nach STORE DATA "Ende". Transportschluessel geloescht, keine
 *                 Personalisierung mehr moeglich, Nutzerfunktionen frei.
 *
 * Kommandos:
 *   80 CA 9F 7F 00             GET DATA: Seriennummer lesen (wie CPLC bei GlobalPlatform)
 *   80 E2 <Tag> 00 Lc <Daten>  STORE DATA: Daten || CBC-MAC (16 Bytes)
 *                              Tag 01 = PIN (verschluesselt), 02 = Name (Klartext), 7F = Ende
 *   00 20 00 81 Lc <PIN>       VERIFY (erst nach Personalisierung)
 *   80 12 00 00 00             GET HOLDER: Name lesen (nur nach PIN)
 */
public class PersoApplet extends Applet {

    public static final byte TAG_PIN = 0x01;
    public static final byte TAG_HOLDER = 0x02;
    public static final byte TAG_END = 0x7F;

    private static final byte INS_GET_DATA = (byte) 0xCA;
    private static final byte INS_STORE_DATA = (byte) 0xE2;
    private static final byte INS_VERIFY = (byte) 0x20;
    private static final byte INS_GET_HOLDER = (byte) 0x12;

    private static final byte STATE_PREPERSO = 0x01;
    private static final byte STATE_PERSONALIZED = 0x0F;

    private static final short SERIAL_LEN = 8;
    private static final short BLOCK = 16;  // AES-Blockgroesse = MAC-Laenge
    private static final short INSTALL_PARAM_LEN = SERIAL_LEN + 2 * BLOCK;  // Seriennr. + K_ENC + K_MAC

    // --- persistent (EEPROM) ---
    private byte state;
    private final byte[] serial = new byte[SERIAL_LEN];
    private final AESKey kEnc;   // kartenindividuell, aus dem Masterschluessel abgeleitet
    private final AESKey kMac;
    private final OwnerPIN pin;
    private boolean pinLoaded;
    private final byte[] holder = new byte[32];
    private short holderLen;

    // --- Krypto-Objekte (einmal anlegen, immer wiederverwenden) ---
    private final Cipher aesCbc;
    private final Signature cbcMac;

    // --- transient (RAM): Arbeitspuffer, wird beim Deselect geloescht ---
    private final byte[] scratch;

    private PersoApplet(byte[] bArray, short off, byte len) {
        if (len != INSTALL_PARAM_LEN) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH); // Installation schlaegt fehl
        }
        Util.arrayCopy(bArray, off, serial, (short) 0, SERIAL_LEN);

        kEnc = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
        kEnc.setKey(bArray, (short) (off + SERIAL_LEN));
        kMac = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
        kMac.setKey(bArray, (short) (off + SERIAL_LEN + BLOCK));

        aesCbc = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);
        cbcMac = Signature.getInstance(Signature.ALG_AES_MAC_128_NOPAD, false);

        pin = new OwnerPIN((byte) 3, (byte) 8);
        scratch = JCSystem.makeTransientByteArray((short) 64, JCSystem.CLEAR_ON_DESELECT);
        state = STATE_PREPERSO;
    }

    /**
     * bArray im GlobalPlatform-Format:
     * [Laenge][AID] [Laenge][Privileges] [Laenge][Applet-Parameter = Seriennr. || K_ENC || K_MAC]
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        short off = bOffset;
        byte aidLen = bArray[off];
        short aidOff = (short) (off + 1);
        off = (short) (off + 1 + aidLen);
        byte privLen = bArray[off];
        off = (short) (off + 1 + privLen);
        byte paramLen = bArray[off];
        off++;
        new PersoApplet(bArray, off, paramLen).register(bArray, aidOff, aidLen);
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
            case INS_GET_DATA:   getSerial(apdu); break;
            case INS_STORE_DATA: storeData(apdu); break;
            case INS_VERIFY:     verify(apdu); break;
            case INS_GET_HOLDER: getHolder(apdu); break;
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    /** Die Personalisierungsmaschine muss wissen, welche Karte vor ihr liegt. */
    private void getSerial(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_P1] != (byte) 0x9F || buf[ISO7816.OFFSET_P2] != (byte) 0x7F) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
        Util.arrayCopyNonAtomic(serial, (short) 0, buf, (short) 0, SERIAL_LEN);
        apdu.setOutgoingAndSend((short) 0, SERIAL_LEN);
    }

    private void storeData(APDU apdu) {
        if (state != STATE_PREPERSO) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED); // 6985: schon personalisiert
        }
        byte[] buf = apdu.getBuffer();
        byte tag = buf[ISO7816.OFFSET_P1];
        short len = apdu.setIncomingAndReceive();
        short cdata = apdu.getOffsetCdata();

        if (len < BLOCK || len > (short) (BLOCK + holder.length)) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short payloadLen = (short) (len - BLOCK);

        // 1. Integritaet und Herkunft pruefen: MAC ueber (Tag || Nutzdaten), ISO-9797-Padding.
        //    Der Tag ist mit drin, damit man verschluesselte PIN-Daten nicht als "Name" einspielen kann.
        scratch[0] = tag;
        Util.arrayCopyNonAtomic(buf, cdata, scratch, (short) 1, payloadLen);
        short macInputLen = pad(scratch, (short) (1 + payloadLen));
        cbcMac.init(kMac, Signature.MODE_VERIFY);
        if (!cbcMac.verify(scratch, (short) 0, macInputLen, buf, (short) (cdata + payloadLen), BLOCK)) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED); // 6982
        }

        // 2. Datenelement verarbeiten
        switch (tag) {
            case TAG_PIN:
                if (payloadLen != BLOCK) {
                    ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                }
                // Erst HIER, in der Karte, wird die PIN sichtbar: entschluesseln mit K_ENC
                aesCbc.init(kEnc, Cipher.MODE_DECRYPT);
                aesCbc.doFinal(buf, cdata, BLOCK, scratch, (short) 0);
                byte pinLen = scratch[0];                      // Format: [Laenge][PIN][00...]
                if (pinLen < 4 || pinLen > 8) {
                    Util.arrayFillNonAtomic(scratch, (short) 0, (short) scratch.length, (byte) 0);
                    ISOException.throwIt(ISO7816.SW_WRONG_DATA);
                }
                pin.update(scratch, (short) 1, pinLen);
                pinLoaded = true;
                Util.arrayFillNonAtomic(scratch, (short) 0, (short) scratch.length, (byte) 0); // Klartext loeschen
                break;

            case TAG_HOLDER:
                // Nicht geheim, daher nur MAC-geschuetzt. arrayCopy (atomar), weil Ziel im EEPROM liegt.
                Util.arrayCopy(buf, cdata, holder, (short) 0, payloadLen);
                holderLen = payloadLen;
                break;

            case TAG_END:
                if (!pinLoaded || holderLen == 0) {
                    ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED); // unvollstaendig
                }
                // Reihenfolge ist Absicht: erst den Zustand umschalten (sperrt STORE DATA),
                // dann die Transportschluessel loeschen. Saubere Loesung: Transaktion -> Uebung 4.
                state = STATE_PERSONALIZED;
                kEnc.clearKey();
                kMac.clearKey();
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
    }

    private void verify(APDU apdu) {
        if (state != STATE_PERSONALIZED) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED); // Karte noch nicht ausgegeben
        }
        if (pin.getTriesRemaining() == 0) {
            ISOException.throwIt((short) 0x6983);
        }
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        if (!pin.check(buf, apdu.getOffsetCdata(), (byte) len)) {
            ISOException.throwIt((short) (0x63C0 | pin.getTriesRemaining()));
        }
    }

    private void getHolder(APDU apdu) {
        if (state != STATE_PERSONALIZED) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        byte[] buf = apdu.getBuffer();
        Util.arrayCopyNonAtomic(holder, (short) 0, buf, (short) 0, holderLen);
        apdu.setOutgoingAndSend((short) 0, holderLen);
    }

    /** ISO/IEC 9797-1 Methode 2: 0x80 anhaengen, mit 0x00 auf Blockgrenze auffuellen. */
    private static short pad(byte[] b, short len) {
        b[len++] = (byte) 0x80;
        while ((len & 0x0F) != 0) {
            b[len++] = 0x00;
        }
        return len;
    }
}
