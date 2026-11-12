package playground.aufgaben;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Kleine Pruefhelfer fuer die Aufgaben-Testklassen. */
public final class Pruefe {

    private Pruefe() {}

    /** Vergleicht eine eingetragene Vorhersage (z. B. Statuswort) mit dem tatsaechlichen Ergebnis. */
    public static void vorhersage(String aufgabe, int vorhersage, int ist) {
        if (vorhersage == 0) {
            fail(aufgabe + ": Bitte zuerst deine Vorhersage eintragen (steht noch auf 0).");
        }
        assertEquals(String.format("%s: Vorhersage 0x%04X, Karte lieferte 0x%04X", aufgabe, vorhersage, ist),
                vorhersage, ist);
    }

    /** Vergleicht eine Anzahl (z. B. APDUs) mit dem gemessenen Wert. */
    public static void anzahl(String aufgabe, int vorhersage, int ist) {
        if (vorhersage == 0) {
            fail(aufgabe + ": Bitte zuerst deine Vorhersage eintragen (steht noch auf 0).");
        }
        assertEquals(String.format("%s: Vorhersage %d, gemessen %d", aufgabe, vorhersage, ist), vorhersage, ist);
    }

    /** Prueft einen eingetragenen Wert, ohne die Loesung in der Fehlermeldung zu verraten. */
    public static void wert(String aufgabe, String was, int eingetragen, int richtig) {
        if (eingetragen == 0) {
            fail(aufgabe + ": '" + was + "' eintragen (steht noch auf 0).");
        }
        assertTrue(aufgabe + ": '" + was + "' stimmt noch nicht.", eingetragen == richtig);
    }
}
