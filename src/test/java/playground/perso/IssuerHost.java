package playground.perso;

import javax.crypto.Cipher;
import javax.smartcardio.CommandAPDU;
import java.util.Arrays;

import static playground.support.Card.hex;

/**
 * Spielt die Autorisierungszentrale des Herausgebers NACH der Kartenausgabe.
 *
 * Sie kennt nur die Masterschluessel KMC_SCRIPT (je Schluesselgeneration einen, im HSM)
 * und die Seriennummer der Karte. Daraus leitet sie K_SCRIPT der Karte ab und baut
 * Issuer Scripts, die z. B. als Anhang an eine Online-Autorisierung zur Karte gelangen.
 */
public class IssuerHost {

    private static final byte[] KMC_SCRIPT_V1 = hex("60 61 62 63 64 65 66 67 68 69 6A 6B 6C 6D 6E 6F");
    private static final byte[] KMC_SCRIPT_V2 = hex("70 71 72 73 74 75 76 77 78 79 7A 7B 7C 7D 7E 7F");

    private final byte[] serial;
    private byte[] kScript;
    private int keyVersion;
    private int counter;

    public IssuerHost(byte[] cardSerial) {
        this.serial = cardSerial.clone();
        this.keyVersion = 1;
        this.kScript = deriveScriptKey(serial, keyVersion);
    }

    /** K_SCRIPT der Karte = Ableitung aus dem Master der jeweiligen Schluesselgeneration. */
    static byte[] deriveScriptKey(byte[] serial, int version) {
        byte[] master = version == 1 ? KMC_SCRIPT_V1 : KMC_SCRIPT_V2;
        return DataPreparation.deriveCardKey(master, serial);
    }

    /** 84 24 00 81: neue PIN setzen. Setzt nebenbei den Fehlbedienungszaehler zurueck (Entsperren). */
    public CommandAPDU changePin(String newPin) {
        return script(0x24, 0x00, 0x81, DataPreparation.pinBlock(newPin));
    }

    /**
     * 84 D8 <neue Version> 00: K_SCRIPT durch die naechste Schluesselgeneration ersetzen.
     * Das Kommando ist noch mit dem ALTEN Schluessel geschuetzt, danach gilt der neue.
     * (Vereinfachung: Der Host stellt sofort um. Real erst nach bestaetigter Ausfuehrung.)
     */
    public CommandAPDU putKey(int newVersion) {
        byte[] newKey = deriveScriptKey(serial, newVersion);
        CommandAPDU c = script(0xD8, newVersion, 0x00, newKey);
        kScript = newKey;
        keyVersion = newVersion;
        return c;
    }

    /** Fuer Tests: Zaehlerstand gezielt setzen (z. B. veralteter Host). */
    void setCounter(int value) {
        counter = value;
    }

    /** Baut 84 INS P1 P2 Lc [Zaehler || ENC_S_ENC(geheim) || MAC_S_MAC(Header || Zaehler || Kryptogramm)] */
    private CommandAPDU script(int ins, int p1, int p2, byte[] secret16) {
        counter++;
        byte[] ctr = {(byte) (counter >> 8), (byte) counter};

        byte[] sMac = sessionKey(ctr, (byte) 0x01);
        byte[] sEnc = sessionKey(ctr, (byte) 0x02);

        byte[] cryptogram = DataPreparation.aes("AES/CBC/NoPadding", Cipher.ENCRYPT_MODE, sEnc, new byte[16], secret16);

        byte[] macInput = new byte[4 + 2 + 16];
        macInput[0] = (byte) 0x84;
        macInput[1] = (byte) ins;
        macInput[2] = (byte) p1;
        macInput[3] = (byte) p2;
        System.arraycopy(ctr, 0, macInput, 4, 2);
        System.arraycopy(cryptogram, 0, macInput, 6, 16);
        byte[] mac = DataPreparation.cbcMac(sMac, macInput);

        byte[] data = new byte[2 + 16 + 16];
        System.arraycopy(ctr, 0, data, 0, 2);
        System.arraycopy(cryptogram, 0, data, 2, 16);
        System.arraycopy(mac, 0, data, 18, 16);
        return new CommandAPDU(0x84, ins, p1, p2, data);
    }

    /** Sitzungsschluessel = AES-ECB_K_SCRIPT(Zaehler || Konstante || 00..00), wie auf der Karte. */
    private byte[] sessionKey(byte[] ctr, byte constant) {
        byte[] block = Arrays.copyOf(ctr, 16);
        block[2] = constant;
        return DataPreparation.aes("AES/ECB/NoPadding", Cipher.ENCRYPT_MODE, kScript, null, block);
    }
}
