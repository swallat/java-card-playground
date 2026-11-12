package playground.pqc;

import org.bouncycastle.crypto.SecretWithEncapsulation;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAParameters;
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPublicKeyParameters;
import org.bouncycastle.pqc.crypto.mldsa.MLDSASigner;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMGenerator;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPublicKeyParameters;
import org.junit.Before;
import org.junit.Test;
import playground.support.Card;

import javax.smartcardio.ResponseAPDU;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Variante 2: Post-Quanten-Kryptografie auf der (simulierten) Karte.
 * Im Mittelpunkt steht das Protokoll fuer grosse Daten:
 *   A) Extended Length (ein APDU)
 *   B) nur Short-APDUs (Command Chaining + GET RESPONSE)
 */
public class PqcCardTest {
    private static final String AID = "F0 00 00 00 01 05";
    private static final byte[] DOCUMENT = "Dienstreiseantrag Nr. 4711".getBytes(StandardCharsets.UTF_8);
    private static final int EXTENDED_LE = 65536;   // Extended Le "00 00" = bis 65536 Bytes

    private Card card;

    @Before
    public void setUp() {
        card = new Card(AID, PqcCardApplet.class);
        card.select();
        assertEquals(0x9000, card.send(0x00, 0x20, 0x00, 0x81, "1234".getBytes()).getSW());
    }

    // ================================================================ ML-DSA-65 (FIPS 204)

    @Test
    public void mlDsaExtendedLength() {
        System.out.println("== A) Extended Length: ML-DSA-65 erzeugen und signieren ==");
        int before = card.apduCount;
        byte[] pub = card.sendLe(0x00, 0x46, 0x80, PqcCardApplet.ALG_ML_DSA_65, EXTENDED_LE).getData();
        ResponseAPDU s = card.send(0x00, 0x2A, 0x9E, 0x9A, DOCUMENT, EXTENDED_LE);
        int apdus = card.apduCount - before;

        assertEquals(1952, pub.length);
        assertEquals(0x9000, s.getSW());
        assertEquals(3309, s.getData().length);
        assertTrue(verifyMlDsa(pub, DOCUMENT, s.getData()));
        System.out.println("  APDUs fuer Schluessel + Signatur: " + apdus);
        assertEquals(2, apdus);
    }

    @Test
    public void mlDsaShortApduAnswersWith61xx() {
        System.out.println("== B) Short-APDU: erste 256 Bytes + SW 6100 (\"256 oder mehr folgen\") ==");
        ResponseAPDU r = card.sendLe(0x00, 0x46, 0x80, PqcCardApplet.ALG_ML_DSA_65, 256);
        assertEquals(0x6100, r.getSW());
        assertEquals(256, r.getData().length);
    }

    @Test
    public void mlDsaShortApdusOnly() {
        System.out.println("== B) Nur Short-APDUs: ML-DSA-65 erzeugen und signieren (GET RESPONSE) ==");
        int before = card.apduCount;
        ResponseAPDU gen = card.sendShortApdusOnly(0x00, 0x46, 0x80, PqcCardApplet.ALG_ML_DSA_65, null);
        ResponseAPDU s = card.sendShortApdusOnly(0x00, 0x2A, 0x9E, 0x9A, DOCUMENT);
        int apdus = card.apduCount - before;

        assertEquals(0x9000, gen.getSW());
        assertEquals(1952, gen.getData().length);
        assertEquals(0x9000, s.getSW());
        assertEquals(3309, s.getData().length);
        assertTrue(verifyMlDsa(gen.getData(), DOCUMENT, s.getData()));
        System.out.println("  APDUs fuer Schluessel + Signatur: " + apdus
                + " (1952/256 -> 8, 3309/256 -> 13)");
        assertEquals(8 + 13, apdus);
    }

    @Test
    public void mlDsaTamperedDocumentFails() {
        byte[] pub = card.sendLe(0x00, 0x46, 0x80, PqcCardApplet.ALG_ML_DSA_65, EXTENDED_LE).getData();
        byte[] sig = card.send(0x00, 0x2A, 0x9E, 0x9A, DOCUMENT, EXTENDED_LE).getData();
        byte[] forged = DOCUMENT.clone();
        forged[forged.length - 1] = '2';
        assertFalse(verifyMlDsa(pub, forged, sig));
    }

    // ================================================================ ML-KEM-768 (FIPS 203)

    @Test
    public void mlKemExtendedLength() throws Exception {
        System.out.println("== A) Extended Length: ML-KEM-768 Schluesseleinigung ==");
        byte[] ek = card.sendLe(0x00, 0x46, 0x80, PqcCardApplet.ALG_ML_KEM_768, EXTENDED_LE).getData();
        assertEquals(1184, ek.length);
        SecretWithEncapsulation enc = encapsulate(ek);
        assertEquals(1088, enc.getEncapsulation().length);

        System.out.println("Karte entkapselt, liefert nur eine Schluesselbestaetigung:");
        ResponseAPDU r = card.send(0x00, 0x2A, 0x80, 0x86, enc.getEncapsulation(), EXTENDED_LE);
        assertEquals(0x9000, r.getSW());
        assertArrayEquals(keyConfirmation(enc.getSecret()), r.getData());
    }

    @Test
    public void mlKemShortApdusOnly() throws Exception {
        System.out.println("== B) Nur Short-APDUs: Public Key per GET RESPONSE, Chiffrat per Command Chaining ==");
        ResponseAPDU gen = card.sendShortApdusOnly(0x00, 0x46, 0x80, PqcCardApplet.ALG_ML_KEM_768, null);
        assertEquals(1184, gen.getData().length);
        SecretWithEncapsulation enc = encapsulate(gen.getData());

        int before = card.apduCount;
        ResponseAPDU r = card.sendShortApdusOnly(0x00, 0x2A, 0x80, 0x86, enc.getEncapsulation());
        System.out.println("  APDUs fuer 1088 Bytes Chiffrat: " + (card.apduCount - before) + " (4 x 255 + 68)");
        assertEquals(5, card.apduCount - before);
        assertEquals(0x9000, r.getSW());
        assertArrayEquals(keyConfirmation(enc.getSecret()), r.getData());
    }

    @Test
    public void mlKemTamperedCiphertextGivesWrongSecretWithoutError() throws Exception {
        byte[] ek = card.sendLe(0x00, 0x46, 0x80, PqcCardApplet.ALG_ML_KEM_768, EXTENDED_LE).getData();
        SecretWithEncapsulation enc = encapsulate(ek);
        byte[] ciphertext = enc.getEncapsulation();
        ciphertext[100] ^= 0x01;

        System.out.println("Manipuliertes Chiffrat: KEIN Fehler, aber ein anderes Geheimnis (implicit rejection):");
        ResponseAPDU r = card.send(0x00, 0x2A, 0x80, 0x86, ciphertext, EXTENDED_LE);
        assertEquals(0x9000, r.getSW());
        assertFalse(Arrays.equals(keyConfirmation(enc.getSecret()), r.getData()));
    }

    // ================================================================ Protokoll-Fehlerfaelle

    @Test
    public void interruptedChainIsRejected() {
        card.sendLe(0x00, 0x46, 0x80, PqcCardApplet.ALG_ML_KEM_768, EXTENDED_LE);
        System.out.println("Kette beginnt mit DECIPHER, wird aber mit SIGN fortgesetzt:");
        assertEquals(0x9000, card.send(0x10, 0x2A, 0x80, 0x86, new byte[255]).getSW());
        assertEquals(0x6883, card.send(0x00, 0x2A, 0x9E, 0x9A, DOCUMENT, 256).getSW());
    }

    @Test
    public void getResponseWithoutPendingDataIsRejected() {
        System.out.println("GET RESPONSE, obwohl nichts bereitliegt:");
        assertEquals(0x6985, card.sendLe(0x00, 0xC0, 0x00, 0x00, 256).getSW());
    }

    @Test
    public void pqcOperationsRequirePin() {
        card.reset();
        card.select();
        System.out.println("Nach Reset ohne PIN:");
        assertEquals(0x6982, card.sendLe(0x00, 0x46, 0x80, PqcCardApplet.ALG_ML_DSA_65, EXTENDED_LE).getSW());
    }

    // ================================================================ Host-Seite (off-card)

    private static boolean verifyMlDsa(byte[] publicKey, byte[] data, byte[] signature) {
        MLDSASigner verifier = new MLDSASigner();
        verifier.init(false, new MLDSAPublicKeyParameters(MLDSAParameters.ml_dsa_65, publicKey));
        verifier.update(data, 0, data.length);
        return verifier.verifySignature(signature);
    }

    private static SecretWithEncapsulation encapsulate(byte[] encapsulationKey) {
        System.out.println("Host kapselt ein Geheimnis fuer die Karte (Encapsulation)");
        return new MLKEMGenerator(new SecureRandom()).generateEncapsulated(
                new MLKEMPublicKeyParameters(MLKEMParameters.ml_kem_768, encapsulationKey));
    }

    private static byte[] keyConfirmation(byte[] sharedSecret) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update("KEY-CONFIRM".getBytes(StandardCharsets.US_ASCII));
        sha.update(sharedSecret);
        return sha.digest();
    }
}
