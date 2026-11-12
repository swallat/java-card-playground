package playground.aufgaben.uebung02;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.OwnerPIN;
import javacard.framework.Util;

/**
 * Aufgaben-Kopie von PinApplet (Aufgabenzettel Uebung 2).
 * Eingabestellen: Suche nach ">>> AUFGABE".
 * Pruefen:  mise exec -- mvn test -Dtest=Uebung02Aufgaben
 *
 *   00 20 00 81 [Lc PIN]                  VERIFY (ohne Daten: Statusabfrage)
 *   80 10 00 00 00                        GET SECRET (nur nach PIN)
 *   00 24 00 81 08 <alt 4> <neu 4>        CHANGE REFERENCE DATA   (Aufgabe 3)
 *   00 2C 00 81 08 <PUK 8>                RESET RETRY COUNTER     (Aufgabe 4)
 */
public class PinAufgabeApplet extends Applet {

    private static final byte INS_VERIFY = (byte) 0x20;
    private static final byte INS_GET_SECRET = (byte) 0x10;
    private static final byte INS_CHANGE_REFERENCE_DATA = (byte) 0x24;
    private static final byte INS_RESET_RETRY_COUNTER = (byte) 0x2C;

    // >>> AUFGABE 2: Fehlbedienungszaehler aendern
    public static final byte PIN_TRY_LIMIT = 3;
    // <<< AUFGABE 2

    /** Fuer den Host: liest das Limit zur Laufzeit (Konstanten wuerden sonst eingebettet). */
    public static byte pinTryLimit() {
        return PIN_TRY_LIMIT;
    }
    private static final byte PIN_MAX_LENGTH = 8;

    private static final byte[] INITIAL_PIN = {'1', '2', '3', '4'};
    private static final byte[] SECRET = {'G', 'e', 'h', 'e', 'i', 'm'};

    private final OwnerPIN pin;

    // >>> AUFGABE 4: Feld fuer die PUK (wird im Konstruktor angelegt)
    private OwnerPIN puk;
    // <<< AUFGABE 4

    private PinAufgabeApplet() {
        pin = new OwnerPIN(PIN_TRY_LIMIT, PIN_MAX_LENGTH);
        pin.update(INITIAL_PIN, (short) 0, (byte) INITIAL_PIN.length);

        // >>> AUFGABE 4: PUK anlegen, 10 Versuche, Wert "12345678"
        //     Hilfe: new OwnerPIN((byte) 10, (byte) 8) und update(...)

        // <<< AUFGABE 4
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new PinAufgabeApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
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
            case INS_VERIFY:                verify(apdu); break;
            case INS_GET_SECRET:            getSecret(apdu); break;
            case INS_CHANGE_REFERENCE_DATA: changeReferenceData(apdu); break;
            case INS_RESET_RETRY_COUNTER:   resetRetryCounter(apdu); break;
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void verify(APDU apdu) {
        if (pin.getTriesRemaining() == 0) {
            ISOException.throwIt((short) 0x6983);
        }
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        if (len == 0) {
            if (pin.isValidated()) {
                return;
            }
            ISOException.throwIt((short) (0x63C0 | pin.getTriesRemaining()));
        }
        if (!pin.check(buf, apdu.getOffsetCdata(), (byte) len)) {
            ISOException.throwIt((short) (0x63C0 | pin.getTriesRemaining()));
        }
    }

    private void getSecret(APDU apdu) {
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        byte[] buf = apdu.getBuffer();
        Util.arrayCopyNonAtomic(SECRET, (short) 0, buf, (short) 0, (short) SECRET.length);
        apdu.setOutgoingAndSend((short) 0, (short) SECRET.length);
    }

    private void changeReferenceData(APDU apdu) {
        if (pin.getTriesRemaining() == 0) {
            ISOException.throwIt((short) 0x6983);
        }
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        short off = apdu.getOffsetCdata();
        if (len != 8) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        // Daten ab buf[off]: alte PIN (4 Bytes) || neue PIN (4 Bytes)
        // >>> AUFGABE 3: alte PIN pruefen (bei Fehler 63Cx wie in verify), dann neue PIN setzen.
        //     Danach diese Zeile entfernen:
        ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
        // <<< AUFGABE 3
    }

    private void resetRetryCounter(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        short off = apdu.getOffsetCdata();
        if (len != 8) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        // Daten ab buf[off]: PUK (8 Bytes)
        // >>> AUFGABE 4: PUK pruefen (gesperrt: 6983, falsch: 63Cx mit den PUK-Restversuchen),
        //     bei Erfolg die PIN entsperren: pin.resetAndUnblock(). Danach diese Zeile entfernen:
        ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
        // <<< AUFGABE 4
    }
}
