package playground.aufgaben.uebung05;

import java.util.HashSet;
import java.util.Set;

/**
 * Aufgabe 4: Eine Mini-CA, die bei der Zertifikatsausstellung doppelte oeffentliche Schluessel ablehnt.
 * Echte Trust Center pruefen eingereichte Schluessel z. B. auf Duplikate und bekannte Schwaechen (ROCA).
 */
public class MiniCa {

    private final Set<String> bekannteSchluessel = new HashSet<String>();

    /**
     * @param oeffentlicherSchluessel 65 Bytes 04 || X || Y
     * @return true = Zertifikat wird ausgestellt, false = abgelehnt (Duplikat)
     */
    public boolean einreichen(byte[] oeffentlicherSchluessel) {
        // >>> AUFGABE 4: Schluessel merken (z. B. als Hex-String in bekannteSchluessel) und
        //     false liefern, wenn er schon einmal eingereicht wurde. Hilfe: Set.add(...) liefert
        //     false, wenn das Element schon enthalten war.
        return true;
        // <<< AUFGABE 4
    }
}
