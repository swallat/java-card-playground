package playground.perso;

import org.junit.Before;
import org.junit.Test;
import playground.support.Card;

import javax.smartcardio.CommandAPDU;

import static org.junit.Assert.assertEquals;

/** Verwaltung nach der Ausgabe: Issuer Scripts mit K_SCRIPT. */
public class IssuerScriptTest {
    private static final String AID = "F0 00 00 00 01 03";
    private static final byte[] SERIAL = Card.hex("12 34 56 78 9A BC DE F0");

    private Card card;
    private IssuerHost issuer;

    @Before
    public void setUp() {
        card = new Card(AID, PersoApplet.class, DataPreparation.installParams(SERIAL));
        card.select();
        issuer = new IssuerHost(SERIAL);
    }

    private void personalize() {
        System.out.println("== Personalisierung (PIN 4711, K_SCRIPT Generation 1) ==");
        DataPreparation dp = new DataPreparation(SERIAL);
        assertEquals(0x9000, card.send(dp.storePin("4711")).getSW());
        assertEquals(0x9000, card.send(dp.storeHolder("Max Mustermann")).getSW());
        assertEquals(0x9000, card.send(dp.storeScriptKey()).getSW());
        assertEquals(0x9000, card.send(dp.endPerso()).getSW());
    }

    private int verify(String pin) {
        return card.send(0x00, 0x20, 0x00, 0x81, pin.getBytes()).getSW();
    }

    @Test
    public void scriptChangesPinAfterIssuance() {
        personalize();
        System.out.println("== Autorisierungszentrale: neue PIN 2580 per Issuer Script ==");
        assertEquals(0x9000, card.send(issuer.changePin("2580")).getSW());
        assertEquals(0x63C2, verify("4711"));   // alte PIN gilt nicht mehr
        assertEquals(0x9000, verify("2580"));
    }

    @Test
    public void scriptUnblocksBlockedPin() {
        personalize();
        System.out.println("== Karteninhaber sperrt seine PIN ==");
        verify("0000");
        verify("0000");
        verify("0000");
        assertEquals(0x6983, verify("4711"));
        System.out.println("== Autorisierungszentrale entsperrt per Skript (neue PIN 2580) ==");
        assertEquals(0x9000, card.send(issuer.changePin("2580")).getSW());
        assertEquals(0x9000, verify("2580"));
    }

    @Test
    public void replayedScriptIsRejected() {
        personalize();
        System.out.println("== Skript einmal ausfuehren, dann mitgeschnitten wieder einspielen ==");
        CommandAPDU recorded = issuer.changePin("2580");
        assertEquals(0x9000, card.send(recorded).getSW());
        assertEquals(0x6985, card.send(recorded).getSW());   // Zaehler nicht hoeher -> Replay
    }

    @Test
    public void tamperedScriptIsRejected() {
        personalize();
        System.out.println("== Manipuliertes Skript (1 Bit im Kryptogramm) ==");
        CommandAPDU c = issuer.changePin("2580");
        byte[] data = c.getData();
        data[5] ^= 0x01;
        assertEquals(0x6988, card.send(0x84, c.getINS(), c.getP1(), c.getP2(), data).getSW());
    }

    @Test
    public void changePinCannotBeTurnedIntoPutKey() {
        personalize();
        System.out.println("== PIN-Skript als PUT KEY umetikettiert ==");
        CommandAPDU c = issuer.changePin("2580");
        assertEquals(0x6988, card.send(0x84, 0xD8, 0x02, 0x00, c.getData()).getSW());
    }

    @Test
    public void scriptsAreRejectedBeforeIssuance() {
        System.out.println("== Skript an eine noch nicht personalisierte Karte ==");
        assertEquals(0x6985, card.send(issuer.changePin("2580")).getSW());
    }

    @Test
    public void scriptKeyCanBeRotated() {
        personalize();
        System.out.println("== PUT KEY: K_SCRIPT Generation 1 -> 2 ==");
        assertEquals(0x9000, card.send(issuer.putKey(2)).getSW());
        System.out.println("== Skript mit Generation 2 wird akzeptiert ==");
        assertEquals(0x9000, card.send(issuer.changePin("2580")).getSW());
        assertEquals(0x9000, verify("2580"));

        System.out.println("== Ein Host, der noch Generation 1 nutzt, wird abgewiesen ==");
        IssuerHost oldHost = new IssuerHost(SERIAL);
        oldHost.setCounter(100);                 // Zaehler waere hoch genug, Schluessel ist veraltet
        assertEquals(0x6988, card.send(oldHost.changePin("1111")).getSW());
    }
}
