package playground.aufgaben.uebung03;

import org.junit.Before;
import org.junit.Test;
import playground.aufgaben.Pruefe;
import playground.support.Card;

import javax.smartcardio.CommandAPDU;

import static org.junit.Assert.assertEquals;

/**
 * Testbed zum Aufgabenzettel Zwischenuebung Personalisierung (aufgaben/uebung-03-personalisierung.md).
 *
 * Eingabestellen: ">>> AUFGABE" in dieser Datei und in PersoAufgabeApplet.
 * Ausfuehren:     mise exec -- mvn test -Dtest=Uebung03Aufgaben
 */
public class Uebung03Aufgaben {

    private static final String AID = "F0 00 00 00 01 13";
    private static final byte[] SERIAL = Card.hex("12 34 56 78 9A BC DE F0");

    // >>> AUFGABE 2: "andere" Seriennummer (zuerst wie hier, dann gleich der echten SERIAL setzen)
    //     und deine Vorhersage des Statusworts fuer das PIN-STORE-DATA
    static final byte[] A2_ANDERE_SERIENNUMMER = Card.hex("00 00 00 00 00 00 00 01");
    static final int A2_VORHERSAGE_SW = 0x0000;
    // <<< AUFGABE 2

    // >>> AUFGABE 3: Vorhersage fuer "PIN-Kryptogramm als Name einspielen" (Tag getauscht).
    //     Einmal mit TAG_IM_MAC = true, dann mit false in PersoAufgabeApplet.
    static final int A3_VORHERSAGE_SW = 0x0000;
    // <<< AUFGABE 3

    // >>> AUFGABE 4: Vorhersage fuer das zweite Senden desselben PIN-APDUs (vor dem Abschluss)
    static final int A4_VORHERSAGE_SW = 0x0000;
    // <<< AUFGABE 4

    private Card card;

    @Before
    public void setUp() {
        card = new Card(AID, PersoAufgabeApplet.class, DataPreparationAufgabe.installParams(SERIAL));
        card.select();
    }

    /** Aufgabe 1: Diesen Test im Debugger starten (Breakpoints siehe ">>> AUFGABE 1" im Applet). */
    @Test
    public void aufgabe1_debuggerEinstieg() {
        DataPreparationAufgabe dp = new DataPreparationAufgabe(SERIAL);
        assertEquals(0x9000, card.send(dp.storePin("4711")).getSW());
        assertEquals(0x9000, card.send(dp.storeHolder("Max Mustermann")).getSW());
        assertEquals(0x9000, card.send(dp.endPerso()).getSW());
        assertEquals(0x9000, card.send(0x00, 0x20, 0x00, 0x81, "4711".getBytes()).getSW());
    }

    @Test
    public void aufgabe2_falscheKarte() {
        DataPreparationAufgabe other = new DataPreparationAufgabe(A2_ANDERE_SERIENNUMMER);
        int sw = card.send(other.storePin("4711")).getSW();
        Pruefe.vorhersage("Aufgabe 2", A2_VORHERSAGE_SW, sw);
    }

    @Test
    public void aufgabe3_tagGetauscht() {
        CommandAPDU pinApdu = new DataPreparationAufgabe(SERIAL).storePin("4711");
        int sw = card.send(0x80, 0xE2, PersoAufgabeApplet.TAG_HOLDER, 0x00, pinApdu.getData()).getSW();
        System.out.println("  Aufgabe 3: TAG_IM_MAC = " + PersoAufgabeApplet.tagImMac());
        Pruefe.vorhersage("Aufgabe 3", A3_VORHERSAGE_SW, sw);
    }

    @Test
    public void aufgabe4_replayVorAbschluss() {
        DataPreparationAufgabe dp = new DataPreparationAufgabe(SERIAL);
        CommandAPDU pinApdu = dp.storePin("4711");
        assertEquals(0x9000, card.send(pinApdu).getSW());

        int sw = 0;
        // >>> AUFGABE 4: dasselbe APDU (pinApdu) noch einmal an die Karte senden und das
        //     Statuswort in sw speichern. Hilfe: card.send(...).getSW()

        // <<< AUFGABE 4

        if (sw == 0) {
            org.junit.Assert.fail("Aufgabe 4: APDU erneut senden und sw setzen.");
        }
        Pruefe.vorhersage("Aufgabe 4", A4_VORHERSAGE_SW, sw);
    }
}
