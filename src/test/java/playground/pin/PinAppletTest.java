package playground.pin;

import org.junit.Before;
import org.junit.Test;
import playground.support.Card;

import javax.smartcardio.ResponseAPDU;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class PinAppletTest {
    private static final String AID = "F0 00 00 00 01 02";
    private static final byte[] RIGHT_PIN = {'1', '2', '3', '4'};
    private static final byte[] WRONG_PIN = {'9', '9', '9', '9'};

    private Card card;

    @Before
    public void setUp() {
        card = new Card(AID, PinApplet.class);
        card.select();
    }

    private ResponseAPDU verify(byte[] pin) { return card.send(0x00, 0x20, 0x00, 0x81, pin); }
    private ResponseAPDU verifyStatus()     { return card.send(0x00, 0x20, 0x00, 0x81); }
    private ResponseAPDU getSecret()        { return card.sendLe(0x80, 0x10, 0x00, 0x00, 256); }

    @Test
    public void secretWithoutPinGives6982() {
        System.out.println("GET SECRET ohne PIN:");
        assertEquals(0x6982, getSecret().getSW());
    }

    @Test
    public void rightPinUnlocksSecret() {
        System.out.println("VERIFY richtig, dann GET SECRET:");
        assertEquals(0x9000, verify(RIGHT_PIN).getSW());
        ResponseAPDU r = getSecret();
        assertEquals(0x9000, r.getSW());
        assertArrayEquals("Geheim".getBytes(), r.getData());
    }

    @Test
    public void wrongPinCountsDownAndRightPinResetsCounter() {
        System.out.println("2x falsch, dann richtig, dann Status:");
        assertEquals(0x63C2, verify(WRONG_PIN).getSW());
        assertEquals(0x63C1, verify(WRONG_PIN).getSW());
        assertEquals(0x9000, verify(RIGHT_PIN).getSW());
        // Nach Erfolg steht der Zaehler wieder auf 3, Status-Abfrage liefert 9000 (validiert)
        assertEquals(0x9000, verifyStatus().getSW());
    }

    @Test
    public void threeWrongPinsBlock() {
        System.out.println("3x falsch -> gesperrt, auch die richtige PIN hilft nicht mehr:");
        assertEquals(0x63C2, verify(WRONG_PIN).getSW());
        assertEquals(0x63C1, verify(WRONG_PIN).getSW());
        assertEquals(0x63C0, verify(WRONG_PIN).getSW());
        assertEquals(0x6983, verify(RIGHT_PIN).getSW());
        assertEquals(0x6982, getSecret().getSW());
    }

    @Test
    public void statusQueryDoesNotUseATry() {
        System.out.println("VERIFY ohne Daten = Statusabfrage:");
        assertEquals(0x63C3, verifyStatus().getSW());
        assertEquals(0x63C3, verifyStatus().getSW()); // kein Versuch verbraucht
    }

    @Test
    public void resetKeepsTryCounter() {
        System.out.println("Fehlversuch, Reset: Zaehler ist persistent (EEPROM):");
        assertEquals(0x63C2, verify(WRONG_PIN).getSW());
        card.reset();
        card.select();
        assertEquals(0x63C2, verifyStatus().getSW()); // immer noch nur 2 Versuche
    }

    @Test
    public void resetClearsValidation() {
        System.out.println("PIN ok, Reset: Validierung ist transient (RAM):");
        assertEquals(0x9000, verify(RIGHT_PIN).getSW());
        assertEquals(0x9000, getSecret().getSW());
        card.reset();
        card.select();
        assertEquals(0x6982, getSecret().getSW()); // PIN muss neu eingegeben werden
    }
}
