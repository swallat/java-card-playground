package playground.aufgaben.uebung08;

import playground.gp.Scp03;
import playground.support.Card;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Aufgaben-Kopie der SCP03-Host-Seite (Aufgabenzettel GlobalPlatform, Aufgabe 3).
 * Eingabestelle: ">>> AUFGABE 3".
 */
public class Scp03HostAufgabe {

    private final byte[] keyMac;
    private byte[] sMac;
    private byte[] macChain;

    public Scp03HostAufgabe(byte[] keyMac) {
        this.keyMac = keyMac.clone();
    }

    /** Liefert das Statuswort von EXTERNAL AUTHENTICATE oder -1, wenn die Karte abgelehnt wurde. */
    public int open(Card card) {
        byte[] hostChallenge = new byte[8];
        new SecureRandom().nextBytes(hostChallenge);
        ResponseAPDU r = card.send(new CommandAPDU(0x80, 0x50, 0x00, 0x00, hostChallenge, 256));
        if (r.getSW() != 0x9000) {
            return r.getSW();
        }
        byte[] resp = r.getData();
        byte[] cardChallenge = Arrays.copyOfRange(resp, 13, 21);
        byte[] cardCryptogram = Arrays.copyOfRange(resp, 21, 29);
        byte[] context = Scp03.concat(hostChallenge, cardChallenge);

        sMac = Scp03.kdf(keyMac, Scp03.DERIVE_S_MAC, 128, context);
        if (!MessageDigest.isEqual(Scp03.kdf(sMac, Scp03.DERIVE_CARD_CRYPTOGRAM, 64, context), cardCryptogram)) {
            return -1;
        }

        // >>> AUFGABE 3: Host-Kryptogramm berechnen. Es wird wie das Karten-Kryptogramm abgeleitet,
        //     aber mit einer anderen Ableitungskonstante (siehe Konstanten in Scp03).
        byte[] hostCryptogram = new byte[8];
        // <<< AUFGABE 3

        macChain = new byte[16];
        return card.send(wrap(0x84, 0x82, 0x01, 0x00, hostCryptogram)).getSW();
    }

    public CommandAPDU wrap(int cla, int ins, int p1, int p2, byte[] data) {
        int smCla = cla | 0x04;
        byte[] header = {(byte) smCla, (byte) ins, (byte) p1, (byte) p2, (byte) (data.length + 8)};
        byte[] fullMac = Scp03.cmac(sMac, macChain, header, data);
        macChain = fullMac;
        byte[] withMac = Scp03.concat(data, Arrays.copyOf(fullMac, 8));
        return ins == 0xF2 ? new CommandAPDU(smCla, ins, p1, p2, withMac, 256) : new CommandAPDU(smCla, ins, p1, p2, withMac);
    }

    public CommandAPDU getStatus(int p1) {
        return wrap(0x80, 0xF2, p1, 0x00, new byte[]{0x4F, 0x00});
    }
}
