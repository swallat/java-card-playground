package playground.sign;

import org.junit.Before;
import org.junit.Test;
import playground.support.Card;

import javax.smartcardio.ResponseAPDU;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SignAppletTest {
    private static final String AID = "F0 00 00 00 01 04";
    private static final byte[] DOCUMENT = "Dienstreiseantrag Nr. 4711".getBytes(StandardCharsets.UTF_8);

    private Card card;

    @Before
    public void setUp() {
        card = new Card(AID, SignApplet.class);
        card.select();
    }

    private int verifyPin() { return card.send(0x00, 0x20, 0x00, 0x81, "1234".getBytes()).getSW(); }
    private ResponseAPDU generate()    { return card.sendLe(0x00, 0x46, 0x80, 0x00, 256); }
    private ResponseAPDU readPublic()  { return card.sendLe(0x00, 0x46, 0x81, 0x00, 256); }
    private ResponseAPDU sign(byte[] d) { return card.send(0x00, 0x2A, 0x9E, 0x9A, d, 256); }

    /** Off-card: 65-Byte-Punkt (04 || X || Y) in einen Java-PublicKey fuer P-256 umwandeln. */
    static ECPublicKey toPublicKey(byte[] w) throws Exception {
        AlgorithmParameters ap = AlgorithmParameters.getInstance("EC");
        ap.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec p256 = ap.getParameterSpec(ECParameterSpec.class);
        ECPoint point = new ECPoint(
                new BigInteger(1, Arrays.copyOfRange(w, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(w, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, p256));
    }

    /** Off-card-Pruefung mit Standard-Java-Krypto, nur mit dem oeffentlichen Schluessel. */
    static boolean verifyOffCard(ECPublicKey pub, byte[] data, byte[] signature) throws Exception {
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initVerify(pub);
        s.update(data);
        return s.verify(signature);
    }

    @Test
    public void generateWithoutPinIsRejected() {
        System.out.println("Schluessel erzeugen ohne PIN:");
        assertEquals(0x6982, generate().getSW());
    }

    @Test
    public void signWithoutKeyIsRejected() {
        System.out.println("Signieren, bevor ein Schluessel existiert:");
        assertEquals(0x9000, verifyPin());
        assertEquals(0x6985, sign(DOCUMENT).getSW());
    }

    @Test
    public void generateReturnsUncompressedPublicPoint() throws Exception {
        System.out.println("Schluesselpaar auf der Karte erzeugen:");
        assertEquals(0x9000, verifyPin());
        ResponseAPDU r = generate();
        assertEquals(0x9000, r.getSW());
        assertEquals(65, r.getData().length);          // 04 || X (32) || Y (32)
        assertEquals(0x04, r.getData()[0]);
        toPublicKey(r.getData());                      // muss ein gueltiger P-256-Punkt sein
        System.out.println("Oeffentlichen Schluessel erneut lesen (ohne PIN):");
        assertArrayEquals(r.getData(), readPublic().getData());
    }

    @Test
    public void signWithoutPinIsRejected() {
        assertEquals(0x9000, verifyPin());
        assertEquals(0x9000, generate().getSW());
        card.reset();
        card.select();
        System.out.println("Nach Reset ohne PIN signieren:");
        assertEquals(0x6982, sign(DOCUMENT).getSW());
    }

    @Test
    public void signOnCardVerifyOffCard() throws Exception {
        assertEquals(0x9000, verifyPin());
        ECPublicKey pub = toPublicKey(generate().getData());

        System.out.println("Dokument auf der Karte signieren:");
        ResponseAPDU r = sign(DOCUMENT);
        assertEquals(0x9000, r.getSW());
        byte[] signature = r.getData();
        assertEquals(0x30, signature[0] & 0xFF);       // DER: SEQUENCE { INTEGER r, INTEGER s }

        assertTrue("Signatur muss off-card verifizierbar sein", verifyOffCard(pub, DOCUMENT, signature));

        byte[] forged = DOCUMENT.clone();
        forged[forged.length - 1] = '2';               // "4712" statt "4711"
        assertFalse("Veraendertes Dokument darf nicht verifizieren", verifyOffCard(pub, forged, signature));
    }

    /**
     * SIMULATOR-SCHWAECHE (jCardSim 3.0.6.0): Signature.init(MODE_SIGN) nutzt einen ungeseedeten
     * DigestRandomGenerator -> jede Signatur hat dieselbe Nonce k -> gleiches r.
     * Auf einer echten Karte waere das ein kritischer Fehler. Dieser Test dokumentiert das Verhalten.
     */
    @Test
    public void simulatorReusesEcdsaNonce() {
        assertEquals(0x9000, verifyPin());
        assertEquals(0x9000, generate().getSW());
        System.out.println("Zwei VERSCHIEDENE Dokumente signieren und r vergleichen:");
        byte[] s1 = sign(DOCUMENT).getData();
        byte[] s2 = sign("Anderes Dokument".getBytes(StandardCharsets.UTF_8)).getData();
        assertEquals("Gleiches r = gleiche Nonce k (Simulator-Schwaeche)",
                NonceReuseAttack.parseDer(s1)[0], NonceReuseAttack.parseDer(s2)[0]);
    }

    /** Der private Schluessel "verlaesst die Karte nie" - aber eine schlechte Nonce verraet ihn. */
    @Test
    public void nonceReuseLeaksPrivateKey() throws Exception {
        assertEquals(0x9000, verifyPin());
        ECPublicKey cardPub = toPublicKey(generate().getData());
        byte[] m1 = DOCUMENT;
        byte[] m2 = "Anderes Dokument".getBytes(StandardCharsets.UTF_8);
        byte[] sig1 = sign(m1).getData();
        byte[] sig2 = sign(m2).getData();

        System.out.println("Angreifer rechnet aus zwei Signaturen den privaten Schluessel aus ...");
        java.math.BigInteger d = NonceReuseAttack.recoverPrivateKey(m1, sig1, m2, sig2);
        System.out.println("  d = " + d.toString(16));

        // Gegenprobe: mit dem errechneten d off-card signieren, mit dem Public Key DER KARTE pruefen
        byte[] forgedDoc = "Gefaelschte Anweisung".getBytes(StandardCharsets.UTF_8);
        Signature attacker = Signature.getInstance("SHA256withECDSA");
        attacker.initSign(NonceReuseAttack.toPrivateKey(d));
        attacker.update(forgedDoc);
        byte[] forgedSig = attacker.sign();
        assertTrue("Faelschung muss mit dem Kartenschluessel verifizieren", verifyOffCard(cardPub, forgedDoc, forgedSig));
    }

    @Test
    public void privateKeySurvivesReset() throws Exception {
        assertEquals(0x9000, verifyPin());
        ECPublicKey pub = toPublicKey(generate().getData());
        card.reset();
        card.select();
        System.out.println("Nach Reset: PIN, signieren, mit dem ALTEN oeffentlichen Schluessel pruefen:");
        assertEquals(0x9000, verifyPin());
        assertTrue(verifyOffCard(pub, DOCUMENT, sign(DOCUMENT).getData()));
    }

    /** SIMULATOR-SCHWAECHE: Jede frisch simulierte Karte erzeugt als erstes denselben Schluessel. */
    @Test
    public void simulatorGeneratesSameFirstKeyOnEveryCard() {
        assertEquals(0x9000, verifyPin());
        byte[] pubCardA = generate().getData();

        System.out.println("Zweite, komplett neue Karte:");
        Card other = new Card(AID, SignApplet.class);
        other.select();
        other.send(0x00, 0x20, 0x00, 0x81, "1234".getBytes());
        byte[] pubCardB = other.sendLe(0x00, 0x46, 0x80, 0x00, 256).getData();

        assertArrayEquals("Gleicher Schluessel auf zwei 'Karten' (Simulator-Schwaeche)", pubCardA, pubCardB);
    }

    /** SIMULATOR-SCHWAECHE: Nonce k == privater Schluessel d, eine Signatur genuegt. */
    @Test
    public void singleSignatureLeaksKeyBecauseNonceEqualsKey() throws Exception {
        assertEquals(0x9000, verifyPin());
        byte[] w = generate().getData();
        ECPublicKey cardPub = toPublicKey(w);
        byte[] sig = sign(DOCUMENT).getData();

        java.math.BigInteger r = NonceReuseAttack.parseDer(sig)[0];
        java.math.BigInteger x = new java.math.BigInteger(1, Arrays.copyOfRange(w, 1, 33));
        assertEquals("r == x(W) bedeutet k == d", x, r);

        System.out.println("Angreifer rechnet aus EINER Signatur den privaten Schluessel aus ...");
        java.math.BigInteger d = NonceReuseAttack.recoverFromSingleSignatureWhenNonceEqualsKey(DOCUMENT, sig);
        byte[] forgedDoc = "Gefaelschte Anweisung".getBytes(StandardCharsets.UTF_8);
        Signature attacker = Signature.getInstance("SHA256withECDSA");
        attacker.initSign(NonceReuseAttack.toPrivateKey(d));
        attacker.update(forgedDoc);
        assertTrue(verifyOffCard(cardPub, forgedDoc, attacker.sign()));
    }

    @Test
    public void regeneratingReplacesTheKey() throws Exception {
        assertEquals(0x9000, verifyPin());
        ECPublicKey oldPub = toPublicKey(generate().getData());
        System.out.println("Neues Schluesselpaar erzeugen: alte Zertifikate passen nicht mehr:");
        ECPublicKey newPub = toPublicKey(generate().getData());
        byte[] sig = sign(DOCUMENT).getData();
        assertTrue(verifyOffCard(newPub, DOCUMENT, sig));
        assertFalse(verifyOffCard(oldPub, DOCUMENT, sig));
    }

    @Test
    public void thereIsNoWayToReadThePrivateKey() {
        assertEquals(0x9000, verifyPin());
        assertEquals(0x9000, generate().getSW());
        System.out.println("Versuch, irgendetwas anderes als den oeffentlichen Schluessel zu lesen:");
        assertEquals(0x6A86, card.sendLe(0x00, 0x46, 0x82, 0x00, 256).getSW());
        assertEquals(0x6D00, card.sendLe(0x00, 0xCA, 0x00, 0x00, 256).getSW());
    }
}
