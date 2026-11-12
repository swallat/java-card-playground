package playground.loesungen.uebung03;

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
 * Musterloesung (Kopie) von PersoApplet, ohne K_SCRIPT (Aufgabenzettel Zwischenuebung Personalisierung).
 * Loesungs- und Breakpoint-Stellen: Suche nach ">>> LOESUNG".
 * Pruefen:  mise exec -- mvn test -Dtest=Uebung03LoesungTest
 */
public class PersoOhneTagLoesungApplet extends Applet {

    // >>> LOESUNG AUFGABE 3: auf false setzen = Tag NICHT im MAC (gilt fuer Karte UND Host)
    public static final boolean TAG_IM_MAC = false;   // Variante fuer Aufgabe 3
    // <<< LOESUNG AUFGABE 3

    /** Fuer den Host: liest den Schalter zur Laufzeit (Konstanten wuerden sonst eingebettet). */
    public static boolean tagImMac() {
        return TAG_IM_MAC;
    }

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
    private static final short BLOCK = 16;
    private static final short INSTALL_PARAM_LEN = SERIAL_LEN + 2 * BLOCK;

    private byte state;
    private final byte[] serial = new byte[SERIAL_LEN];
    private final AESKey kEnc;
    private final AESKey kMac;
    private final OwnerPIN pin;
    private boolean pinLoaded;
    private final byte[] holder = new byte[32];
    private short holderLen;

    private final Cipher aesCbc;
    private final Signature cbcMac;
    private final byte[] scratch;

    private PersoOhneTagLoesungApplet(byte[] bArray, short off, byte len) {
        if (len != INSTALL_PARAM_LEN) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
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

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        short off = bOffset;
        byte aidLen = bArray[off];
        short aidOff = (short) (off + 1);
        off = (short) (off + 1 + aidLen);
        byte privLen = bArray[off];
        off = (short) (off + 1 + privLen);
        byte paramLen = bArray[off];
        off++;
        // >>> LOESUNG AUFGABE 1a: Breakpoint hier - bArray ansehen (Ansicht auf Hex umstellen)
        new PersoOhneTagLoesungApplet(bArray, off, paramLen).register(bArray, aidOff, aidLen);
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

    private void getSerial(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        Util.arrayCopyNonAtomic(serial, (short) 0, buf, (short) 0, SERIAL_LEN);
        apdu.setOutgoingAndSend((short) 0, SERIAL_LEN);
    }

    private void storeData(APDU apdu) {
        if (state != STATE_PREPERSO) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        byte[] buf = apdu.getBuffer();
        byte tag = buf[ISO7816.OFFSET_P1];
        short len = apdu.setIncomingAndReceive();
        short cdata = apdu.getOffsetCdata();
        if (len < BLOCK || len > (short) (BLOCK + holder.length)) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short payloadLen = (short) (len - BLOCK);

        short macInputLen;
        if (TAG_IM_MAC) {
            scratch[0] = tag;
            Util.arrayCopyNonAtomic(buf, cdata, scratch, (short) 1, payloadLen);
            macInputLen = pad(scratch, (short) (1 + payloadLen));
        } else {
            Util.arrayCopyNonAtomic(buf, cdata, scratch, (short) 0, payloadLen);
            macInputLen = pad(scratch, payloadLen);
        }
        // >>> LOESUNG AUFGABE 1b: Breakpoint hier - MAC-Eingabe in scratch und macInputLen ansehen
        cbcMac.init(kMac, Signature.MODE_VERIFY);
        if (!cbcMac.verify(scratch, (short) 0, macInputLen, buf, (short) (cdata + payloadLen), BLOCK)) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }

        switch (tag) {
            case TAG_PIN:
                if (payloadLen != BLOCK) {
                    ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
                }
                aesCbc.init(kEnc, Cipher.MODE_DECRYPT);
                aesCbc.doFinal(buf, cdata, BLOCK, scratch, (short) 0);
                // >>> LOESUNG AUFGABE 1c: Breakpoint hier - PIN im Klartext in scratch, und was steht dahinter?
                byte pinLen = scratch[0];
                if (pinLen < 4 || pinLen > 8) {
                    Util.arrayFillNonAtomic(scratch, (short) 0, (short) scratch.length, (byte) 0);
                    ISOException.throwIt(ISO7816.SW_WRONG_DATA);
                }
                pin.update(scratch, (short) 1, pinLen);
                pinLoaded = true;
                Util.arrayFillNonAtomic(scratch, (short) 0, (short) scratch.length, (byte) 0);
                break;

            case TAG_HOLDER:
                Util.arrayCopy(buf, cdata, holder, (short) 0, payloadLen);
                holderLen = payloadLen;
                break;

            case TAG_END:
                if (!pinLoaded || holderLen == 0) {
                    ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
                }
                // >>> LOESUNG AUFGABE 5 (Gedankenexperiment): Was waere, wenn die beiden clearKey()-Zeilen
                //     VOR dem Statuswechsel stuenden und dazwischen der Strom ausfiele?
                state = STATE_PERSONALIZED;
                kEnc.clearKey();
                kMac.clearKey();
                // <<< LOESUNG AUFGABE 5
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
    }

    private void verify(APDU apdu) {
        if (state != STATE_PERSONALIZED) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
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

    private static short pad(byte[] b, short len) {
        b[len++] = (byte) 0x80;
        while ((len & 0x0F) != 0) {
            b[len++] = 0x00;
        }
        return len;
    }
}
