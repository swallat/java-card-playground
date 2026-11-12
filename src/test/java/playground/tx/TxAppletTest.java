package playground.tx;

import com.licel.jcardsim.utils.AIDUtil;
import org.junit.Before;
import org.junit.Test;
import playground.hello.HelloApplet;
import playground.support.Card;

import static org.junit.Assert.assertEquals;

public class TxAppletTest {
    private static final String AID = "F0 00 00 00 01 06";
    private static final String OTHER_AID = "F0 00 00 00 01 01";

    private Card card;

    @Before
    public void setUp() {
        card = new Card(AID, TxApplet.class);
        // Zweites Applet, um einen Applet-Wechsel (Deselect) ausloesen zu koennen
        card.sim.installApplet(AIDUtil.create(OTHER_AID), HelloApplet.class,
                new byte[]{6, (byte) 0xF0, 0, 0, 0, 1, 1}, (short) 0, (byte) 7);
        card.select();
    }

    private int[] counters() {
        byte[] d = card.sendLe(0x80, 0x12, 0x00, 0x00, 256).getData();
        return new int[]{s(d, 0), s(d, 2), s(d, 4)};
    }

    /** {Guthaben, Buchungen, Summe Buchungen, freie Commit-Kapazitaet} */
    private int[] purse() {
        byte[] d = card.sendLe(0x80, 0x32, 0x00, 0x00, 256).getData();
        return new int[]{s(d, 0), s(d, 2), s(d, 4), s(d, 6)};
    }

    private int debit(int mode, boolean powerLoss, int amount) {
        return card.send(0x80, 0x30, mode, powerLoss ? 0x01 : 0x00,
                new byte[]{(byte) (amount >> 8), (byte) amount}).getSW();
    }

    /** "Karte aus dem Feld und wieder rein" */
    private void powerCycle() {
        card.reset();
        card.select();
    }

    private static int s(byte[] d, int off) {
        return (short) (((d[off] & 0xFF) << 8) | (d[off + 1] & 0xFF));
    }

    private static void printPurse(int[] p) {
        System.out.printf("  Guthaben %d, Buchungen %d, Summe Buchungen %d -> Invariante %s%n",
                p[0], p[1], p[2], (p[0] + p[2] == TxApplet.INITIAL_BALANCE) ? "OK" : "VERLETZT");
    }

    // ================================================================ Teil A: Speicherarten

    @Test
    public void persistentVsTransient() {
        System.out.println("Zaehler: persistent / CLEAR_ON_RESET / CLEAR_ON_DESELECT");
        card.send(0x80, 0x10, 0x00, 0x00);
        card.send(0x80, 0x10, 0x00, 0x00);
        assertCounters(new int[]{2, 2, 2}, counters());

        System.out.println("  --- anderes Applet auswaehlen (Deselect), dann zurueck ---");
        card.send(0x00, 0xA4, 0x04, 0x00, Card.hex(OTHER_AID));
        card.select();
        assertCounters(new int[]{2, 2, 0}, counters());   // CLEAR_ON_DESELECT geloescht

        System.out.println("  --- Reset (Karte aus dem Feld) ---");
        powerCycle();
        assertCounters(new int[]{2, 0, 0}, counters());   // CLEAR_ON_RESET auch geloescht
    }

    /**
     * SIMULATOR-GRENZE (jCardSim 3.0.6.0): Transaktionen zaehlen nur die Verschachtelungstiefe,
     * es gibt kein Journal und keinen Rollback. Auf einer echten Karte waere persistent = 1.
     */
    @Test
    public void simulatorDoesNotRollBackOnAbort() {
        card.send(0x80, 0x10, 0x00, 0x00);
        System.out.println("Transaktion: beide Zaehler +100, dann abortTransaction():");
        byte[] d = card.sendLe(0x80, 0x14, 0x00, 0x00, 256).getData();
        assertEquals("Simulator: persistent NICHT zurueckgerollt (echte Karte: 1)", 101, s(d, 0));
        assertEquals("transient wird nie zurueckgerollt (auch nicht auf echter Karte)", 101, s(d, 4));
    }

    // ================================================================ Teil B: Boerse

    @Test
    public void normalDebitKeepsInvariant() {
        System.out.println("Abbuchung 100 ohne Stoerung:");
        assertEquals(0x9000, debit(TxApplet.MODE_UNPROTECTED, false, 100));
        int[] p = purse();
        printPurse(p);
        assertEquals(900, p[0]);
        assertEquals(1, p[1]);
        assertEquals(TxApplet.INITIAL_BALANCE, p[0] + p[2]);
    }

    @Test
    public void unprotectedPowerLossBreaksInvariant() {
        System.out.println("P1=00 OHNE Schutz, Stromausfall zwischen den Schreibzugriffen:");
        assertEquals(0x6F01, debit(TxApplet.MODE_UNPROTECTED, true, 100));
        powerCycle();
        int[] p = purse();
        printPurse(p);
        assertEquals("Guthaben abgebucht ...", 900, p[0]);
        assertEquals("... aber keine Buchung protokolliert", 0, p[1]);
        assertEquals("100 Einheiten sind verschwunden", 900, p[0] + p[2]);
    }

    /** SIMULATOR-GRENZE: Auf einer echten Karte waere hier Guthaben 1000 (Rollback beim Einschalten). */
    @Test
    public void jcreTransactionHasNoEffectInSimulator() {
        System.out.println("P1=01 JCRE-Transaktion, Stromausfall (Simulator: kein Rollback):");
        assertEquals(0x6F01, debit(TxApplet.MODE_JCRE_TRANSACTION, true, 100));
        powerCycle();
        int[] p = purse();
        printPurse(p);
        assertEquals("Simulator rollt nicht zurueck (echte Karte: 1000)", 900, p[0]);
    }

    @Test
    public void jcreTransactionCommitsWithoutFailure() {
        System.out.println("P1=01 JCRE-Transaktion ohne Stoerung:");
        assertEquals(0x9000, debit(TxApplet.MODE_JCRE_TRANSACTION, false, 250));
        int[] p = purse();
        printPurse(p);
        assertEquals(750, p[0]);
        assertEquals(TxApplet.INITIAL_BALANCE, p[0] + p[2]);
        System.out.println("  freie Commit-Kapazitaet laut Simulator: " + p[3] + " (Platzhalterwert)");
    }

    @Test
    public void ownJournalRecoversAfterPowerLoss() {
        System.out.println("P1=02 eigenes Undo-Journal, Stromausfall, dann Einschalten (select):");
        assertEquals(0x6F01, debit(TxApplet.MODE_OWN_JOURNAL, true, 100));
        powerCycle();                                      // select() stellt aus dem Journal wieder her
        int[] p = purse();
        printPurse(p);
        assertEquals("Journal hat zurueckgerollt", 1000, p[0]);
        assertEquals(0, p[1]);
        assertEquals(TxApplet.INITIAL_BALANCE, p[0] + p[2]);
    }

    @Test
    public void ownJournalCommitsWithoutFailure() {
        System.out.println("P1=02 eigenes Undo-Journal ohne Stoerung, danach Einschalten:");
        assertEquals(0x9000, debit(TxApplet.MODE_OWN_JOURNAL, false, 100));
        powerCycle();                                      // darf nichts zuruecknehmen
        int[] p = purse();
        printPurse(p);
        assertEquals(900, p[0]);
        assertEquals(1, p[1]);
    }

    @Test
    public void atomicCopySurvivesPowerLoss() {
        System.out.println("P1=03 ein atomarer Schreibzugriff, Stromausfall waehrend der Vorbereitung:");
        assertEquals(0x6F01, debit(TxApplet.MODE_ATOMIC_COPY, true, 100));
        powerCycle();
        int[] p = purse();
        printPurse(p);
        assertEquals("nichts geaendert", 1000, p[0]);
        assertEquals(TxApplet.INITIAL_BALANCE, p[0] + p[2]);

        System.out.println("P1=03 ohne Stoerung:");
        assertEquals(0x9000, debit(TxApplet.MODE_ATOMIC_COPY, false, 100));
        p = purse();
        printPurse(p);
        assertEquals(900, p[0]);
        assertEquals(1, p[1]);
        assertEquals(TxApplet.INITIAL_BALANCE, p[0] + p[2]);
    }

    private static void assertCounters(int[] expected, int[] actual) {
        assertEquals("persistent", expected[0], actual[0]);
        assertEquals("CLEAR_ON_RESET", expected[1], actual[1]);
        assertEquals("CLEAR_ON_DESELECT", expected[2], actual[2]);
    }
}
