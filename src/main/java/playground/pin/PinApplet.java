package playground.pin;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.OwnerPIN;
import javacard.framework.Util;

/**
 * Uebung 2: PIN-Pruefung mit Fehlbedienungszaehler.
 *
 * Kommandos:
 *   00 20 00 81 [Lc PIN]   VERIFY (ISO 7816-4). Ohne Daten (Case 1): nur Status abfragen.
 *   80 10 00 00 00         GET SECRET (proprietaer), nur nach erfolgreicher PIN.
 */
public class PinApplet extends Applet {

    public static final byte CLA_ISO = (byte) 0x00;
    public static final byte CLA_PROPRIETARY = (byte) 0x80;

    public static final byte INS_VERIFY = (byte) 0x20;      // ISO 7816-4
    public static final byte INS_GET_SECRET = (byte) 0x10;  // eigenes Kommando

    /** P2 von VERIFY = Referenz der PIN. 0x81 = "spezifische PIN dieser Anwendung". */
    public static final byte P2_PIN_REF = (byte) 0x81;

    public static final byte PIN_TRY_LIMIT = 3;
    public static final byte PIN_MAX_LENGTH = 8;

    /** Standard-SWs, die ISO7816 in Java Card 3.0.5 nicht als Konstante kennt. */
    private static final short SW_PIN_TRIES_BASE = (short) 0x63C0;      // 63Cx
    private static final short SW_PIN_BLOCKED = (short) 0x6983;

    /**
     * Initial-PIN "1234" (ASCII 31 32 33 34). NUR fuer die Uebung fest im Code.
     * Real wird sie bei der Personalisierung gesetzt (Install-Parameter oder ein
     * eigenes Personalisierungskommando) und dem Nutzer per PIN-Brief mitgeteilt.
     */
    private static final byte[] INITIAL_PIN = {'1', '2', '3', '4'};

    private static final byte[] SECRET = {'G', 'e', 'h', 'e', 'i', 'm'};

    /**
     * OwnerPIN: persistentes Objekt (EEPROM). Enthaelt Referenz-PIN und
     * Fehlbedienungszaehler. Das Flag "validiert" ist laut Spezifikation transient.
     */
    private final OwnerPIN pin;

    private PinApplet() {
        // Alle Objekte im Konstruktor anlegen (laeuft waehrend install), nie in process().
        pin = new OwnerPIN(PIN_TRY_LIMIT, PIN_MAX_LENGTH);
        pin.update(INITIAL_PIN, (short) 0, (byte) INITIAL_PIN.length);
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new PinApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    /** Wird aufgerufen, wenn ein anderes Applet selektiert wird: PIN-Status verwerfen. */
    public void deselect() {
        pin.reset();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }

        byte[] buf = apdu.getBuffer();
        byte cla = buf[ISO7816.OFFSET_CLA];
        byte ins = buf[ISO7816.OFFSET_INS];

        if (cla == CLA_ISO && ins == INS_VERIFY) {
            verify(apdu);
        } else if (cla == CLA_PROPRIETARY && ins == INS_GET_SECRET) {
            getSecret(apdu);
        } else if (cla != CLA_ISO && cla != CLA_PROPRIETARY) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        } else {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void verify(APDU apdu) {
        byte[] buf = apdu.getBuffer();

        if (buf[ISO7816.OFFSET_P1] != 0x00 || buf[ISO7816.OFFSET_P2] != P2_PIN_REF) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2); // 6A86
        }

        // Gesperrt? Dann gar nicht erst pruefen, auch nicht mit der richtigen PIN.
        if (pin.getTriesRemaining() == 0) {
            ISOException.throwIt(SW_PIN_BLOCKED); // 6983
        }

        short len = apdu.setIncomingAndReceive();

        // Case 1 (keine Daten): nur Status abfragen, ohne einen Versuch zu verbrauchen.
        if (len == 0) {
            if (pin.isValidated()) {
                return; // 9000
            }
            ISOException.throwIt((short) (SW_PIN_TRIES_BASE | pin.getTriesRemaining()));
        }

        // check() verringert den Zaehler VOR dem Vergleich und setzt ihn bei Erfolg zurueck.
        // So hilft es nichts, mitten im Vergleich den Strom zu kappen.
        if (!pin.check(buf, apdu.getOffsetCdata(), (byte) len)) {
            ISOException.throwIt((short) (SW_PIN_TRIES_BASE | pin.getTriesRemaining())); // 63Cx
        }
        // Erfolg: 9000, pin.isValidated() ist jetzt true.
    }

    private void getSecret(APDU apdu) {
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED); // 6982
        }
        byte[] buf = apdu.getBuffer();
        Util.arrayCopyNonAtomic(SECRET, (short) 0, buf, (short) 0, (short) SECRET.length);
        apdu.setOutgoingAndSend((short) 0, (short) SECRET.length);
    }
}
