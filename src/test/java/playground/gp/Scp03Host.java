package playground.gp;

import playground.support.Card;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Host-Seite von SCP03 (wie sie z. B. GlobalPlatformPro implementiert): Kanal oeffnen,
 * Karte authentisieren, Kommandos mit C-MAC versehen.
 */
public class Scp03Host {

    private final byte[] keyMac;
    private byte[] sMac;
    private byte[] macChain;

    /** Zuletzt gesendetes EXTERNAL AUTHENTICATE (fuer den Replay-Test). */
    public CommandAPDU lastExternalAuthenticate;
    /** Ergebnis der Kartenpruefung im letzten Handshake. */
    public boolean cardAuthenticated;

    public Scp03Host(byte[] keyMac) {
        this.keyMac = keyMac.clone();
    }

    /**
     * Schritt 1 + 2 des Handshakes. Liefert das Statuswort von EXTERNAL AUTHENTICATE,
     * oder -1, wenn der Host die KARTE abgelehnt hat (Karten-Kryptogramm falsch).
     */
    public int open(Card card) {
        byte[] hostChallenge = new byte[8];
        new SecureRandom().nextBytes(hostChallenge);

        ResponseAPDU r = card.send(new CommandAPDU(0x80, 0x50, 0x00, 0x00, hostChallenge, 256)); // INITIALIZE UPDATE
        if (r.getSW() != 0x9000) {
            return r.getSW();
        }
        byte[] resp = r.getData();
        byte[] cardChallenge = Arrays.copyOfRange(resp, 13, 21);
        byte[] cardCryptogram = Arrays.copyOfRange(resp, 21, 29);
        byte[] context = Scp03.concat(hostChallenge, cardChallenge);

        sMac = Scp03.kdf(keyMac, Scp03.DERIVE_S_MAC, 128, context);
        cardAuthenticated = MessageDigest.isEqual(
                Scp03.kdf(sMac, Scp03.DERIVE_CARD_CRYPTOGRAM, 64, context), cardCryptogram);
        if (!cardAuthenticated) {
            System.out.println("  Host: Karten-Kryptogramm falsch -> Karte kennt die Schluessel nicht, Abbruch");
            return -1;
        }

        byte[] hostCryptogram = Scp03.kdf(sMac, Scp03.DERIVE_HOST_CRYPTOGRAM, 64, context);
        macChain = new byte[16];
        lastExternalAuthenticate = wrap(0x84, 0x82, 0x01, 0x00, hostCryptogram);   // SL 01 = C-MAC
        return card.send(lastExternalAuthenticate).getSW();
    }

    /** Kommando im Kanal: CLA mit SM-Bit, Daten || C-MAC (8 Bytes), MAC-Kette fortfuehren. */
    public CommandAPDU wrap(int cla, int ins, int p1, int p2, byte[] data) {
        int smCla = cla | 0x04;
        byte[] header = {(byte) smCla, (byte) ins, (byte) p1, (byte) p2, (byte) (data.length + 8)};
        byte[] fullMac = Scp03.cmac(sMac, macChain, header, data);
        macChain = fullMac;
        byte[] withMac = Scp03.concat(data, Arrays.copyOf(fullMac, 8));
        return ins == 0xF2
                ? new CommandAPDU(smCla, ins, p1, p2, withMac, 256)
                : new CommandAPDU(smCla, ins, p1, p2, withMac);
    }

    public CommandAPDU getStatus(int p1) {
        return wrap(0x80, 0xF2, p1, 0x00, new byte[]{0x4F, 0x00});
    }

    public CommandAPDU setStatus(int newState) {
        return wrap(0x80, 0xF0, 0x80, newState & 0xFF, new byte[0]);
    }
}
