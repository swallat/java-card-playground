package playground.pqc;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.OwnerPIN;
import javacardx.apdu.ExtendedLength;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.MLDSAKeyPairGenerator;
import org.bouncycastle.crypto.generators.MLKEMKeyPairGenerator;
import org.bouncycastle.crypto.kems.MLKEMExtractor;
import org.bouncycastle.crypto.params.MLDSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.MLDSAParameters;
import org.bouncycastle.crypto.params.MLDSAPrivateKeyParameters;
import org.bouncycastle.crypto.params.MLDSAPublicKeyParameters;
import org.bouncycastle.crypto.params.MLKEMKeyGenerationParameters;
import org.bouncycastle.crypto.params.MLKEMParameters;
import org.bouncycastle.crypto.params.MLKEMPrivateKeyParameters;
import org.bouncycastle.crypto.params.MLKEMPublicKeyParameters;
import org.bouncycastle.crypto.signers.MLDSASigner;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * PQC-PROTOKOLL-SIMULATION - KEIN echter Java-Card-Code!
 *
 * Java Card 3.0.5 hat keine API fuer ML-DSA/ML-KEM. Echte PQC-Karten implementieren die
 * Verfahren im Betriebssystem-Kern und bieten sie ueber herstellerspezifische Schnittstellen an.
 * Die Krypto kommt hier deshalb aus BouncyCastle (im Simulator moeglich, weil das Applet als
 * normales Java laeuft). Der Code liegt bewusst unter src/test: nicht CAP-konvertierbar.
 *
 * Demonstriert wird das PROTOKOLL fuer grosse Daten, in beiden Varianten nach ISO 7816-4:
 *   A) Extended Length: ein APDU, Lc/Le mit 2 Bytes (Interface ExtendedLength)
 *   B) Nur Short-APDUs: Eingabe per Command Chaining (CLA-Bit 0x10),
 *      Ausgabe per Response Chaining (SW 61xx + GET RESPONSE 00 C0 00 00 Le)
 *
 * Kommandos:
 *   00 20 00 81 Lc <PIN>            VERIFY
 *   00 46 80 01 | 00 46 80 02       GENERATE: 01 = ML-DSA-65, 02 = ML-KEM-768 -> oeffentlicher Schluessel
 *   00 2A 9E 9A <Daten>             PSO: COMPUTE DIGITAL SIGNATURE (ML-DSA-65)
 *   00 2A 80 86 <ML-KEM-Chiffrat>   PSO: DECIPHER (Decapsulation) -> nur Schluesselbestaetigung
 *   00 C0 00 00 Le                  GET RESPONSE (naechster Teil einer langen Antwort)
 */
public class PqcCardApplet extends Applet implements ExtendedLength {

    public static final byte ALG_ML_DSA_65 = 0x01;
    public static final byte ALG_ML_KEM_768 = 0x02;
    public static final byte ALG_ML_DSA_87 = 0x03;   // fuer den Aufgabenzettel PQC, Aufgabe 3
    private static final int ML_KEM_768_CIPHERTEXT_LEN = 1088;  // FIPS 203

    private static final byte CLA_CHAINING_BIT = 0x10;
    private static final byte INS_GET_RESPONSE = (byte) 0xC0;
    private static final short SW_LAST_COMMAND_OF_CHAIN_EXPECTED = 0x6883;  // ISO 7816-4

    private final OwnerPIN pin;
    private final SecureRandom rng = new SecureRandom();  // im Simulator: echter Java-Zufall

    private MLDSAPrivateKeyParameters dsaPrivate;   // verlaesst die "Karte" nie
    private MLKEMPrivateKeyParameters kemPrivate;   // verlaesst die "Karte" nie

    // Command Chaining: gesammelte Eingabe-Teile
    private final byte[] chainBuffer = new byte[8192];
    private int chainLen;
    private int chainHeader;                         // INS/P1/P2 der laufenden Kette

    // Response Chaining: noch nicht abgeholter Rest einer langen Antwort
    private byte[] pending;
    private int pendingPos;

    private PqcCardApplet() {
        pin = new OwnerPIN((byte) 3, (byte) 8);
        byte[] initial = {'1', '2', '3', '4'};
        pin.update(initial, (short) 0, (byte) initial.length);
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new PqcCardApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void deselect() {
        pin.reset();
        chainLen = 0;
        pending = null;
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        byte ins = buf[ISO7816.OFFSET_INS];

        if (ins == INS_GET_RESPONSE) {
            getResponse(apdu);
            return;
        }
        pending = null;   // jedes andere Kommando verwirft eine nicht abgeholte Antwort

        // Laeuft eine Kette, darf nur deren Fortsetzung kommen - vor allen anderen Pruefungen.
        int header = ((ins & 0xFF) << 16)
                | ((buf[ISO7816.OFFSET_P1] & 0xFF) << 8) | (buf[ISO7816.OFFSET_P2] & 0xFF);
        if (chainLen > 0 && header != chainHeader) {
            chainLen = 0;
            ISOException.throwIt(SW_LAST_COMMAND_OF_CHAIN_EXPECTED);  // 6883
        }

        short p1p2 = (short) (((buf[ISO7816.OFFSET_P1] & 0xFF) << 8) | (buf[ISO7816.OFFSET_P2] & 0xFF));
        if (ins == 0x20) {
            byte[] data = receiveAll(apdu);
            if (!pin.check(data, (short) 0, (byte) data.length)) {
                ISOException.throwIt((short) (0x63C0 | pin.getTriesRemaining()));
            }
            return;
        }
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        if (ins == 0x46 && buf[ISO7816.OFFSET_P1] == (byte) 0x80) {
            generate(apdu, buf[ISO7816.OFFSET_P2]);
        } else if (ins == 0x2A && p1p2 == (short) 0x9E9A) {
            sign(apdu);
        } else if (ins == 0x2A && p1p2 == (short) 0x8086) {
            decapsulate(apdu);
        } else {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void generate(APDU apdu, byte alg) {
        if (alg == ALG_ML_DSA_65) {
            MLDSAKeyPairGenerator g = new MLDSAKeyPairGenerator();
            g.init(new MLDSAKeyGenerationParameters(rng, MLDSAParameters.ml_dsa_65));
            AsymmetricCipherKeyPair kp = g.generateKeyPair();
            dsaPrivate = (MLDSAPrivateKeyParameters) kp.getPrivate();
            sendResponse(apdu, ((MLDSAPublicKeyParameters) kp.getPublic()).getEncoded());
        } else if (alg == ALG_ML_DSA_87) {
            MLDSAKeyPairGenerator g = new MLDSAKeyPairGenerator();
            g.init(new MLDSAKeyGenerationParameters(rng, MLDSAParameters.ml_dsa_87));
            AsymmetricCipherKeyPair kp = g.generateKeyPair();
            dsaPrivate = (MLDSAPrivateKeyParameters) kp.getPrivate();
            sendResponse(apdu, ((MLDSAPublicKeyParameters) kp.getPublic()).getEncoded());
        } else if (alg == ALG_ML_KEM_768) {
            MLKEMKeyPairGenerator g = new MLKEMKeyPairGenerator();
            g.init(new MLKEMKeyGenerationParameters(rng, MLKEMParameters.ml_kem_768));
            AsymmetricCipherKeyPair kp = g.generateKeyPair();
            kemPrivate = (MLKEMPrivateKeyParameters) kp.getPrivate();
            sendResponse(apdu, ((MLKEMPublicKeyParameters) kp.getPublic()).getEncoded());
        } else {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
    }

    private void sign(APDU apdu) {
        if (dsaPrivate == null) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        byte[] message = readInput(apdu);
        if (message == null) {
            return;   // Teil einer Kette angenommen (9000), auf den Rest warten
        }
        MLDSASigner signer = new MLDSASigner();
        signer.init(true, dsaPrivate);
        signer.update(message, 0, message.length);
        try {
            sendResponse(apdu, signer.generateSignature());
        } catch (org.bouncycastle.crypto.CryptoException e) {
            ISOException.throwIt(ISO7816.SW_UNKNOWN);
        }
    }

    /**
     * ML-KEM Decapsulation. Das gemeinsame Geheimnis bleibt auf der Karte; zurueck geht nur
     * eine Schluesselbestaetigung SHA-256("KEY-CONFIRM" || ss).
     * ML-KEM meldet bei manipuliertem Chiffrat KEINEN Fehler ("implicit rejection"),
     * sondern liefert ein zufaellig wirkendes, falsches Geheimnis.
     */
    private void decapsulate(APDU apdu) {
        if (kemPrivate == null) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        byte[] ciphertext = readInput(apdu);
        if (ciphertext == null) {
            return;
        }
        if (ciphertext.length != ML_KEM_768_CIPHERTEXT_LEN) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        byte[] sharedSecret = new MLKEMExtractor(kemPrivate).extractSecret(ciphertext);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update("KEY-CONFIRM".getBytes("US-ASCII"));
            sha.update(sharedSecret);
            sendResponse(apdu, sha.digest());
        } catch (java.io.UnsupportedEncodingException | java.security.NoSuchAlgorithmException e) {
            ISOException.throwIt(ISO7816.SW_UNKNOWN);
        } finally {
            Arrays.fill(sharedSecret, (byte) 0);
        }
    }

    // ------------------------------------------------------------------ Eingabe: Command Chaining

    /**
     * Liefert die vollstaendige Eingabe oder null, wenn das APDU nur ein Teil einer Kette war
     * (CLA-Bit 0x10 gesetzt). Alle Teile einer Kette muessen dasselbe INS/P1/P2 haben.
     */
    private byte[] readInput(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        boolean moreToCome = (buf[ISO7816.OFFSET_CLA] & CLA_CHAINING_BIT) != 0;
        int header = ((buf[ISO7816.OFFSET_INS] & 0xFF) << 16)
                | ((buf[ISO7816.OFFSET_P1] & 0xFF) << 8) | (buf[ISO7816.OFFSET_P2] & 0xFF);
        byte[] part = receiveAll(apdu);

        if (chainLen > 0 && header != chainHeader) {
            chainLen = 0;
            ISOException.throwIt(SW_LAST_COMMAND_OF_CHAIN_EXPECTED);  // Kette unterbrochen
        }
        if (chainLen + part.length > chainBuffer.length) {
            chainLen = 0;
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        System.arraycopy(part, 0, chainBuffer, chainLen, part.length);
        chainLen += part.length;
        chainHeader = header;

        if (moreToCome) {
            return null;
        }
        byte[] all = Arrays.copyOf(chainBuffer, chainLen);
        chainLen = 0;
        return all;
    }

    /** Alle Datenbytes eines APDUs empfangen, auch wenn sie nicht in einen Puffer-Durchgang passen. */
    private static byte[] receiveAll(APDU apdu) {
        short read = apdu.setIncomingAndReceive();
        int total = apdu.getIncomingLength();
        byte[] out = new byte[total];
        int pos = 0;
        short off = apdu.getOffsetCdata();
        while (read > 0) {
            System.arraycopy(apdu.getBuffer(), off, out, pos, read);
            pos += read;
            read = apdu.receiveBytes((short) 0);
            off = 0;
        }
        return out;
    }

    // ------------------------------------------------------------------ Ausgabe: Response Chaining

    /**
     * Antwort senden. Reicht das Le des Hosts (Extended Length), geht alles in einem APDU.
     * Sonst (Short-APDU, Le max. 256): erster Teil + SW 61xx, Rest per GET RESPONSE.
     */
    private void sendResponse(APDU apdu, byte[] data) {
        short le = apdu.setOutgoing();
        int max = (le == 0) ? 65536 : (le & 0xFFFF);   // Extended Le "00 00" = 65536
        pending = data;
        pendingPos = 0;
        sendChunk(apdu, max);
    }

    private void getResponse(APDU apdu) {
        if (pending == null) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        short le = apdu.setOutgoing();
        sendChunk(apdu, (le == 0) ? 256 : (le & 0xFFFF));
    }

    private void sendChunk(APDU apdu, int max) {
        int n = Math.min(max, pending.length - pendingPos);
        apdu.setOutgoingLength((short) n);
        apdu.sendBytesLong(pending, (short) pendingPos, (short) n);
        pendingPos += n;
        int rest = pending.length - pendingPos;
        if (rest > 0) {
            // 61xx: "es liegen noch xx Bytes bereit", 6100 = 256 oder mehr
            ISOException.throwIt((short) (0x6100 | (rest >= 256 ? 0 : rest)));
        }
        pending = null;
    }
}
