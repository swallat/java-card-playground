package playground.perso;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.smartcardio.CommandAPDU;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static playground.support.Card.hex;

/**
 * Spielt die Herausgeber-Seite: Datenaufbereitung + Personalisierungsmaschine.
 *
 * In der Realitaet laeuft alles mit den Masterschluesseln IM HSM. Der Masterschluessel
 * (KMC, "Key Master Card") verlaesst das HSM nie; nach aussen gehen nur abgeleitete
 * Kartenschluessel bzw. bereits verschluesselte Daten.
 */
public class DataPreparation {

    // Masterschluessel des Herausgebers. 40..4F / 50..5F sind die beruehmten GP-Testschluessel.
    private static final byte[] KMC_ENC = hex("40 41 42 43 44 45 46 47 48 49 4A 4B 4C 4D 4E 4F");
    private static final byte[] KMC_MAC = hex("50 51 52 53 54 55 56 57 58 59 5A 5B 5C 5D 5E 5F");

    private final byte[] kEnc;
    private final byte[] kMac;

    /** Phase 3: Die Maschine hat die Seriennummer gelesen und laesst das HSM die Kartenschluessel ableiten. */
    public DataPreparation(byte[] cardSerial) {
        this.kEnc = deriveCardKey(KMC_ENC, cardSerial);
        this.kMac = deriveCardKey(KMC_MAC, cardSerial);
    }

    /**
     * Key Diversification (vereinfacht): K_Karte = AES_KMC(Seriennummer || 00..00).
     * Jede Karte hat so eigene Schluessel; wer eine Karte knackt, kennt nicht die der anderen.
     * Real: z. B. GP/EMV-Ableitungsverfahren bzw. NIST SP 800-108 KDF bei SCP03.
     */
    static byte[] deriveCardKey(byte[] master, byte[] serial) {
        byte[] block = Arrays.copyOf(serial, 16);
        return aes("AES/ECB/NoPadding", Cipher.ENCRYPT_MODE, master, null, block);
    }

    /**
     * Phase 1 (Kartenhersteller): Install-Parameter = Seriennummer || K_ENC || K_MAC.
     * Real bekommt der Hersteller diese Schluessel bzw. das Ableitungsverfahren ueber einen
     * gesicherten Schluesselaustausch (Key Ceremony, Transport unter einem KEK).
     */
    public static byte[] installParams(byte[] serial) {
        byte[] out = new byte[8 + 16 + 16];
        System.arraycopy(serial, 0, out, 0, 8);
        System.arraycopy(deriveCardKey(KMC_ENC, serial), 0, out, 8, 16);
        System.arraycopy(deriveCardKey(KMC_MAC, serial), 0, out, 24, 16);
        return out;
    }

    /** PIN-Block [Laenge][PIN][00...] (16 Bytes), mit K_ENC verschluesselt. */
    public CommandAPDU storePin(String pinDigits) {
        byte[] p = pinDigits.getBytes(StandardCharsets.US_ASCII);
        byte[] block = new byte[16];
        block[0] = (byte) p.length;
        System.arraycopy(p, 0, block, 1, p.length);
        byte[] cryptogram = aes("AES/CBC/NoPadding", Cipher.ENCRYPT_MODE, kEnc, new byte[16], block);
        return storeData(PersoApplet.TAG_PIN, cryptogram);
    }

    /** Name ist nicht geheim: Klartext, aber mit MAC gegen Manipulation. */
    public CommandAPDU storeHolder(String name) {
        return storeData(PersoApplet.TAG_HOLDER, name.getBytes(StandardCharsets.US_ASCII));
    }

    public CommandAPDU endPerso() {
        return storeData(PersoApplet.TAG_END, new byte[0]);
    }

    /** STORE DATA: 80 E2 <Tag> 00 Lc <Nutzdaten || MAC(Tag || Nutzdaten)> */
    private CommandAPDU storeData(byte tag, byte[] payload) {
        byte[] macInput = new byte[1 + payload.length];
        macInput[0] = tag;
        System.arraycopy(payload, 0, macInput, 1, payload.length);
        byte[] mac = cbcMac(kMac, macInput);

        byte[] data = Arrays.copyOf(payload, payload.length + 16);
        System.arraycopy(mac, 0, data, payload.length, 16);
        return new CommandAPDU(0x80, 0xE2, tag & 0xFF, 0x00, data);
    }

    /** AES-CBC-MAC mit ISO-9797-1-Padding Methode 2 (passt zu ALG_AES_MAC_128_NOPAD auf der Karte). */
    private static byte[] cbcMac(byte[] key, byte[] msg) {
        int padded = (msg.length / 16 + 1) * 16;
        byte[] in = Arrays.copyOf(msg, padded);
        in[msg.length] = (byte) 0x80;
        byte[] ct = aes("AES/CBC/NoPadding", Cipher.ENCRYPT_MODE, key, new byte[16], in);
        return Arrays.copyOfRange(ct, ct.length - 16, ct.length);
    }

    private static byte[] aes(String transformation, int mode, byte[] key, byte[] iv, byte[] data) {
        try {
            Cipher c = Cipher.getInstance(transformation);
            SecretKeySpec k = new SecretKeySpec(key, "AES");
            if (iv == null) c.init(mode, k); else c.init(mode, k, new IvParameterSpec(iv));
            return c.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
