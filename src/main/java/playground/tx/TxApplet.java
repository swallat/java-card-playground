package playground.tx;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * Uebung 4: persistenter vs. transienter Speicher, Transaktionen und Stromausfall (Tearing).
 *
 * Teil A - Speicherarten:
 *   80 10 00 00          INCREMENT: alle drei Zaehler +1
 *   80 12 00 00 00       READ COUNTERS: persistent || CLEAR_ON_RESET || CLEAR_ON_DESELECT (je 2 Bytes)
 *   80 14 00 00 00       ABORT DEMO: in einer Transaktion beide Zaehlerarten +100, dann abortTransaction()
 *
 * Teil B - Boerse als 6-Byte-Datensatz: Guthaben || Anzahl Buchungen || Summe Buchungen.
 *   Invariante: Guthaben + Summe Buchungen = 1000.
 *   80 30 P1 P2 02 <Betrag>   DEBIT
 *        P1 = 00  ohne Schutz (zwei getrennte Schreibzugriffe)
 *             01  JCRE-Transaktion (beginTransaction / commitTransaction)
 *             02  eigenes Undo-Journal (Prinzip des Commit-Puffers)
 *             03  ein einziger atomarer Schreibzugriff (Util.arrayCopy des ganzen Datensatzes)
 *        P2 = 01  Testschalter: "Stromausfall" mitten im Vorgang
 *   80 32 00 00 00            READ PURSE: Datensatz (6) || freie Commit-Kapazitaet (2)
 */
public class TxApplet extends Applet {

    private static final byte INS_INCREMENT = (byte) 0x10;
    private static final byte INS_READ_COUNTERS = (byte) 0x12;
    private static final byte INS_ABORT_DEMO = (byte) 0x14;
    private static final byte INS_DEBIT = (byte) 0x30;
    private static final byte INS_READ_PURSE = (byte) 0x32;

    public static final byte MODE_UNPROTECTED = 0x00;
    public static final byte MODE_JCRE_TRANSACTION = 0x01;
    public static final byte MODE_OWN_JOURNAL = 0x02;
    public static final byte MODE_ATOMIC_COPY = 0x03;
    private static final byte P2_SIMULATE_POWER_LOSS = 0x01;

    /** Antwort auf den simulierten Stromausfall. Eine echte Karte wuerde gar nicht mehr antworten. */
    public static final short SW_SIMULATED_POWER_LOSS = (short) 0x6F01;
    public static final short INITIAL_BALANCE = 1000;

    private static final short OFF_BALANCE = 0;
    private static final short OFF_COUNT = 2;
    private static final short OFF_TOTAL = 4;
    private static final short RECORD_LEN = 6;

    // --- Teil A ---
    private short persistentCounter;
    private final short[] resetCounter;
    private final short[] deselectCounter;

    // --- Teil B ---
    private final byte[] purse = new byte[RECORD_LEN];          // persistent
    private final byte[] journalBackup = new byte[RECORD_LEN];  // persistent: Kopie vor der Aenderung
    private boolean journalValid;                               // persistent: "Vorgang laeuft"
    private final byte[] work;                                  // transient: Arbeitskopie im RAM

    private TxApplet() {
        resetCounter = JCSystem.makeTransientShortArray((short) 1, JCSystem.CLEAR_ON_RESET);
        deselectCounter = JCSystem.makeTransientShortArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
        work = JCSystem.makeTransientByteArray(RECORD_LEN, JCSystem.CLEAR_ON_DESELECT);
        Util.setShort(purse, OFF_BALANCE, INITIAL_BALANCE);
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new TxApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public boolean select() {
        recoverJournal();   // wie die Runtime beim Einschalten: angefangene Vorgaenge zurueckrollen
        return true;
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        switch (buf[ISO7816.OFFSET_INS]) {
            case INS_INCREMENT:     increment(); break;
            case INS_READ_COUNTERS: readCounters(apdu); break;
            case INS_ABORT_DEMO:    abortDemo(apdu); break;
            case INS_DEBIT:         debit(apdu); break;
            case INS_READ_PURSE:    readPurse(apdu); break;
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    // ------------------------------------------------------------------ Teil A

    private void increment() {
        persistentCounter++;
        resetCounter[0]++;
        deselectCounter[0]++;
    }

    private void readCounters(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        Util.setShort(buf, (short) 0, persistentCounter);
        Util.setShort(buf, (short) 2, resetCounter[0]);
        Util.setShort(buf, (short) 4, deselectCounter[0]);
        apdu.setOutgoingAndSend((short) 0, (short) 6);
    }

    /**
     * Laut Spezifikation: persistente Aenderungen werden zurueckgerollt, transiente nicht.
     * (jCardSim rollt gar nichts zurueck - siehe Test.)
     */
    private void abortDemo(APDU apdu) {
        JCSystem.beginTransaction();
        persistentCounter += 100;
        deselectCounter[0] += 100;
        JCSystem.abortTransaction();
        readCounters(apdu);
    }

    // ------------------------------------------------------------------ Teil B

    private void debit(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        byte mode = buf[ISO7816.OFFSET_P1];
        boolean powerLoss = buf[ISO7816.OFFSET_P2] == P2_SIMULATE_POWER_LOSS;
        short len = apdu.setIncomingAndReceive();
        if (len != 2) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short amount = Util.getShort(buf, apdu.getOffsetCdata());
        short balance = Util.getShort(purse, OFF_BALANCE);
        if (amount <= 0 || amount > balance) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        short newBalance = (short) (balance - amount);
        short newCount = (short) (Util.getShort(purse, OFF_COUNT) + 1);
        short newTotal = (short) (Util.getShort(purse, OFF_TOTAL) + amount);

        switch (mode) {
            case MODE_UNPROTECTED:
                Util.setShort(purse, OFF_BALANCE, newBalance);      // Schreibzugriff 1
                tearIf(powerLoss);
                Util.setShort(purse, OFF_COUNT, newCount);          // Schreibzugriff 2
                Util.setShort(purse, OFF_TOTAL, newTotal);
                break;

            case MODE_JCRE_TRANSACTION:
                JCSystem.beginTransaction();
                Util.setShort(purse, OFF_BALANCE, newBalance);
                tearIf(powerLoss);                                  // echte Karte: Rollback beim Einschalten
                Util.setShort(purse, OFF_COUNT, newCount);
                Util.setShort(purse, OFF_TOTAL, newTotal);
                JCSystem.commitTransaction();
                break;

            case MODE_OWN_JOURNAL:
                // 1. alten Zustand sichern (ein atomares arrayCopy), 2. Flag setzen (atomares Byte)
                Util.arrayCopy(purse, (short) 0, journalBackup, (short) 0, RECORD_LEN);
                journalValid = true;
                // 3. aendern - beliebig viele Schreibzugriffe
                Util.setShort(purse, OFF_BALANCE, newBalance);
                tearIf(powerLoss);
                Util.setShort(purse, OFF_COUNT, newCount);
                Util.setShort(purse, OFF_TOTAL, newTotal);
                // 4. Commit = Flag loeschen. Erst ab hier gilt der neue Zustand.
                journalValid = false;
                break;

            case MODE_ATOMIC_COPY:
                // Neuen Datensatz komplett im RAM vorbereiten ...
                Util.setShort(work, OFF_BALANCE, newBalance);
                Util.setShort(work, OFF_COUNT, newCount);
                Util.setShort(work, OFF_TOTAL, newTotal);
                tearIf(powerLoss);                                  // Ausfall hier: persistent nichts geaendert
                // ... und mit EINEM atomaren Schreibzugriff uebernehmen (Util.arrayCopy ist atomar)
                Util.arrayCopy(work, (short) 0, purse, (short) 0, RECORD_LEN);
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
    }

    /** Beim "Einschalten" (hier: select): angefangenen Vorgang aus dem Journal zuruecknehmen. */
    private void recoverJournal() {
        if (journalValid) {
            Util.arrayCopy(journalBackup, (short) 0, purse, (short) 0, RECORD_LEN);
            journalValid = false;
        }
    }

    private static void tearIf(boolean powerLoss) {
        if (powerLoss) {
            ISOException.throwIt(SW_SIMULATED_POWER_LOSS);
        }
    }

    private void readPurse(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        Util.arrayCopyNonAtomic(purse, (short) 0, buf, (short) 0, RECORD_LEN);
        Util.setShort(buf, RECORD_LEN, JCSystem.getUnusedCommitCapacity());
        apdu.setOutgoingAndSend((short) 0, (short) (RECORD_LEN + 2));
    }
}
