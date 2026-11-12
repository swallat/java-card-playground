package playground.gp;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import static playground.support.Card.hex;

/**
 * GLOBALPLATFORM-PROTOKOLL-SIMULATION - KEIN echter Card Manager!
 *
 * jCardSim hat keine Issuer Security Domain. Diese Emulation bildet die Verwaltungsseite
 * nach (Krypto mit BouncyCastle, deshalb unter src/test, nicht CAP-faehig):
 *   80 50 KVN 00 08 <Host-Challenge> 00     INITIALIZE UPDATE
 *   84 82 SL 00 10 <Host-Kryptogramm> <C-MAC> EXTERNAL AUTHENTICATE (SL 01 = C-MAC)
 *   84 F2 P1 00 Lc 4F 00 <C-MAC> 00          GET STATUS  (P1 80 = ISD, 40 = Anwendungen)
 *   84 F0 80 P2 08 <C-MAC>                   SET STATUS  (Kartenlebenszyklus -> P2)
 * Statische Kartenschluessel: die GP-Testschluessel 40..4F (ENC = MAC = DEK).
 */
public class GpCardEmulator extends Applet {

    public static final String ISD_AID = "A0 00 00 01 51 00 00 00";

    // Kartenlebenszyklus (GP Card Specification)
    public static final byte OP_READY = 0x01;
    public static final byte INITIALIZED = 0x07;
    public static final byte SECURED = 0x0F;
    public static final byte CARD_LOCKED = 0x7F;
    public static final byte TERMINATED = (byte) 0xFF;

    // Privilegien (Byte 1): 80 Security Domain, 40 DAP Verification, 20 Delegated Management,
    // 10 Card Lock, 08 Card Terminate, 04 Card Reset, 02 CVM Management, 01 Mandated DAP
    public static final byte ISD_PRIVILEGES = (byte) 0x9E;

    public static final byte KEY_VERSION = 0x30;
    private static final short SW_AUTHENTICATION_FAILED = 0x6300;
    private static final short SW_REFERENCED_DATA_NOT_FOUND = 0x6A88;

    public static final byte[] DEFAULT_KEY = hex("40 41 42 43 44 45 46 47 48 49 4A 4B 4C 4D 4E 4F");

    /** Was GET STATUS fuer Anwendungen meldet: AID, Lebenszyklus, Privilegien (simuliertes Register). */
    static final Object[][] REGISTRY = {
            {hex("F0 00 00 00 01 01"), (byte) 0x07, (byte) 0x00},   // HelloApplet, SELECTABLE
            {hex("F0 00 00 00 01 03"), (byte) 0x0F, (byte) 0x00},   // PersoApplet, anwendungsspezifisch (PERSONALIZED)
            {hex("F0 00 00 00 01 04"), (byte) 0x07, (byte) 0x00},   // SignApplet, SELECTABLE
    };

    private final SecureRandom rng = new SecureRandom();
    private final byte[] diversificationData = hex("00 00 12 34 56 78 9A BC DE F0");

    // persistent
    private byte cardLifeCycle = OP_READY;

    // Sitzungszustand (auf echter Karte: transient)
    private byte[] context;            // Host-Challenge || Karten-Challenge, nach INITIALIZE UPDATE
    private byte[] sMac;
    private byte[] macChain;
    private boolean authenticated;

    private GpCardEmulator() {}

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new GpCardEmulator().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void deselect() {
        closeSession();
    }

    private void closeSession() {
        context = null;
        sMac = null;
        macChain = null;
        authenticated = false;
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            closeSession();
            return;
        }
        byte[] buf = apdu.getBuffer();
        if (cardLifeCycle == TERMINATED) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  // Endstation: nichts geht mehr
        }
        short len = apdu.setIncomingAndReceive();
        byte[] data = Arrays.copyOfRange(buf, apdu.getOffsetCdata(), apdu.getOffsetCdata() + len);
        byte ins = buf[ISO7816.OFFSET_INS];

        if (ins == 0x50) {
            initializeUpdate(apdu, buf, data);
            return;
        }
        if (ins == (byte) 0x82) {
            externalAuthenticate(buf, data);
            return;
        }

        // Alle Verwaltungskommandos: nur im authentisierten Kanal und nur mit gueltigem C-MAC
        data = verifyCmac(buf, data);
        switch (ins) {
            case (byte) 0xF2: getStatus(apdu, buf[ISO7816.OFFSET_P1]); break;
            case (byte) 0xF0: setStatus(buf[ISO7816.OFFSET_P1], buf[ISO7816.OFFSET_P2]); break;
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    // ------------------------------------------------------------------ SCP03-Handshake

    private void initializeUpdate(APDU apdu, byte[] buf, byte[] hostChallenge) {
        byte kvn = buf[ISO7816.OFFSET_P1];
        if (kvn != 0x00 && kvn != KEY_VERSION) {
            ISOException.throwIt(SW_REFERENCED_DATA_NOT_FOUND);
        }
        if (hostChallenge.length != 8) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        closeSession();
        byte[] cardChallenge = new byte[8];
        rng.nextBytes(cardChallenge);
        context = Scp03.concat(hostChallenge, cardChallenge);
        sMac = Scp03.kdf(DEFAULT_KEY, Scp03.DERIVE_S_MAC, 128, context);
        byte[] cardCryptogram = Scp03.kdf(sMac, Scp03.DERIVE_CARD_CRYPTOGRAM, 64, context);

        // Antwort: Diversifizierungsdaten (10) || Key-Info: KVN, SCP 03, i (3) || Karten-Challenge (8) || Kryptogramm (8)
        byte[] resp = Scp03.concat(diversificationData, new byte[]{KEY_VERSION, 0x03, 0x70}, cardChallenge, cardCryptogram);
        System.arraycopy(resp, 0, buf, 0, resp.length);
        apdu.setOutgoingAndSend((short) 0, (short) resp.length);
    }

    private void externalAuthenticate(byte[] buf, byte[] data) {
        if (context == null) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  // erst INITIALIZE UPDATE
        }
        if (data.length != 16 || buf[ISO7816.OFFSET_CLA] != (byte) 0x84) {
            closeSession();
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        byte[] hostCryptogram = Arrays.copyOf(data, 8);
        byte[] expected = Scp03.kdf(sMac, Scp03.DERIVE_HOST_CRYPTOGRAM, 64, context);
        byte[] header = Arrays.copyOf(buf, 5);
        byte[] fullMac = Scp03.cmac(sMac, new byte[16], header, hostCryptogram);   // MAC-Kette startet bei 0
        boolean macOk = MessageDigest.isEqual(Arrays.copyOf(fullMac, 8), Arrays.copyOfRange(data, 8, 16));

        if (!MessageDigest.isEqual(expected, hostCryptogram) || !macOk) {
            closeSession();
            ISOException.throwIt(SW_AUTHENTICATION_FAILED);              // 6300
        }
        context = null;            // Handshake ist verbraucht: kein zweites EXTERNAL AUTHENTICATE
        macChain = fullMac;
        authenticated = true;
    }

    /** Prueft den C-MAC am Ende der Daten und fuehrt die MAC-Kette weiter. Liefert die Daten ohne MAC. */
    private byte[] verifyCmac(byte[] buf, byte[] data) {
        if (!authenticated || (buf[ISO7816.OFFSET_CLA] & 0x04) == 0 || data.length < 8) {
            closeSession();
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);   // 6982
        }
        byte[] plain = Arrays.copyOf(data, data.length - 8);
        byte[] header = Arrays.copyOf(buf, 5);                                 // CLA INS P1 P2 Lc (inkl. MAC-Laenge)
        byte[] fullMac = Scp03.cmac(sMac, macChain, header, plain);
        if (!MessageDigest.isEqual(Arrays.copyOf(fullMac, 8), Arrays.copyOfRange(data, data.length - 8, data.length))) {
            closeSession();                                                    // Fehler beendet die Sitzung
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        macChain = fullMac;
        return plain;
    }

    // ------------------------------------------------------------------ Verwaltungskommandos

    private void getStatus(APDU apdu, byte p1) {
        byte[] out;
        if (p1 == (byte) 0x80) {
            out = entry(hex(ISD_AID), cardLifeCycle, ISD_PRIVILEGES);
        } else if (p1 == 0x40) {
            out = new byte[0];
            for (Object[] e : REGISTRY) {
                out = Scp03.concat(out, entry((byte[]) e[0], (Byte) e[1], (Byte) e[2]));
            }
        } else {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
            return;
        }
        byte[] buf = apdu.getBuffer();
        System.arraycopy(out, 0, buf, 0, out.length);
        apdu.setOutgoingAndSend((short) 0, (short) out.length);
    }

    /** GET-STATUS-Eintrag (Legacy-Format P2 = 00): Laenge AID || AID || Lebenszyklus || Privilegien */
    private static byte[] entry(byte[] aid, byte lifeCycle, byte privileges) {
        return Scp03.concat(new byte[]{(byte) aid.length}, aid, new byte[]{lifeCycle, privileges});
    }

    /** Erlaubte Uebergaenge des Kartenlebenszyklus. */
    private void setStatus(byte p1, byte newState) {
        if (p1 != (byte) 0x80) {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
        boolean allowed =
                newState == TERMINATED                                         // von ueberall, endgueltig
                || (cardLifeCycle == OP_READY && newState == INITIALIZED)
                || (cardLifeCycle == INITIALIZED && newState == SECURED)
                || (cardLifeCycle == SECURED && newState == CARD_LOCKED)
                || (cardLifeCycle == CARD_LOCKED && newState == SECURED);      // entsperren
        if (!allowed) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);         // 6985
        }
        cardLifeCycle = newState;
    }
}
