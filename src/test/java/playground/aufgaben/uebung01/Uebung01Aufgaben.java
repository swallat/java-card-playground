package playground.aufgaben.uebung01;

import org.junit.Before;
import org.junit.Test;
import playground.aufgaben.Pruefe;
import playground.support.Card;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Testbed zum Aufgabenzettel Uebung 1 (aufgaben/uebung-01-apdu.md).
 *
 * Eingabestellen: ">>> AUFGABE" in dieser Datei und in HelloAufgabeApplet.
 * Ausfuehren:     mise exec -- mvn test -Dtest=Uebung01Aufgaben
 * Eine Aufgabe:   mise exec -- mvn test -Dtest=Uebung01Aufgaben#aufgabe3_eigenesKommando
 */
public class Uebung01Aufgaben {

    private static final String AID = "F0 00 00 00 01 11";

    // >>> AUFGABE 1: Werte eintragen (Hex wie 0x12, Cases als Zahl 1-4)
    static final int A1_SELECT_INS = 0x00;     // INS-Byte von SELECT
    static final int A1_SELECT_P1 = 0x00;      // P1 bei "Auswahl per Name (AID)"
    static final int A1_CASE_HELLO = 0;        // Case von 80 01 00 00 00
    static final int A1_CASE_ECHO = 0;         // Case von 80 02 00 00 03 AA BB CC 00
    // <<< AUFGABE 1

    // >>> AUFGABE 2: eine AID, die es auf der Karte NICHT gibt, und deine Vorhersage des Statusworts
    static final String A2_FALSCHE_AID = "TODO";   // z. B. "F0 00 00 00 01 99"
    static final int A2_VORHERSAGE_SW = 0x0000;
    // <<< AUFGABE 2

    // >>> AUFGABE 4: deine Vorhersage fuer HELLO mit Le = 5 (Antwort ist 10 Bytes lang)
    static final int A4_VORHERSAGE_SW = 0x0000;
    // <<< AUFGABE 4

    // >>> AUFGABE 5: deine Vorhersage fuer SELECT, nachdem du in HelloAufgabeApplet den
    //     selectingApplet()-Block auskommentiert hast
    static final int A5_VORHERSAGE_SW_SELECT = 0x0000;
    // <<< AUFGABE 5

    private Card card;

    @Before
    public void setUp() {
        card = new Card(AID, HelloAufgabeApplet.class);
    }

    @Test
    public void aufgabe1_apdusZerlegen() {
        CommandAPDU select = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, Card.hex(AID));
        Pruefe.wert("Aufgabe 1", "SELECT-INS", A1_SELECT_INS, select.getINS());
        Pruefe.wert("Aufgabe 1", "SELECT-P1", A1_SELECT_P1, select.getP1());
        Pruefe.wert("Aufgabe 1", "Case von HELLO", A1_CASE_HELLO, apduCase(new CommandAPDU(0x80, 0x01, 0, 0, 256)));
        Pruefe.wert("Aufgabe 1", "Case von ECHO", A1_CASE_ECHO,
                apduCase(new CommandAPDU(0x80, 0x02, 0, 0, new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC}, 256)));
    }

    @Test
    public void aufgabe2_falscheAid() {
        if (A2_FALSCHE_AID.contains("TODO")) {
            fail("Aufgabe 2: A2_FALSCHE_AID eintragen.");
        }
        ResponseAPDU r = card.send(0x00, 0xA4, 0x04, 0x00, Card.hex(A2_FALSCHE_AID));
        Pruefe.vorhersage("Aufgabe 2", A2_VORHERSAGE_SW, r.getSW());
    }

    @Test
    public void aufgabe3_eigenesKommando() {
        card.select();
        ResponseAPDU r = card.sendLe(0x80, HelloAufgabeApplet.INS_VERSION, 0x00, 0x00, 256);
        assertEquals("Aufgabe 3: Statuswort", 0x9000, r.getSW());
        assertArrayEquals("Aufgabe 3: Antwortdaten", new byte[]{0x01, 0x00}, r.getData());
    }

    @Test
    public void aufgabe4_leZuKlein() {
        card.select();
        ResponseAPDU r = card.sendLe(0x80, HelloAufgabeApplet.INS_HELLO, 0x00, 0x00, 5);
        System.out.println("  Aufgabe 4: " + r.getData().length + " Datenbytes erhalten");
        Pruefe.vorhersage("Aufgabe 4", A4_VORHERSAGE_SW, r.getSW());
    }

    @Test
    public void aufgabe5_selectNichtAbgefangen() {
        int sw = card.select().getSW();
        System.out.println("  Aufgabe 5: Ist das Applet danach ausgewaehlt? HELLO liefert:");
        card.sendLe(0x80, HelloAufgabeApplet.INS_HELLO, 0x00, 0x00, 256);
        Pruefe.vorhersage("Aufgabe 5", A5_VORHERSAGE_SW_SELECT, sw);
    }

    /** Ermittelt den APDU-Case (1-4) aus Lc/Le. */
    private static int apduCase(CommandAPDU c) {
        boolean data = c.getNc() > 0;
        boolean le = c.getNe() > 0;
        return data ? (le ? 4 : 3) : (le ? 2 : 1);
    }
}
