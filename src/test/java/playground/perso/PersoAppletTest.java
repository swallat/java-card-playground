package playground.perso;

import org.junit.Before;
import org.junit.Test;
import playground.support.Card;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class PersoAppletTest {
    private static final String AID = "F0 00 00 00 01 03";
    private static final byte[] SERIAL = Card.hex("12 34 56 78 9A BC DE F0");

    private Card card;

    @Before
    public void setUp() {
        System.out.println("== Phase 1: Vorpersonalisierung beim Kartenhersteller (Install mit Kartenschluesseln) ==");
        card = new Card(AID, PersoApplet.class, DataPreparation.installParams(SERIAL));
        card.select();
    }

    private byte[] readSerial() {
        ResponseAPDU r = card.sendLe(0x80, 0xCA, 0x9F, 0x7F, 256);
        assertEquals(0x9000, r.getSW());
        return r.getData();
    }

    private void personalize(String pin, String name) {
        System.out.println("== Phase 3: Personalisierungsmaschine liest Seriennummer ==");
        byte[] serial = readSerial();
        System.out.println("== Phase 2: HSM leitet Kartenschluessel ab, bereitet Daten auf ==");
        DataPreparation dp = new DataPreparation(serial);
        System.out.println("== Phase 3: STORE DATA ==");
        assertEquals(0x9000, card.send(dp.storePin(pin)).getSW());
        assertEquals(0x9000, card.send(dp.storeHolder(name)).getSW());
        assertEquals(0x9000, card.send(dp.endPerso()).getSW());
    }

    @Test
    public void unpersonalizedCardIsUnusable() {
        System.out.println("Nicht personalisierte Karte: VERIFY geht nicht:");
        assertEquals(0x6985, card.send(0x00, 0x20, 0x00, 0x81, "1234".getBytes()).getSW());
    }

    @Test
    public void fullPersonalizationThenUse() {
        personalize("4711", "Max Mustermann");
        System.out.println("== Phase 5: Karteninhaber nutzt die Karte ==");
        assertEquals(0x6982, card.sendLe(0x80, 0x12, 0x00, 0x00, 256).getSW());
        assertEquals(0x9000, card.send(0x00, 0x20, 0x00, 0x81, "4711".getBytes()).getSW());
        ResponseAPDU r = card.sendLe(0x80, 0x12, 0x00, 0x00, 256);
        assertEquals(0x9000, r.getSW());
        assertArrayEquals("Max Mustermann".getBytes(), r.getData());
    }

    @Test
    public void tamperedDataIsRejected() {
        System.out.println("Manipulierte Personalisierungsdaten (1 Bit gekippt):");
        CommandAPDU c = new DataPreparation(SERIAL).storePin("4711");
        byte[] data = c.getData();
        data[3] ^= 0x01;
        assertEquals(0x6982, card.send(0x80, 0xE2, c.getP1(), c.getP2(), data).getSW());
    }

    @Test
    public void dataPreparedForAnotherCardIsRejected() {
        System.out.println("Daten fuer eine ANDERE Karte (andere Seriennummer -> andere Schluessel):");
        DataPreparation other = new DataPreparation(Card.hex("00 00 00 00 00 00 00 01"));
        assertEquals(0x6982, card.send(other.storePin("4711")).getSW());
    }

    @Test
    public void swappingTheTagIsRejected() {
        System.out.println("Verschluesselte PIN als 'Name' einspielen (Tag getauscht):");
        CommandAPDU c = new DataPreparation(SERIAL).storePin("4711");
        assertEquals(0x6982, card.send(0x80, 0xE2, PersoApplet.TAG_HOLDER, 0x00, c.getData()).getSW());
    }

    @Test
    public void incompletePersonalizationCannotBeFinished() {
        System.out.println("Abschluss ohne PIN:");
        assertEquals(0x6985, card.send(new DataPreparation(SERIAL).endPerso()).getSW());
    }

    @Test
    public void noPersonalizationAfterEnd() {
        personalize("4711", "Max Mustermann");
        System.out.println("Nach Abschluss: neue PIN einspielen?");
        assertEquals(0x6985, card.send(new DataPreparation(SERIAL).storePin("0000")).getSW());
    }
}
