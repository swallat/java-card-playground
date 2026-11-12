package playground.aufgaben.uebung06;

import org.bouncycastle.crypto.SecretWithEncapsulation;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMGenerator;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters;
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPublicKeyParameters;
import org.junit.Before;
import org.junit.Test;
import playground.aufgaben.Pruefe;
import playground.pqc.PqcCardApplet;
import playground.support.Card;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Testbed zum Aufgabenzettel Uebung 3, Variante 2 - PQC (aufgaben/uebung-06-pqc.md).
 *
 * Eingabestellen: ">>> AUFGABE" in dieser Datei.
 * Ausfuehren:     mise exec -- mvn test -Dtest=Uebung06Aufgaben
 */
public class Uebung06Aufgaben {

    private static final String AID = "F0 00 00 00 01 16";
    private static final byte[] DOCUMENT = "Dienstreiseantrag Nr. 4711".getBytes(StandardCharsets.UTF_8);

    // >>> AUFGABE 1: Extended-APDU 00 2A 80 86 00 04 40 <1088 Bytes> 00 00 zerlegen
    static final int A1_POSITION_MARKER = 0;      // Index (ab 0) des Extended-Length-Markers im APDU
    static final int A1_LC_DEZIMAL = 0;           // Wert von Lc
    static final int A1_ANZAHL_LC_BYTES = 0;      // wie viele Bytes Lc hat (ohne Marker)
    // <<< AUFGABE 1

    // >>> AUFGABE 2: GET RESPONSE beim Abholen der ML-DSA-65-Signatur (3309 Bytes)
    static final int A2_ANZAHL_GET_RESPONSE = 0;  // wie viele GET RESPONSE nach dem SIGN-Kommando?
    static final int A2_REST_IM_LETZTEN = 0;      // xx im letzten 61xx (dezimal)
    // <<< AUFGABE 2

    // >>> AUFGABE 3: ML-DSA-87 nur mit Short-APDUs: APDUs fuer Schluessel + Signatur zusammen
    static final int A3_VORHERSAGE_APDUS = 0;
    // <<< AUFGABE 3

    // >>> AUFGABE 4: Teilgroesse fuer Command Chaining und Vorhersage der APDUs fuer 1088 Bytes Chiffrat
    static final int A4_TEILGROESSE = 128;
    static final int A4_VORHERSAGE_APDUS = 0;
    // <<< AUFGABE 4

    // >>> AUFGABE 5: Vorhersage fuer ein manipuliertes ML-KEM-Chiffrat
    static final int A5_VORHERSAGE_SW = 0x0000;
    static final Boolean A5_VORHERSAGE_GLEICHES_GEHEIMNIS = null;   // true oder false
    // <<< AUFGABE 5

    private Card card;

    @Before
    public void setUp() {
        card = new Card(AID, PqcCardApplet.class);
        card.select();
        assertEquals(0x9000, card.send(0x00, 0x20, 0x00, 0x81, "1234".getBytes()).getSW());
    }

    @Test
    public void aufgabe1_extendedApduZerlegen() {
        CommandAPDU c = new CommandAPDU(0x00, 0x2A, 0x80, 0x86, new byte[1088], 65536);
        byte[] b = c.getBytes();
        int markerIndex = 4;                      // direkt nach CLA INS P1 P2
        assertEquals(0x00, b[markerIndex]);
        Pruefe.wert("Aufgabe 1", "Position des Markers", A1_POSITION_MARKER, markerIndex);
        Pruefe.wert("Aufgabe 1", "Lc", A1_LC_DEZIMAL, ((b[5] & 0xFF) << 8) | (b[6] & 0xFF));
        Pruefe.wert("Aufgabe 1", "Anzahl Lc-Bytes", A1_ANZAHL_LC_BYTES, 2);
    }

    @Test
    public void aufgabe2_getResponseVerfolgen() {
        card.sendLe(0x00, 0x46, 0x80, PqcCardApplet.ALG_ML_DSA_65, 65536);
        int before = card.apduCount;
        ResponseAPDU r = card.sendShortApdusOnly(0x00, 0x2A, 0x9E, 0x9A, DOCUMENT);
        int getResponses = card.apduCount - before - 1;
        assertEquals(3309, r.getData().length);
        Pruefe.anzahl("Aufgabe 2 (Anzahl GET RESPONSE)", A2_ANZAHL_GET_RESPONSE, getResponses);
        Pruefe.anzahl("Aufgabe 2 (Rest im letzten 61xx)", A2_REST_IM_LETZTEN, 3309 % 256);
    }

    @Test
    public void aufgabe3_mlDsa87() {
        int before = card.apduCount;
        ResponseAPDU gen = card.sendShortApdusOnly(0x00, 0x46, 0x80, PqcCardApplet.ALG_ML_DSA_87, null);
        ResponseAPDU sig = card.sendShortApdusOnly(0x00, 0x2A, 0x9E, 0x9A, DOCUMENT);
        System.out.printf("  Aufgabe 3: ML-DSA-87 Public Key %d Bytes, Signatur %d Bytes%n",
                gen.getData().length, sig.getData().length);
        Pruefe.anzahl("Aufgabe 3", A3_VORHERSAGE_APDUS, card.apduCount - before);
    }

    @Test
    public void aufgabe4_kleinereKettenglieder() throws Exception {
        byte[] ek = card.sendLe(0x00, 0x46, 0x80, PqcCardApplet.ALG_ML_KEM_768, 65536).getData();
        SecretWithEncapsulation enc = encapsulate(ek);
        int before = card.apduCount;
        ResponseAPDU r = card.sendShortApdusOnly(0x00, 0x2A, 0x80, 0x86, enc.getEncapsulation(), A4_TEILGROESSE);
        assertEquals(0x9000, r.getSW());
        assertArrayEqualsMsg("Schluesselbestaetigung", keyConfirmation(enc.getSecret()), r.getData());
        Pruefe.anzahl("Aufgabe 4", A4_VORHERSAGE_APDUS, card.apduCount - before);
    }

    @Test
    public void aufgabe5_implicitRejection() throws Exception {
        if (A5_VORHERSAGE_GLEICHES_GEHEIMNIS == null) {
            fail("Aufgabe 5: A5_VORHERSAGE_GLEICHES_GEHEIMNIS eintragen.");
        }
        byte[] ek = card.sendLe(0x00, 0x46, 0x80, PqcCardApplet.ALG_ML_KEM_768, 65536).getData();
        SecretWithEncapsulation enc = encapsulate(ek);
        byte[] ct = enc.getEncapsulation();
        ct[100] ^= 0x01;
        ResponseAPDU r = card.send(0x00, 0x2A, 0x80, 0x86, ct, 65536);
        Pruefe.vorhersage("Aufgabe 5 (Statuswort)", A5_VORHERSAGE_SW, r.getSW());
        boolean gleich = Arrays.equals(keyConfirmation(enc.getSecret()), r.getData());
        assertEquals("Aufgabe 5: Vorhersage 'gleiches Geheimnis'", A5_VORHERSAGE_GLEICHES_GEHEIMNIS.booleanValue(), gleich);
    }

    // ------------------------------------------------------------------ Hilfen (nicht aendern)

    private static SecretWithEncapsulation encapsulate(byte[] ek) {
        return new MLKEMGenerator(new SecureRandom()).generateEncapsulated(
                new MLKEMPublicKeyParameters(MLKEMParameters.ml_kem_768, ek));
    }

    private static byte[] keyConfirmation(byte[] ss) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update("KEY-CONFIRM".getBytes(StandardCharsets.US_ASCII));
        sha.update(ss);
        return sha.digest();
    }

    private static void assertArrayEqualsMsg(String msg, byte[] a, byte[] b) {
        org.junit.Assert.assertArrayEquals(msg, a, b);
    }
}
