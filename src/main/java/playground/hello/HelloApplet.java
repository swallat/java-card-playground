package playground.hello;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Uebung 1: Minimales Applet.
 *
 * Lebenszyklus: install() wird einmal beim Laden aufgerufen und registriert das Applet
 * (register()). Danach wird es per SELECT (00 A4 04 00 Lc AID) ausgewaehlt; ab dann landen
 * ALLE weiteren APDUs in process().
 */
public class HelloApplet extends Applet {

    // Eigene Befehle: CLA 0x80 = proprietaer (Bit 8 gesetzt), INS frei waehlbar
    public static final byte CLA_PLAYGROUND = (byte) 0x80;
    public static final byte INS_HELLO = (byte) 0x01; // liefert "Hello Card"
    public static final byte INS_ECHO  = (byte) 0x02; // schickt die Daten zurueck

    private static final byte[] HELLO = {
        'H', 'e', 'l', 'l', 'o', ' ', 'C', 'a', 'r', 'd'
    };

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new HelloApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void process(APDU apdu) {
        // Beim SELECT ruft die Runtime process() mit dem SELECT-APDU auf. Antwort: 9000.
        if (selectingApplet()) {
            return;
        }

        byte[] buf = apdu.getBuffer();

        if (buf[ISO7816.OFFSET_CLA] != CLA_PLAYGROUND) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED); // 6E00
        }

        switch (buf[ISO7816.OFFSET_INS]) {
            case INS_HELLO:
                // Antwortdaten in den APDU-Buffer schreiben und senden
                Util.arrayCopyNonAtomic(HELLO, (short) 0, buf, (short) 0, (short) HELLO.length);
                apdu.setOutgoingAndSend((short) 0, (short) HELLO.length);
                break;

            case INS_ECHO:
                // Eingehende Daten (Lc Bytes) holen und wieder zurueckgeben
                short len = apdu.setIncomingAndReceive();
                // Daten im selben Buffer nach vorne schieben und senden. Rueckgabedaten setzen
                // voraus, dass der Host ein Le geschickt hat (Case 2 / Case 4).
                Util.arrayCopyNonAtomic(buf, apdu.getOffsetCdata(), buf, (short) 0, len);
                apdu.setOutgoingAndSend((short) 0, len);
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED); // 6D00
        }
    }
}
