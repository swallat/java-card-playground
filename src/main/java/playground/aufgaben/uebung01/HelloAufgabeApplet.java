package playground.aufgaben.uebung01;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Aufgaben-Kopie von HelloApplet (Aufgabenzettel Uebung 1).
 * Eingabestellen: Suche nach ">>> AUFGABE".
 * Pruefen:  mise exec -- mvn test -Dtest=Uebung01Aufgaben
 */
public class HelloAufgabeApplet extends Applet {

    public static final byte CLA_PLAYGROUND = (byte) 0x80;
    public static final byte INS_HELLO = (byte) 0x01;
    public static final byte INS_ECHO = (byte) 0x02;
    public static final byte INS_VERSION = (byte) 0x03;   // Aufgabe 3

    private static final byte[] HELLO = {'H', 'e', 'l', 'l', 'o', ' ', 'C', 'a', 'r', 'd'};

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new HelloAufgabeApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void process(APDU apdu) {
        // >>> AUFGABE 5: Diese drei Zeilen testweise auskommentieren (danach wieder einkommentieren!)
        if (selectingApplet()) {
            return;
        }
        // <<< AUFGABE 5

        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_CLA] != CLA_PLAYGROUND) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        switch (buf[ISO7816.OFFSET_INS]) {
            case INS_HELLO:
                Util.arrayCopyNonAtomic(HELLO, (short) 0, buf, (short) 0, (short) HELLO.length);
                apdu.setOutgoingAndSend((short) 0, (short) HELLO.length);
                break;

            case INS_ECHO:
                short len = apdu.setIncomingAndReceive();
                Util.arrayCopyNonAtomic(buf, apdu.getOffsetCdata(), buf, (short) 0, len);
                apdu.setOutgoingAndSend((short) 0, len);
                break;

            case INS_VERSION:
                // >>> AUFGABE 3: Die zwei Bytes 01 00 als Antwort senden.
                //     Hilfe: in buf schreiben, dann apdu.setOutgoingAndSend(offset, laenge).
                //     Danach diese Zeile entfernen:
                ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
                // <<< AUFGABE 3
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
