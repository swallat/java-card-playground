package playground.loesungen.uebung03;

import org.junit.Before;
import org.junit.Test;
import playground.aufgaben.Pruefe;
import playground.support.Card;

import javax.smartcardio.CommandAPDU;

import static org.junit.Assert.assertEquals;

/**
 * Musterloesung zum Aufgabenzettel Zwischenuebung Personalisierung (aufgaben/uebung-03-personalisierung.md).
 *
 * Loesungsstellen: ">>> LOESUNG" in dieser Datei und in PersoLoesungApplet.
 * Ausfuehren:     mise exec -- mvn test -Dtest=Uebung03LoesungTest
 */
public class Uebung03LoesungTest {

    private static final String AID = "F0 00 00 00 01 13";
    private static final byte[] SERIAL = Card.hex("12 34 56 78 9A BC DE F0");

    // >>> LOESUNG AUFGABE 2: "andere" Seriennummer (zuerst wie hier, dann gleich der echten SERIAL setzen)
    //     und deine Vorhersage des Statusworts fuer das PIN-STORE-DATA
    static final byte[] A2_ANDERE_SERIENNUMMER = Card.hex("00 00 00 00 00 00 00 01");
    static final int A2_VORHERSAGE_SW = 0x6982;
    // <<< LOESUNG AUFGABE 2

    // >>> LOESUNG AUFGABE 3: Vorhersage fuer "PIN-Kryptogramm als Name einspielen" (Tag getauscht).
    //     Einmal mit TAG_IM_MAC = true, dann mit false in PersoLoesungApplet.
    static final int A3_VORHERSAGE_SW = 0x6982;
    // <<< LOESUNG AUFGABE 3

    // >>> LOESUNG AUFGABE 4: Vorhersage fuer das zweite Senden desselben PIN-APDUs (vor dem Abschluss)
    static final int A4_VORHERSAGE_SW = 0x9000;
    // <<< LOESUNG AUFGABE 4

    private Card card;

    @Before
    public void setUp() {
        card = new Card(AID, PersoLoesungApplet.class, DataPreparationLoesung.installParams(SERIAL));
        card.select();
    }

    /** Aufgabe 1: Diesen Test im Debugger starten (Breakpoints siehe ">>> LOESUNG AUFGABE 1" in PersoLoesungApplet). */
    @Test
    public void aufgabe1_debuggerEinstieg() {
        DataPreparationLoesung dp = new DataPreparationLoesung(SERIAL);
        assertEquals(0x9000, card.send(dp.storePin("4711")).getSW());
        assertEquals(0x9000, card.send(dp.storeHolder("Max Mustermann")).getSW());
        assertEquals(0x9000, card.send(dp.endPerso()).getSW());
        assertEquals(0x9000, card.send(0x00, 0x20, 0x00, 0x81, "4711".getBytes()).getSW());
    }

    @Test
    public void aufgabe2_falscheKarte() {
        DataPreparationLoesung other = new DataPreparationLoesung(A2_ANDERE_SERIENNUMMER);
        int sw = card.send(other.storePin("4711")).getSW();
        Pruefe.vorhersage("Aufgabe 2", A2_VORHERSAGE_SW, sw);
    }

    @Test
    public void aufgabe3_tagGetauscht() {
        CommandAPDU pinApdu = new DataPreparationLoesung(SERIAL).storePin("4711");
        int sw = card.send(0x80, 0xE2, PersoLoesungApplet.TAG_HOLDER, 0x00, pinApdu.getData()).getSW();
        System.out.println("  Aufgabe 3: TAG_IM_MAC = " + PersoLoesungApplet.tagImMac());
        Pruefe.vorhersage("Aufgabe 3", A3_VORHERSAGE_SW, sw);

        // Zweite Haelfte: Tag NICHT im MAC -> der Angriff gelingt (9000)
        Card ohneTag = new Card(AID, PersoOhneTagLoesungApplet.class, DataPreparationLoesung.installParams(SERIAL));
        ohneTag.select();
        CommandAPDU pinApdu2 = new DataPreparationLoesung(SERIAL, false).storePin("4711");
        assertEquals("Aufgabe 3 ohne Tag im MAC: Angriff gelingt", 0x9000,
                ohneTag.send(0x80, 0xE2, PersoOhneTagLoesungApplet.TAG_HOLDER, 0x00, pinApdu2.getData()).getSW());
    }

    @Test
    public void aufgabe4_replayVorAbschluss() {
        DataPreparationLoesung dp = new DataPreparationLoesung(SERIAL);
        CommandAPDU pinApdu = dp.storePin("4711");
        assertEquals(0x9000, card.send(pinApdu).getSW());

        int sw = 0;
        // >>> LOESUNG AUFGABE 4: dasselbe APDU (pinApdu) noch einmal an die Karte senden und das
        //     Statuswort in sw speichern. Hilfe: card.send(...).getSW()

        sw = card.send(pinApdu).getSW();
        // <<< LOESUNG AUFGABE 4

        if (sw == 0) {
            org.junit.Assert.fail("Aufgabe 4: APDU erneut senden und sw setzen.");
        }
        Pruefe.vorhersage("Aufgabe 4", A4_VORHERSAGE_SW, sw);
    }
}
