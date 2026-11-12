package playground.loesungen.uebung07;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * Musterloesung (Kopie) von TxApplet (Aufgabenzettel Uebung 4 - Transaktionen).
 * Loesungsstellen: Suche nach ">>> LOESUNG".
 * Pruefen:  mise exec -- mvn test -Dtest=Uebung07LoesungTest
 *
 *   80 10 00 00 / 80 12 00 00 00     Zaehler erhoehen / lesen (persistent, CLEAR_ON_RESET, CLEAR_ON_DESELECT)
 *   80 30 P1 P2 02 <Betrag>          DEBIT, P1 = 00 ohne Schutz, 01 JCRE-Transaktion, 02 Journal,
 *                                    03 atomarer Schreibzugriff; P2 = 01 simulierter Stromausfall
 *   80 32 00 00 00                   Boerse lesen: Guthaben || Buchungen || Summe Buchungen
 */
public class TxLoesungApplet extends Applet {

    public static final byte MODE_UNPROTECTED = 0x00;
    public static final byte MODE_JCRE_TRANSACTION = 0x01;
    public static final byte MODE_OWN_JOURNAL = 0x02;
    public static final byte MODE_ATOMIC_COPY = 0x03;
    public static final short SW_SIMULATED_POWER_LOSS = (short) 0x6F01;
    public static final short INITIAL_BALANCE = 1000;

    private static final short OFF_BALANCE = 0;
    private static final short OFF_COUNT = 2;
    private static final short OFF_TOTAL = 4;
    private static final short RECORD_LEN = 6;

    private short persistentCounter;
    private final short[] resetCounter;
    private final short[] deselectCounter;

    private final byte[] purse = new byte[RECORD_LEN];
    private final byte[] journalBackup = new byte[RECORD_LEN];
    private boolean journalValid;
    private final byte[] work;

    private TxLoesungApplet() {
        resetCounter = JCSystem.makeTransientShortArray((short) 1, JCSystem.CLEAR_ON_RESET);
        deselectCounter = JCSystem.makeTransientShortArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
        work = JCSystem.makeTransientByteArray(RECORD_LEN, JCSystem.CLEAR_ON_DESELECT);
        Util.setShort(purse, OFF_BALANCE, INITIAL_BALANCE);
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new TxLoesungApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public boolean select() {
        recoverJournal();
        return true;
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        switch (buf[ISO7816.OFFSET_INS]) {
            case (byte) 0x10:
                persistentCounter++;
                resetCounter[0]++;
                deselectCounter[0]++;
                break;
            case (byte) 0x12:
                Util.setShort(buf, (short) 0, persistentCounter);
                Util.setShort(buf, (short) 2, resetCounter[0]);
                Util.setShort(buf, (short) 4, deselectCounter[0]);
                apdu.setOutgoingAndSend((short) 0, (short) 6);
                break;
            case (byte) 0x30:
                debit(apdu);
                break;
            case (byte) 0x32:
                Util.arrayCopyNonAtomic(purse, (short) 0, buf, (short) 0, RECORD_LEN);
                apdu.setOutgoingAndSend((short) 0, RECORD_LEN);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void debit(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        byte mode = buf[ISO7816.OFFSET_P1];
        boolean powerLoss = buf[ISO7816.OFFSET_P2] == 0x01;
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
                Util.setShort(purse, OFF_BALANCE, newBalance);
                tearIf(powerLoss);
                Util.setShort(purse, OFF_COUNT, newCount);
                Util.setShort(purse, OFF_TOTAL, newTotal);
                break;

            case MODE_JCRE_TRANSACTION:
                JCSystem.beginTransaction();
                Util.setShort(purse, OFF_BALANCE, newBalance);
                tearIf(powerLoss);
                Util.setShort(purse, OFF_COUNT, newCount);
                Util.setShort(purse, OFF_TOTAL, newTotal);
                JCSystem.commitTransaction();
                break;

            case MODE_OWN_JOURNAL:
                Util.arrayCopy(purse, (short) 0, journalBackup, (short) 0, RECORD_LEN);   // 1. sichern
                journalValid = true;                                                    // 2. Flag
                Util.setShort(purse, OFF_BALANCE, newBalance);                          // 3. aendern
                tearIf(powerLoss);
                Util.setShort(purse, OFF_COUNT, newCount);
                Util.setShort(purse, OFF_TOTAL, newTotal);
                journalValid = false;                                                   // 4. Commit
                break;

            case MODE_ATOMIC_COPY:
                Util.setShort(work, OFF_BALANCE, newBalance);
                Util.setShort(work, OFF_COUNT, newCount);
                Util.setShort(work, OFF_TOTAL, newTotal);
                tearIf(powerLoss);
                // >>> LOESUNG AUFGABE 3: den vorbereiteten Datensatz (work) mit EINEM atomaren Schreibzugriff
                //     in den persistenten Datensatz (purse) uebernehmen. Hilfe: Util.arrayCopy(...)
                Util.arrayCopy(work, (short) 0, purse, (short) 0, RECORD_LEN);
                // <<< LOESUNG AUFGABE 3
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
    }

    /** Wird bei jedem SELECT aufgerufen - das "Einschalten" der Karte. */
    private void recoverJournal() {
        // >>> LOESUNG AUFGABE 2: Wenn ein Vorgang angefangen, aber nicht abgeschlossen wurde (journalValid),
        //     den gesicherten Datensatz (journalBackup) zurueck nach purse kopieren und das Flag loeschen.
        if (journalValid) {
            Util.arrayCopy(journalBackup, (short) 0, purse, (short) 0, RECORD_LEN);
            journalValid = false;
        }
        // <<< LOESUNG AUFGABE 2
    }

    private static void tearIf(boolean powerLoss) {
        if (powerLoss) {
            ISOException.throwIt(SW_SIMULATED_POWER_LOSS);
        }
    }
}
