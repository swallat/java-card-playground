package playground.loesungen.uebung03;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.smartcardio.CommandAPDU;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static playground.support.Card.hex;

/**
 * Host-Seite (HSM + Personalisierungsmaschine) passend zu PersoLoesungApplet.
 * Der Schalter fuer Aufgabe 3 wird hier als Parameter uebergeben.
 */
public class DataPreparationLoesung {

    private static final byte[] KMC_ENC = hex("40 41 42 43 44 45 46 47 48 49 4A 4B 4C 4D 4E 4F");
    private static final byte[] KMC_MAC = hex("50 51 52 53 54 55 56 57 58 59 5A 5B 5C 5D 5E 5F");

    private final byte[] kEnc;
    private final byte[] kMac;
    private final boolean tagImMac;

    public DataPreparationLoesung(byte[] cardSerial) {
        this(cardSerial, true);
    }

    /** tagImMac muss zur Karte passen (PersoLoesungApplet: true, PersoOhneTagLoesungApplet: false). */
    public DataPreparationLoesung(byte[] cardSerial, boolean tagImMac) {
        this.tagImMac = tagImMac;
        this.kEnc = deriveCardKey(KMC_ENC, cardSerial);
        this.kMac = deriveCardKey(KMC_MAC, cardSerial);
    }

    static byte[] deriveCardKey(byte[] master, byte[] serial) {
        return aes("AES/ECB/NoPadding", Cipher.ENCRYPT_MODE, master, null, Arrays.copyOf(serial, 16));
    }

    public static byte[] installParams(byte[] serial) {
        byte[] out = new byte[40];
        System.arraycopy(serial, 0, out, 0, 8);
        System.arraycopy(deriveCardKey(KMC_ENC, serial), 0, out, 8, 16);
        System.arraycopy(deriveCardKey(KMC_MAC, serial), 0, out, 24, 16);
        return out;
    }

    public CommandAPDU storePin(String pinDigits) {
        byte[] p = pinDigits.getBytes(StandardCharsets.US_ASCII);
        byte[] block = new byte[16];
        block[0] = (byte) p.length;
        System.arraycopy(p, 0, block, 1, p.length);
        return storeData(PersoLoesungApplet.TAG_PIN, aes("AES/CBC/NoPadding", Cipher.ENCRYPT_MODE, kEnc, new byte[16], block));
    }

    public CommandAPDU storeHolder(String name) {
        return storeData(PersoLoesungApplet.TAG_HOLDER, name.getBytes(StandardCharsets.US_ASCII));
    }

    public CommandAPDU endPerso() {
        return storeData(PersoLoesungApplet.TAG_END, new byte[0]);
    }

    private CommandAPDU storeData(byte tag, byte[] payload) {
        byte[] macInput;
        if (tagImMac) {
            macInput = new byte[1 + payload.length];
            macInput[0] = tag;
            System.arraycopy(payload, 0, macInput, 1, payload.length);
        } else {
            macInput = payload.clone();
        }
        byte[] mac = cbcMac(kMac, macInput);
        byte[] data = Arrays.copyOf(payload, payload.length + 16);
        System.arraycopy(mac, 0, data, payload.length, 16);
        return new CommandAPDU(0x80, 0xE2, tag & 0xFF, 0x00, data);
    }

    private static byte[] cbcMac(byte[] key, byte[] msg) {
        byte[] in = Arrays.copyOf(msg, (msg.length / 16 + 1) * 16);
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
