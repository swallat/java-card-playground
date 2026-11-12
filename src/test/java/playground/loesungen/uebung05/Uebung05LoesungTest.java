package playground.loesungen.uebung05;

import org.junit.Before;
import org.junit.Test;
import playground.aufgaben.Pruefe;
import playground.sign.NonceReuseAttack;
import playground.sign.SignApplet;
import playground.support.Card;

import javax.smartcardio.ResponseAPDU;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Musterloesung zum Aufgabenzettel Uebung 3 - Signatur (aufgaben/uebung-05-signatur.md).
 *
 * Loesungsstellen: ">>> LOESUNG" in dieser Datei, in SignLoesungApplet und in MiniCa.
 * Ausfuehren:     mise exec -- mvn test -Dtest=Uebung05LoesungTest
 */
public class Uebung05LoesungTest {

    private static final String AID = "F0 00 00 00 01 15";
    private static final byte[] DOCUMENT = "Dienstreiseantrag Nr. 4711".getBytes(StandardCharsets.UTF_8);

    // >>> LOESUNG AUFGABE 1: Aufbau von Public Key und DER-Signatur (Hex bzw. Anzahl Bytes)
    static final int A1_PUBKEY_ERSTES_BYTE = 0x04;     // Kennung "unkomprimierter Punkt"
    static final int A1_PUBKEY_LAENGE_X = 32;           // Bytes der X-Koordinate
    static final int A1_DER_TAG_SEQUENCE = 0x30;
    static final int A1_DER_TAG_INTEGER = 0x02;
    // <<< LOESUNG AUFGABE 1

    private Card card;

    @Before
    public void setUp() {
        card = new Card(AID, SignLoesungApplet.class);
        card.select();
    }

    private int verifyPin(Card c) { return c.send(0x00, 0x20, 0x00, 0x81, "1234".getBytes()).getSW(); }
    private byte[] generate(Card c) { return c.sendLe(0x00, 0x46, 0x80, 0x00, 256).getData(); }

    @Test
    public void aufgabe1_aufbauLesen() {
        verifyPin(card);
        byte[] w = generate(card);
        byte[] sig = card.send(0x00, 0x2A, 0x9E, 0x9A, DOCUMENT, 256).getData();
        Pruefe.wert("Aufgabe 1", "erstes Byte Public Key", A1_PUBKEY_ERSTES_BYTE, w[0] & 0xFF);
        Pruefe.wert("Aufgabe 1", "Laenge X", A1_PUBKEY_LAENGE_X, (w.length - 1) / 2);
        Pruefe.wert("Aufgabe 1", "DER-Tag SEQUENCE", A1_DER_TAG_SEQUENCE, sig[0] & 0xFF);
        Pruefe.wert("Aufgabe 1", "DER-Tag INTEGER", A1_DER_TAG_INTEGER, sig[2] & 0xFF);
    }

    @Test
    public void aufgabe2_pinProSignatur() {
        verifyPin(card);
        generate(card);
        assertEquals("erste Signatur", 0x9000, card.send(0x00, 0x2A, 0x9E, 0x9A, DOCUMENT, 256).getSW());
        assertEquals("Aufgabe 2: zweite Signatur ohne neue PIN muss 6982 liefern",
                0x6982, card.send(0x00, 0x2A, 0x9E, 0x9A, DOCUMENT, 256).getSW());
    }

    @Test
    public void aufgabe3_nurDenHashSchicken() throws Exception {
        verifyPin(card);
        ECPublicKey pub = toPublicKey(generate(card));
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(DOCUMENT);   // Host hasht
        ResponseAPDU r = card.send(0x00, 0x2A, 0x9E, 0x90, hash, 256);
        assertEquals("Aufgabe 3: Statuswort", 0x9000, r.getSW());
        assertTrue("Aufgabe 3: Signatur ueber den Hash muss als SHA256withECDSA ueber das Dokument verifizieren",
                verify(pub, DOCUMENT, r.getData()));
    }

    @Test
    public void aufgabe4_duplikatpruefungImTrustCenter() {
        MiniCa ca = new MiniCa();
        Card karteA = new Card(AID, SignApplet.class);
        Card karteB = new Card(AID, SignApplet.class);
        for (Card k : new Card[]{karteA, karteB}) {
            k.select();
            verifyPin(k);
        }
        byte[] pubA = generate(karteA);
        byte[] pubB = generate(karteB);
        assertTrue("Aufgabe 4: erster Schluessel muss angenommen werden", ca.einreichen(pubA));
        boolean zweiterAngenommen = ca.einreichen(pubB);
        System.out.println("  Aufgabe 4: Schluessel A und B identisch? " + Arrays.equals(pubA, pubB));
        assertFalse("Aufgabe 4: Die CA muss den doppelten Schluessel ablehnen", zweiterAngenommen);
    }

    // ------------------------------------------------------------------ Aufgabe 5

    /** >>> LOESUNG AUFGABE 5a: Nonce k aus zwei Signaturen mit gleichem k. Alles mod n = NonceReuseAttack.N */
    static BigInteger berechneK(BigInteger z1, BigInteger s1, BigInteger z2, BigInteger s2) {
        BigInteger n = NonceReuseAttack.N;
        // k = (z1 - z2) / (s1 - s2) mod n
        return z1.subtract(z2).multiply(s1.subtract(s2).modInverse(n)).mod(n);
    }

    /** >>> LOESUNG AUFGABE 5a: privater Schluessel d aus k: d = (s1*k - z1) / r */
    static BigInteger berechneDausK(BigInteger k, BigInteger z1, BigInteger s1, BigInteger r) {
        BigInteger n = NonceReuseAttack.N;
        return s1.multiply(k).subtract(z1).multiply(r.modInverse(n)).mod(n);
    }

    /** >>> LOESUNG AUFGABE 5b: privater Schluessel d aus EINER Signatur, wenn k == d gilt */
    static BigInteger berechneDbeiKgleichD(BigInteger z, BigInteger r, BigInteger s) {
        BigInteger n = NonceReuseAttack.N;
        // s*d = z + r*d  =>  d = z / (s - r) mod n
        return z.multiply(s.subtract(r).modInverse(n)).mod(n);
    }

    @Test
    public void aufgabe5a_zweiSignaturen() throws Exception {
        Card ref = referenzKarte();
        ECPublicKey pub = toPublicKey(generate(ref));
        byte[] m1 = DOCUMENT;
        byte[] m2 = "Anderes Dokument".getBytes(StandardCharsets.UTF_8);
        BigInteger[] rs1 = NonceReuseAttack.parseDer(ref.send(0x00, 0x2A, 0x9E, 0x9A, m1, 256).getData());
        BigInteger[] rs2 = NonceReuseAttack.parseDer(ref.send(0x00, 0x2A, 0x9E, 0x9A, m2, 256).getData());
        BigInteger k = berechneK(NonceReuseAttack.hash(m1), rs1[1], NonceReuseAttack.hash(m2), rs2[1]);
        BigInteger d = berechneDausK(k, NonceReuseAttack.hash(m1), rs1[1], rs1[0]);
        assertTrue("Aufgabe 5a: mit deinem d gefaelschte Signatur muss mit dem Kartenschluessel verifizieren",
                faelschungGelingt(d, pub));
    }

    @Test
    public void aufgabe5b_eineSignatur() throws Exception {
        Card ref = referenzKarte();
        ECPublicKey pub = toPublicKey(generate(ref));
        BigInteger[] rs = NonceReuseAttack.parseDer(ref.send(0x00, 0x2A, 0x9E, 0x9A, DOCUMENT, 256).getData());
        BigInteger d = berechneDbeiKgleichD(NonceReuseAttack.hash(DOCUMENT), rs[0], rs[1]);
        assertTrue("Aufgabe 5b: mit deinem d gefaelschte Signatur muss mit dem Kartenschluessel verifizieren",
                faelschungGelingt(d, pub));
    }

    // ------------------------------------------------------------------ Hilfen (nicht aendern)

    /** Referenz-Applet aus Uebung 3 (mit der Nonce-Schwaeche des Simulators). */
    private Card referenzKarte() {
        Card ref = new Card("F0 00 00 00 01 25", SignApplet.class);
        ref.select();
        verifyPin(ref);
        return ref;
    }

    private static boolean faelschungGelingt(BigInteger d, ECPublicKey cardPub) throws Exception {
        if (d.signum() == 0) {
            return false;
        }
        byte[] doc = "Gefaelschte Anweisung".getBytes(StandardCharsets.UTF_8);
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(NonceReuseAttack.toPrivateKey(d));
        s.update(doc);
        return verify(cardPub, doc, s.sign());
    }

    private static ECPublicKey toPublicKey(byte[] w) throws Exception {
        AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
        ap.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec p256 = ap.getParameterSpec(ECParameterSpec.class);
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(w, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(w, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, p256));
    }

    private static boolean verify(ECPublicKey pub, byte[] data, byte[] sig) throws Exception {
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initVerify(pub);
        s.update(data);
        return s.verify(sig);
    }
}
