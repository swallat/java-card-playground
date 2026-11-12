package playground.loesungen.uebung01;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Musterloesung (Kopie) von HelloApplet (Aufgabenzettel Uebung 1).
 * Loesungsstellen: Suche nach ">>> LOESUNG".
 * Pruefen:  mise exec -- mvn test -Dtest=Uebung01LoesungTest
 */
public class HelloLoesungApplet extends Applet {

    public static final byte CLA_PLAYGROUND = (byte) 0x80;
    public static final byte INS_HELLO = (byte) 0x01;
    public static final byte INS_ECHO = (byte) 0x02;
    public static final byte INS_VERSION = (byte) 0x03;   // Aufgabe 3

    private static final byte[] HELLO = {'H', 'e', 'l', 'l', 'o', ' ', 'C', 'a', 'r', 'd'};

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new HelloLoesungApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void process(APDU apdu) {
        // >>> LOESUNG AUFGABE 5: Diese drei Zeilen testweise auskommentieren (danach wieder einkommentieren!)
        if (selectingApplet()) {
            return;
        }
        // <<< LOESUNG AUFGABE 5

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
                // >>> LOESUNG AUFGABE 3: Die zwei Bytes 01 00 als Antwort senden.
                //     Hilfe: in buf schreiben, dann apdu.setOutgoingAndSend(offset, laenge).
                buf[0] = 0x01;
                buf[1] = 0x00;
                apdu.setOutgoingAndSend((short) 0, (short) 2);
                // <<< LOESUNG AUFGABE 3
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
