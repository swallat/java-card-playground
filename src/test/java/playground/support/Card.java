package playground.support;

import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import javacard.framework.AID;
import javacard.framework.Applet;

import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;

/** Duenner Wrapper um den Simulator, der jede APDU als Hex mitprotokolliert. */
public class Card {
    public final CardSimulator sim = new CardSimulator();
    private final AID aid;
    private final byte[] aidBytes;

    public Card(String aidHex, Class<? extends Applet> clazz) {
        this(aidHex, clazz, new byte[0]);
    }

    /**
     * Installiert das Applet mit Install-Parametern im GlobalPlatform-Format, so wie
     * INSTALL [for install] sie an Applet.install() uebergibt:
     *   [Laenge][Instanz-AID] [Laenge][Privileges] [Laenge][Applet-spezifische Parameter]
     */
    public Card(String aidHex, Class<? extends Applet> clazz, byte[] appletParams) {
        this.aid = AIDUtil.create(aidHex);
        this.aidBytes = hex(aidHex);
        byte[] privileges = {0x00};
        byte[] params = new byte[3 + aidBytes.length + privileges.length + appletParams.length];
        int o = 0;
        params[o++] = (byte) aidBytes.length;
        System.arraycopy(aidBytes, 0, params, o, aidBytes.length); o += aidBytes.length;
        params[o++] = (byte) privileges.length;
        System.arraycopy(privileges, 0, params, o, privileges.length); o += privileges.length;
        params[o++] = (byte) appletParams.length;
        System.arraycopy(appletParams, 0, params, o, appletParams.length);
        sim.installApplet(aid, clazz, params, (short) 0, (byte) params.length);
    }

    /** Simuliert "Karte raus und wieder rein": RAM/transiente Daten weg, EEPROM bleibt. */
    public void reset() {
        System.out.println("  --- RESET (Karte aus dem Feld gezogen) ---");
        sim.reset();
    }

    public ResponseAPDU select() {
        // 00 A4 04 00 Lc <AID> : SELECT by name
        return send(new CommandAPDU(0x00, 0xA4, 0x04, 0x00, aidBytes));
    }

    public ResponseAPDU send(CommandAPDU c) {
        System.out.println("  --> " + hex(c.getBytes()));
        ResponseAPDU r = sim.transmitCommand(c);
        System.out.println("  <-- " + (r.getData().length > 0 ? hex(r.getData()) + " " : "")
                + String.format("SW=%04X", r.getSW()));
        return r;
    }

    /** Case 1: kein Lc/Data, kein Le (z. B. VERIFY-Status, reine Steuerbefehle). */
    public ResponseAPDU send(int cla, int ins, int p1, int p2) {
        return send(new CommandAPDU(cla, ins, p1, p2));
    }

    /** Case 3: Lc + Data, keine Antwortdaten erwartet (z. B. VERIFY mit PIN). */
    public ResponseAPDU send(int cla, int ins, int p1, int p2, byte[] data) {
        return send(new CommandAPDU(cla, ins, p1, p2, data));
    }

    /** Case 2: nur Le, Antwortdaten erwartet. ne=256 wird als Le=00 kodiert ("bis zu 256 Bytes"). */
    public ResponseAPDU sendLe(int cla, int ins, int p1, int p2, int ne) {
        return send(new CommandAPDU(cla, ins, p1, p2, ne));
    }

    /** Case 4: Lc + Data und Le. */
    public ResponseAPDU send(int cla, int ins, int p1, int p2, byte[] data, int ne) {
        return send(new CommandAPDU(cla, ins, p1, p2, data, ne));
    }

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X ", x));
        return sb.toString().trim();
    }

    public static byte[] hex(String s) {
        s = s.replaceAll("\\s", "");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return out;
    }
}
