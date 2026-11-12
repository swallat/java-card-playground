package playground.aufgaben.uebung02;

import org.junit.Before;
import org.junit.Test;
import playground.aufgaben.Pruefe;
import playground.support.Card;

import static org.junit.Assert.assertEquals;

/**
 * Testbed zum Aufgabenzettel Uebung 2 (aufgaben/uebung-02-pin.md).
 *
 * Eingabestellen: ">>> AUFGABE" in dieser Datei und in PinAufgabeApplet.
 * Ausfuehren:     mise exec -- mvn test -Dtest=Uebung02Aufgaben
 */
public class Uebung02Aufgaben {

    private static final String AID = "F0 00 00 00 01 12";

    // >>> AUFGABE 1: Vorhersage der Statuswoerter (bei PIN_TRY_LIMIT = 3) fuer diese Folge:
    //     [0] VERIFY falsch   [1] VERIFY falsch   [2] VERIFY ohne Daten
    //     [3] VERIFY richtig  [4] VERIFY ohne Daten
    //     --- RESET ---       [5] VERIFY ohne Daten
    static final int[] A1_VORHERSAGE = {0x0000, 0x0000, 0x0000, 0x0000, 0x0000, 0x0000};
    // <<< AUFGABE 1

    private Card card;

    @Before
    public void setUp() {
        card = new Card(AID, PinAufgabeApplet.class);
        card.select();
    }

    /** Liest das Limit zur Laufzeit (die Konstante wuerde sonst beim Kompilieren eingebettet). */
    private static int limit() {
        return PinAufgabeApplet.pinTryLimit();
    }

    private int verify(String pin) { return card.send(0x00, 0x20, 0x00, 0x81, pin.getBytes()).getSW(); }
    private int status()           { return card.send(0x00, 0x20, 0x00, 0x81).getSW(); }

    @Test
    public void aufgabe1_statuswoerterVorhersagen() {
        int[] ist = new int[6];
        ist[0] = verify("9999");
        ist[1] = verify("9999");
        ist[2] = status();
        ist[3] = verify("1234");
        ist[4] = status();
        card.reset();
        card.select();
        ist[5] = status();
        for (int i = 0; i < ist.length; i++) {
            Pruefe.vorhersage("Aufgabe 1, Schritt [" + i + "]", A1_VORHERSAGE[i], ist[i]);
        }
    }

    @Test
    public void aufgabe2_fuenfVersuche() {
        assertEquals("Aufgabe 2: PIN_TRY_LIMIT in PinAufgabeApplet auf 5 setzen", 0x63C5, status());
    }

    @Test
    public void aufgabe3_pinAendern() {
        assertEquals("Aufgabe 3: richtige alte PIN -> 9000",
                0x9000, card.send(0x00, 0x24, 0x00, 0x81, "12345678".getBytes()).getSW());
        assertEquals("Aufgabe 3: neue PIN 5678 muss gelten", 0x9000, verify("5678"));
        assertEquals("Aufgabe 3: alte PIN 1234 darf nicht mehr gelten",
                0x63C0 | (limit() - 1), verify("1234"));
        assertEquals("Aufgabe 3: falsche alte PIN -> 63Cx",
                0x63C0 | (limit() - 2), card.send(0x00, 0x24, 0x00, 0x81, "00001111".getBytes()).getSW());
    }

    @Test
    public void aufgabe4_entsperrenMitPuk() {
        for (int i = 0; i < limit(); i++) {
            verify("9999");
        }
        assertEquals("Vorbedingung: PIN gesperrt", 0x6983, verify("1234"));
        assertEquals("Aufgabe 4: falsche PUK -> 63C9",
                0x63C9, card.send(0x00, 0x2C, 0x00, 0x81, "00000000".getBytes()).getSW());
        assertEquals("Aufgabe 4: richtige PUK -> 9000",
                0x9000, card.send(0x00, 0x2C, 0x00, 0x81, "12345678".getBytes()).getSW());
        assertEquals("Aufgabe 4: PIN wieder nutzbar", 0x9000, verify("1234"));
    }
}
