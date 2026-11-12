package playground.loesungen.uebung08;

import org.junit.Before;
import org.junit.Test;
import playground.aufgaben.Pruefe;
import playground.gp.GpCardEmulator;
import playground.gp.Scp03Host;
import playground.support.Card;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Musterloesung zum Aufgabenzettel GlobalPlatform (aufgaben/uebung-08-globalplatform.md).
 *
 * Loesungsstellen: ">>> LOESUNG" in dieser Datei und in Scp03HostLoesung.
 * Ausfuehren:     mise exec -- mvn test -Dtest=Uebung08LoesungTest
 */
public class Uebung08LoesungTest {

    // >>> LOESUNG AUFGABE 1: Antwort auf INITIALIZE UPDATE zerlegen
    static final int A1_LAENGE_ANTWORT = 29;          // Bytes insgesamt
    static final int A1_INDEX_KVN = 10;               // Index (ab 0) der Key Version Number
    static final int A1_SCP_KENNUNG = 0x03;          // Byte direkt nach der KVN
    static final int A1_LAENGE_KARTEN_CHALLENGE = 8;
    // <<< LOESUNG AUFGABE 1

    // >>> LOESUNG AUFGABE 2: Vorhersagen der Statuswoerter
    static final int A2_SW_GET_STATUS_OHNE_KANAL = 0x6982;
    static final int A2_SW_ALTES_EXTERNAL_AUTHENTICATE = 0x6300;   // in einer neuen Sitzung wiederholt
    static final int A2_SW_KOMMANDO_ZWEIMAL = 0x6982;              // exakt dasselbe MAC-APDU zum 2. Mal
    // <<< LOESUNG AUFGABE 2

    // >>> LOESUNG AUFGABE 4: Statuswoerter fuer diese SET-STATUS-Folge, Start in OP_READY:
    //     [0] -> SECURED   [1] -> INITIALIZED   [2] -> CARD_LOCKED
    //     [3] -> SECURED   [4] -> TERMINATED    [5] danach: INITIALIZE UPDATE
    static final int[] A4_VORHERSAGE = {0x6985, 0x9000, 0x6985, 0x9000, 0x9000, 0x6985};
    // <<< LOESUNG AUFGABE 4

    // >>> LOESUNG AUFGABE 5: Privilegien 9E der ISD - welche sind gesetzt? (true / false)
    static final Boolean A5_SECURITY_DOMAIN = true;
    static final Boolean A5_DAP_VERIFICATION = false;
    static final Boolean A5_DELEGATED_MANAGEMENT = false;
    static final Boolean A5_CARD_LOCK = true;
    static final Boolean A5_CARD_TERMINATE = true;
    // <<< LOESUNG AUFGABE 5

    private Card card;

    @Before
    public void setUp() {
        card = new Card(GpCardEmulator.ISD_AID, GpCardEmulator.class);
        card.select();
    }

    @Test
    public void aufgabe1_initializeUpdateZerlegen() {
        byte[] d = card.send(new CommandAPDU(0x80, 0x50, 0x00, 0x00, new byte[8], 256)).getData();
        Pruefe.wert("Aufgabe 1", "Laenge der Antwort", A1_LAENGE_ANTWORT, d.length);
        Pruefe.wert("Aufgabe 1", "Index der KVN", A1_INDEX_KVN, 10);
        Pruefe.wert("Aufgabe 1", "SCP-Kennung", A1_SCP_KENNUNG, d[11] & 0xFF);
        Pruefe.wert("Aufgabe 1", "Laenge Karten-Challenge", A1_LAENGE_KARTEN_CHALLENGE, 8);
    }

    @Test
    public void aufgabe2_angriffeVorhersagen() {
        Pruefe.vorhersage("Aufgabe 2 (ohne Kanal)", A2_SW_GET_STATUS_OHNE_KANAL,
                card.sendLe(0x80, 0xF2, 0x80, 0x00, 256).getSW());

        Scp03Host host = new Scp03Host(GpCardEmulator.DEFAULT_KEY);
        assertEquals(0x9000, host.open(card));
        CommandAPDU c = host.getStatus(0x80);
        assertEquals(0x9000, card.send(c).getSW());
        Pruefe.vorhersage("Aufgabe 2 (Kommando zweimal)", A2_SW_KOMMANDO_ZWEIMAL, card.send(c).getSW());

        card.send(new CommandAPDU(0x80, 0x50, 0x00, 0x00, new byte[8], 256));
        Pruefe.vorhersage("Aufgabe 2 (altes EXTERNAL AUTHENTICATE)", A2_SW_ALTES_EXTERNAL_AUTHENTICATE,
                card.send(host.lastExternalAuthenticate).getSW());
    }

    @Test
    public void aufgabe3_hostKryptogramm() {
        Scp03HostLoesung host = new Scp03HostLoesung(GpCardEmulator.DEFAULT_KEY);
        assertEquals("Aufgabe 3: EXTERNAL AUTHENTICATE muss 9000 liefern", 0x9000, host.open(card));
        ResponseAPDU r = card.send(host.getStatus(0x80));
        assertEquals("Aufgabe 3: danach muss GET STATUS im Kanal funktionieren", 0x9000, r.getSW());
    }

    @Test
    public void aufgabe4_lebenszyklus() {
        Scp03Host host = new Scp03Host(GpCardEmulator.DEFAULT_KEY);
        assertEquals(0x9000, host.open(card));
        int[] ist = new int[6];
        ist[0] = card.send(host.setStatus(GpCardEmulator.SECURED)).getSW();
        ist[1] = card.send(host.setStatus(GpCardEmulator.INITIALIZED)).getSW();
        ist[2] = card.send(host.setStatus(GpCardEmulator.CARD_LOCKED)).getSW();
        ist[3] = card.send(host.setStatus(GpCardEmulator.SECURED)).getSW();
        ist[4] = card.send(host.setStatus(GpCardEmulator.TERMINATED)).getSW();
        ist[5] = card.send(new CommandAPDU(0x80, 0x50, 0x00, 0x00, new byte[8], 256)).getSW();
        for (int i = 0; i < ist.length; i++) {
            Pruefe.vorhersage("Aufgabe 4, Schritt [" + i + "]", A4_VORHERSAGE[i], ist[i]);
        }
    }

    @Test
    public void aufgabe5_privilegien() {
        Boolean[] eingabe = {A5_SECURITY_DOMAIN, A5_DAP_VERIFICATION, A5_DELEGATED_MANAGEMENT, A5_CARD_LOCK, A5_CARD_TERMINATE};
        int[] bits = {0x80, 0x40, 0x20, 0x10, 0x08};
        String[] namen = {"Security Domain", "DAP Verification", "Delegated Management", "Card Lock", "Card Terminate"};
        int priv = GpCardEmulator.ISD_PRIVILEGES & 0xFF;
        for (int i = 0; i < bits.length; i++) {
            if (eingabe[i] == null) {
                fail("Aufgabe 5: '" + namen[i] + "' eintragen.");
            }
            assertEquals("Aufgabe 5: " + namen[i], (priv & bits[i]) != 0, eingabe[i].booleanValue());
        }
    }
}
