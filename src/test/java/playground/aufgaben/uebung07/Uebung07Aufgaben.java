package playground.aufgaben.uebung07;

import com.licel.jcardsim.utils.AIDUtil;
import org.junit.Before;
import org.junit.Test;
import playground.aufgaben.Pruefe;
import playground.hello.HelloApplet;
import playground.support.Card;

import static org.junit.Assert.assertEquals;

/**
 * Testbed zum Aufgabenzettel Uebung 4 - Transaktionen (aufgaben/uebung-07-transaktionen.md).
 *
 * Eingabestellen: ">>> AUFGABE" in dieser Datei und in TxAufgabeApplet.
 * Ausfuehren:     mise exec -- mvn test -Dtest=Uebung07Aufgaben
 */
public class Uebung07Aufgaben {

    private static final String AID = "F0 00 00 00 01 17";
    private static final String OTHER_AID = "F0 00 00 00 01 01";

    // >>> AUFGABE 1: Vorhersage {persistent, CLEAR_ON_RESET, CLEAR_ON_DESELECT}
    //     nach 3 x INCREMENT, dann nach Applet-Wechsel, dann nach Reset.
    //     0 ist eine gueltige Antwort. Deshalb wird erst geprueft, wenn A1_AUSGEFUELLT = true ist.
    static final int[] A1_NACH_INCREMENT = {0, 0, 0};
    static final int[] A1_NACH_APPLET_WECHSEL = {0, 0, 0};
    static final int[] A1_NACH_RESET = {0, 0, 0};
    static final boolean A1_AUSGEFUELLT = false;
    // <<< AUFGABE 1

    // >>> AUFGABE 4: Guthaben nach Abbuchung 100 MIT JCRE-Transaktion und Stromausfall
    static final int A4_GUTHABEN_IM_SIMULATOR = 0;
    static final int A4_GUTHABEN_AUF_ECHTER_KARTE = 0;
    // <<< AUFGABE 4

    private Card card;

    @Before
    public void setUp() {
        card = new Card(AID, TxAufgabeApplet.class);
        card.sim.installApplet(AIDUtil.create(OTHER_AID), HelloApplet.class,
                new byte[]{6, (byte) 0xF0, 0, 0, 0, 1, 1}, (short) 0, (byte) 7);
        card.select();
    }

    @Test
    public void aufgabe1_speicherarten() {
        if (!A1_AUSGEFUELLT) {
            org.junit.Assert.fail("Aufgabe 1: Vorhersagen eintragen und A1_AUSGEFUELLT = true setzen.");
        }
        for (int i = 0; i < 3; i++) {
            card.send(0x80, 0x10, 0x00, 0x00);
        }
        vergleiche("nach INCREMENT", A1_NACH_INCREMENT, counters());
        card.send(0x00, 0xA4, 0x04, 0x00, Card.hex(OTHER_AID));
        card.select();
        vergleiche("nach Applet-Wechsel", A1_NACH_APPLET_WECHSEL, counters());
        card.reset();
        card.select();
        vergleiche("nach Reset", A1_NACH_RESET, counters());
    }

    @Test
    public void aufgabe2_journalWiederherstellen() {
        assertEquals(0x6F01, debit(TxAufgabeApplet.MODE_OWN_JOURNAL, true, 100));
        powerCycle();
        int[] p = purse();
        assertEquals("Aufgabe 2: Nach dem Einschalten muss das Journal zurueckgerollt haben", 1000, p[0]);
        assertEquals("Aufgabe 2: Invariante", TxAufgabeApplet.INITIAL_BALANCE, p[0] + p[2]);

        assertEquals(0x9000, debit(TxAufgabeApplet.MODE_OWN_JOURNAL, false, 100));
        powerCycle();
        assertEquals("Aufgabe 2: Ein abgeschlossener Vorgang darf NICHT zurueckgerollt werden", 900, purse()[0]);
    }

    @Test
    public void aufgabe3_atomarerSchreibzugriff() {
        assertEquals(0x9000, debit(TxAufgabeApplet.MODE_ATOMIC_COPY, false, 100));
        int[] p = purse();
        assertEquals("Aufgabe 3: Abbuchung ohne Stoerung muss wirken", 900, p[0]);
        assertEquals("Aufgabe 3: Buchung protokolliert", 1, p[1]);

        assertEquals(0x6F01, debit(TxAufgabeApplet.MODE_ATOMIC_COPY, true, 100));
        powerCycle();
        assertEquals("Aufgabe 3: Stromausfall vor dem Kopieren aendert nichts", 900, purse()[0]);
    }

    @Test
    public void aufgabe4_simulatorUndEchteKarte() {
        assertEquals(0x6F01, debit(TxAufgabeApplet.MODE_JCRE_TRANSACTION, true, 100));
        powerCycle();
        Pruefe.anzahl("Aufgabe 4 (Simulator)", A4_GUTHABEN_IM_SIMULATOR, purse()[0]);
        Pruefe.wert("Aufgabe 4", "Guthaben auf echter Karte", A4_GUTHABEN_AUF_ECHTER_KARTE, 1000);
    }

    // ------------------------------------------------------------------ Hilfen (nicht aendern)

    private int[] counters() {
        byte[] d = card.sendLe(0x80, 0x12, 0x00, 0x00, 256).getData();
        return new int[]{s(d, 0), s(d, 2), s(d, 4)};
    }

    private int[] purse() {
        byte[] d = card.sendLe(0x80, 0x32, 0x00, 0x00, 256).getData();
        return new int[]{s(d, 0), s(d, 2), s(d, 4)};
    }

    private int debit(int mode, boolean powerLoss, int amount) {
        return card.send(0x80, 0x30, mode, powerLoss ? 0x01 : 0x00,
                new byte[]{(byte) (amount >> 8), (byte) amount}).getSW();
    }

    private void powerCycle() {
        card.reset();
        card.select();
    }

    private static int s(byte[] d, int off) {
        return (short) (((d[off] & 0xFF) << 8) | (d[off + 1] & 0xFF));
    }

    private static void vergleiche(String wann, int[] vorhersage, int[] ist) {
        String[] namen = {"persistent", "CLEAR_ON_RESET", "CLEAR_ON_DESELECT"};
        for (int i = 0; i < 3; i++) {
            assertEquals("Aufgabe 1, " + wann + ", " + namen[i], vorhersage[i], ist[i]);
        }
    }
}
