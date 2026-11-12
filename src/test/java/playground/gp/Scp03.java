package playground.gp;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.params.KeyParameter;

import java.util.Arrays;

/**
 * SCP03-Kryptografie nach GlobalPlatform Card Specification, Amendment D (aus dem Gedaechtnis
 * umgesetzt, nicht gegen eine echte Karte verifiziert). Wird von Karten-Emulation und Host genutzt.
 *
 * KDF: NIST SP 800-108 im Counter-Modus mit AES-CMAC als PRF.
 *   Ableitungsdaten = 11 x 00 (Label) || Konstante || 00 (Trenner) || L (2 Bytes, in Bit) || i (1 Byte) || Kontext
 *   Kontext         = Host-Challenge (8) || Karten-Challenge (8)
 */
public final class Scp03 {

    public static final byte DERIVE_CARD_CRYPTOGRAM = 0x00;
    public static final byte DERIVE_HOST_CRYPTOGRAM = 0x01;
    public static final byte DERIVE_S_ENC = 0x04;
    public static final byte DERIVE_S_MAC = 0x06;
    public static final byte DERIVE_S_RMAC = 0x07;

    private Scp03() {}

    public static byte[] cmac(byte[] key, byte[]... parts) {
        CMac mac = new CMac(new AESEngine());
        mac.init(new KeyParameter(key));
        for (byte[] p : parts) {
            mac.update(p, 0, p.length);
        }
        byte[] out = new byte[16];
        mac.doFinal(out, 0);
        return out;
    }

    public static byte[] kdf(byte[] key, byte constant, int lengthBits, byte[] context) {
        byte[] d = new byte[16 + context.length];
        d[11] = constant;
        d[12] = 0x00;
        d[13] = (byte) (lengthBits >> 8);
        d[14] = (byte) lengthBits;
        d[15] = 0x01;
        System.arraycopy(context, 0, d, 16, context.length);
        return Arrays.copyOf(cmac(key, d), lengthBits / 8);
    }

    public static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) len += p.length;
        byte[] out = new byte[len];
        int o = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }
}
