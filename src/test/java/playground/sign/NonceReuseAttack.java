package playground.sign;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPrivateKeySpec;
import java.util.Arrays;

/**
 * Off-card-Angriff auf ECDSA mit wiederverwendeter Nonce k (vgl. PlayStation 3, 2010).
 *
 * ECDSA:  r = x(k*G) mod n,   s = k^-1 * (z + r*d) mod n,   z = SHA-256(Nachricht)
 *
 * Gleiches k bei zwei Nachrichten -> gleiches r, und:
 *   s1 - s2 = k^-1 * (z1 - z2)   =>   k = (z1 - z2) / (s1 - s2)
 *   d = (s1*k - z1) / r
 */
public final class NonceReuseAttack {

    public static final BigInteger N = new BigInteger(
            "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551", 16);

    private NonceReuseAttack() {}

    /** DER SEQUENCE { INTEGER r, INTEGER s } -> {r, s} */
    public static BigInteger[] parseDer(byte[] der) {
        int o = 2;                                     // 30 <len>
        if ((der[1] & 0x80) != 0) o += der[1] & 0x7F;  // lange Laengenform (bei ECDSA-P256 nicht noetig)
        BigInteger[] rs = new BigInteger[2];
        for (int i = 0; i < 2; i++) {
            if (der[o] != 0x02) throw new IllegalArgumentException("INTEGER erwartet");
            int len = der[o + 1];
            rs[i] = new BigInteger(1, Arrays.copyOfRange(der, o + 2, o + 2 + len));
            o += 2 + len;
        }
        return rs;
    }

    public static BigInteger hash(byte[] msg) throws Exception {
        return new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(msg));
    }

    /** Privaten Schluessel d aus zwei Signaturen mit gleichem k berechnen. */
    static BigInteger recoverPrivateKey(byte[] m1, byte[] sig1, byte[] m2, byte[] sig2) throws Exception {
        BigInteger[] rs1 = parseDer(sig1);
        BigInteger[] rs2 = parseDer(sig2);
        BigInteger r = rs1[0];
        if (!r.equals(rs2[0])) throw new IllegalArgumentException("r unterschiedlich: keine Nonce-Wiederverwendung");
        BigInteger z1 = hash(m1), z2 = hash(m2);
        BigInteger s1 = rs1[1], s2 = rs2[1];

        BigInteger k = z1.subtract(z2).multiply(s1.subtract(s2).modInverse(N)).mod(N);
        return s1.multiply(k).subtract(z1).multiply(r.modInverse(N)).mod(N);
    }

    /**
     * Noch schlimmer: Ist die Nonce k gleich dem privaten Schluessel d, gilt
     *   s = d^-1 * (z + r*d)   =>   s*d = z + r*d   =>   d = z / (s - r)
     * Dann reicht EINE Signatur.
     */
    static BigInteger recoverFromSingleSignatureWhenNonceEqualsKey(byte[] msg, byte[] sig) throws Exception {
        BigInteger[] rs = parseDer(sig);
        return hash(msg).multiply(rs[1].subtract(rs[0]).modInverse(N)).mod(N);
    }

    public static PrivateKey toPrivateKey(BigInteger d) throws Exception {
        AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
        ap.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec p256 = ap.getParameterSpec(ECParameterSpec.class);
        return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(d, p256));
    }
}
