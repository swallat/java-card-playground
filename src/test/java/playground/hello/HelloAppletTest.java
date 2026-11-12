package playground.hello;

import org.junit.Before;
import org.junit.Test;
import playground.support.Card;

import javax.smartcardio.ResponseAPDU;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class HelloAppletTest {
    private static final String AID = "F0 00 00 00 01 01";
    private Card card;

    @Before
    public void setUp() {
        card = new Card(AID, HelloApplet.class);
    }

    @Test
    public void selectReturns9000() {
        System.out.println("SELECT:");
        assertEquals(0x9000, card.select().getSW());
    }

    @Test
    public void helloReturnsText() {
        card.select();
        System.out.println("HELLO (80 01 00 00 00):");
        ResponseAPDU r = card.sendLe(0x80, 0x01, 0x00, 0x00, 256);
        assertEquals(0x9000, r.getSW());
        assertArrayEquals("Hello Card".getBytes(), r.getData());
    }

    @Test
    public void echoReturnsData() {
        card.select();
        System.out.println("ECHO (80 02 00 00 03 AA BB CC 00):");
        ResponseAPDU r = card.send(0x80, 0x02, 0x00, 0x00, new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC}, 256);
        assertEquals(0x9000, r.getSW());
        assertArrayEquals(new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC}, r.getData());
    }

    @Test
    public void unknownInsGives6D00() {
        card.select();
        System.out.println("Unbekanntes INS 0x7F:");
        assertEquals(0x6D00, card.send(0x80, 0x7F, 0x00, 0x00).getSW());
    }

    @Test
    public void wrongClaGives6E00() {
        card.select();
        System.out.println("Falsche CLA 0x00:");
        assertEquals(0x6E00, card.send(0x00, 0x01, 0x00, 0x00).getSW());
    }
}
