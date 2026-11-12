package playground.gp;

import org.junit.Before;
import org.junit.Test;
import playground.support.Card;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static playground.support.Card.hex;

public class GpCardTest {

    private Card card;
    private Scp03Host host;

    @Before
    public void setUp() {
        card = new Card(GpCardEmulator.ISD_AID, GpCardEmulator.class);
        System.out.println("SELECT Issuer Security Domain:");
        assertEquals(0x9000, card.select().getSW());
        host = new Scp03Host(GpCardEmulator.DEFAULT_KEY);
    }

    // ================================================================ Secure Channel

    @Test
    public void managementWithoutSecureChannelIsRejected() {
        System.out.println("GET STATUS ohne Secure Channel:");
        assertEquals(0x6982, card.sendLe(0x80, 0xF2, 0x80, 0x00, 256).getSW());
    }

    @Test
    public void initializeUpdateResponseStructure() {
        System.out.println("INITIALIZE UPDATE:");
        ResponseAPDU r = card.send(new CommandAPDU(0x80, 0x50, 0x00, 0x00, new byte[8], 256));
        assertEquals(0x9000, r.getSW());
        byte[] d = r.getData();
        assertEquals(29, d.length);                                   // 10 + 3 + 8 + 8
        assertArrayEquals("Key-Info: KVN 30, SCP 03, i 70", hex("30 03 70"), Arrays.copyOfRange(d, 10, 13));
    }

    @Test
    public void handshakeSucceedsWithRightKeys() {
        System.out.println("SCP03-Handshake mit den richtigen Schluesseln:");
        assertEquals(0x9000, host.open(card));
        assertTrue("Host hat die Karte authentisiert", host.cardAuthenticated);
    }

    @Test
    public void hostDetectsCardWithWrongKeys() {
        System.out.println("Host mit ANDEREN Schluesseln (z. B. gefaelschte Karte aus Host-Sicht):");
        Scp03Host other = new Scp03Host(hex("00 11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF"));
        assertEquals(-1, other.open(card));
        assertFalse(other.cardAuthenticated);
    }

    @Test
    public void cardRejectsWrongHostCryptogram() {
        assertEquals(0x9000, host.open(card));
        System.out.println("Neue Sitzung, aber EXTERNAL AUTHENTICATE aus der ALTEN Sitzung wiederholen:");
        card.send(new CommandAPDU(0x80, 0x50, 0x00, 0x00, new byte[8], 256));     // neue Challenges
        assertEquals(0x6300, card.send(host.lastExternalAuthenticate).getSW());
    }

    @Test
    public void tamperedCommandClosesSession() {
        assertEquals(0x9000, host.open(card));
        CommandAPDU c = host.getStatus(0x80);
        byte[] data = c.getData();
        data[data.length - 1] ^= 0x01;                                // MAC verfaelscht
        System.out.println("GET STATUS mit verfaelschtem C-MAC:");
        assertEquals(0x6982, card.send(new CommandAPDU(c.getCLA(), c.getINS(), c.getP1(), c.getP2(), data, 256)).getSW());
        System.out.println("Danach ist die Sitzung geschlossen, auch ein korrektes Kommando scheitert:");
        assertEquals(0x6982, card.send(host.getStatus(0x80)).getSW());
    }

    @Test
    public void replayedCommandFailsBecauseOfMacChaining() {
        assertEquals(0x9000, host.open(card));
        CommandAPDU c = host.getStatus(0x80);
        System.out.println("GET STATUS einmal korrekt, dann exakt dasselbe APDU noch einmal:");
        assertEquals(0x9000, card.send(c).getSW());
        assertEquals(0x6982, card.send(c).getSW());
    }

    // ================================================================ GET STATUS

    @Test
    public void getStatusShowsIsdAndApplications() {
        assertEquals(0x9000, host.open(card));
        System.out.println("GET STATUS ISD (P1 = 80):");
        byte[] isd = card.send(host.getStatus(0x80)).getData();
        assertArrayEquals(hex(GpCardEmulator.ISD_AID), Arrays.copyOfRange(isd, 1, 9));
        assertEquals("Lebenszyklus OP_READY", GpCardEmulator.OP_READY, isd[9]);
        assertEquals("Privilegien 9E", GpCardEmulator.ISD_PRIVILEGES, isd[10]);

        System.out.println("GET STATUS Anwendungen (P1 = 40):");
        byte[] apps = card.send(host.getStatus(0x40)).getData();
        List<String> lines = new ArrayList<String>();
        for (int o = 0; o < apps.length; ) {
            int len = apps[o];
            lines.add(String.format("  AID %s  Lebenszyklus %02X  Privilegien %02X",
                    Card.hex(Arrays.copyOfRange(apps, o + 1, o + 1 + len)), apps[o + 1 + len], apps[o + 2 + len]));
            o += len + 3;
        }
        for (String l : lines) System.out.println(l);
        assertEquals(3, lines.size());
    }

    // ================================================================ Kartenlebenszyklus

    @Test
    public void cardLifeCycleForwardAndLock() {
        assertEquals(0x9000, host.open(card));
        System.out.println("OP_READY -> INITIALIZED -> SECURED -> CARD_LOCKED -> SECURED:");
        assertEquals(0x9000, card.send(host.setStatus(GpCardEmulator.INITIALIZED)).getSW());
        assertEquals(0x9000, card.send(host.setStatus(GpCardEmulator.SECURED)).getSW());
        assertEquals(0x9000, card.send(host.setStatus(GpCardEmulator.CARD_LOCKED)).getSW());
        assertEquals(0x9000, card.send(host.setStatus(GpCardEmulator.SECURED)).getSW());
        assertEquals(GpCardEmulator.SECURED, card.send(host.getStatus(0x80)).getData()[9]);
    }

    @Test
    public void lifeCycleCannotGoBackwards() {
        assertEquals(0x9000, host.open(card));
        card.send(host.setStatus(GpCardEmulator.INITIALIZED));
        card.send(host.setStatus(GpCardEmulator.SECURED));
        System.out.println("SECURED -> INITIALIZED (zurueck) ist verboten:");
        ResponseAPDU r = card.send(host.setStatus(GpCardEmulator.INITIALIZED));
        assertEquals(0x6985, r.getSW());
    }

    @Test
    public void terminatedIsFinal() {
        assertEquals(0x9000, host.open(card));
        System.out.println("TERMINATED - danach geht nichts mehr, auch kein neuer Secure Channel:");
        assertEquals(0x9000, card.send(host.setStatus(GpCardEmulator.TERMINATED)).getSW());
        assertEquals(0x6985, card.send(new CommandAPDU(0x80, 0x50, 0x00, 0x00, new byte[8], 256)).getSW());
    }
}
