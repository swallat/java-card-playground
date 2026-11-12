package playground.aufgaben.uebung04;

import org.junit.Before;
import org.junit.Test;
import playground.aufgaben.Pruefe;
import playground.perso.DataPreparation;
import playground.perso.IssuerHost;
import playground.perso.PersoApplet;
import playground.support.Card;

import javax.smartcardio.CommandAPDU;

import static org.junit.Assert.assertEquals;

/**
 * Testbed zum Aufgabenzettel Zwischenuebung Issuer Scripts (aufgaben/uebung-04-issuer-scripts.md).
 *
 * Eingabestellen: ">>> AUFGABE" in dieser Datei; Aufgabe 4 zusaetzlich in
 *                 src/main/java/playground/perso/PersoApplet.java (zwei Stellen).
 * Ausfuehren:     mise exec -- mvn test -Dtest=Uebung04Aufgaben
 */
public class Uebung04Aufgaben {

    private static final String AID = "F0 00 00 00 01 14";
    private static final byte[] SERIAL = Card.hex("12 34 56 78 9A BC DE F0");

    // >>> AUFGABE 1: Zerlege ein PIN-Skript (84 24 00 81 ...). Laengen in Bytes.
    static final int A1_CLA = 0x00;
    static final int A1_LC = 0x00;
    static final int A1_LAENGE_ZAEHLER = 0;
    static final int A1_LAENGE_KRYPTOGRAMM = 0;
    static final int A1_LAENGE_MAC = 0;
    // <<< AUFGABE 1

    // >>> AUFGABE 2: Vorhersage, wenn der Host seinen Zaehler auf 0 zurueckdreht
    static final int A2_VORHERSAGE_SW = 0x0000;
    // <<< AUFGABE 2

    // >>> AUFGABE 3: Vorhersage fuer PUT KEY zurueck auf Generation 1 (nach Generation 2)
    static final int A3_VORHERSAGE_SW = 0x0000;
    // <<< AUFGABE 3

    private Card card;
    private IssuerHost issuer;

    @Before
    public void setUp() {
        card = new Card(AID, PersoApplet.class, DataPreparation.installParams(SERIAL));
        card.select();
        issuer = new IssuerHost(SERIAL);
        DataPreparation dp = new DataPreparation(SERIAL);
        assertEquals(0x9000, card.send(dp.storePin("4711")).getSW());
        assertEquals(0x9000, card.send(dp.storeHolder("Max Mustermann")).getSW());
        assertEquals(0x9000, card.send(dp.storeScriptKey()).getSW());
        assertEquals(0x9000, card.send(dp.endPerso()).getSW());
    }

    @Test
    public void aufgabe1_skriptZerlegen() {
        CommandAPDU c = issuer.changePin("2580");
        int dataLen = c.getNc();
        Pruefe.wert("Aufgabe 1", "CLA", A1_CLA, c.getCLA());
        Pruefe.wert("Aufgabe 1", "Lc", A1_LC, dataLen);
        Pruefe.wert("Aufgabe 1", "Laenge Zaehler", A1_LAENGE_ZAEHLER, 2);
        Pruefe.wert("Aufgabe 1", "Laenge Kryptogramm", A1_LAENGE_KRYPTOGRAMM, 16);
        Pruefe.wert("Aufgabe 1", "Laenge MAC", A1_LAENGE_MAC, dataLen - 2 - 16);
    }

    @Test
    public void aufgabe2_zaehlerZurueckdrehen() {
        assertEquals(0x9000, card.send(issuer.changePin("2580")).getSW());   // Zaehler 1
        assertEquals(0x9000, card.send(issuer.changePin("3690")).getSW());   // Zaehler 2
        issuer.setCounter(0);                                                // naechstes Skript: Zaehler 1
        int sw = card.send(issuer.changePin("1111")).getSW();
        Pruefe.vorhersage("Aufgabe 2", A2_VORHERSAGE_SW, sw);
    }

    @Test
    public void aufgabe3_schluesselRollback() {
        assertEquals(0x9000, card.send(issuer.putKey(2)).getSW());
        int sw = card.send(issuer.putKey(1)).getSW();
        Pruefe.vorhersage("Aufgabe 3", A3_VORHERSAGE_SW, sw);
    }

    /** Aufgabe 4: Implementierung in PersoApplet (">>> AUFGABE 4", Teil 1 und Teil 2). */
    @Test
    public void aufgabe4_anwendungSperren() {
        assertEquals("Aufgabe 4: APPLICATION BLOCK muss 9000 liefern",
                0x9000, card.send(issuer.applicationBlock()).getSW());
        assertEquals("Aufgabe 4: VERIFY nach Sperre", 0x6985,
                card.send(0x00, 0x20, 0x00, 0x81, "4711".getBytes()).getSW());
        assertEquals("Aufgabe 4: GET HOLDER nach Sperre", 0x6985,
                card.sendLe(0x80, 0x12, 0x00, 0x00, 256).getSW());
        assertEquals("Aufgabe 4 (Teil 2): Skripte muessen weiter funktionieren", 0x9000,
                card.send(issuer.changePin("2580")).getSW());
    }
}
