# Quintessenz aller Übungen

Die Kernaussagen je Übung, zum schnellen Wiederholen. Details stehen in den
verlinkten Dokumenten, Aufgaben unter [`aufgaben/`](../aufgaben/).

---

## Übung 1 – APDU-Grundlagen → [01-apdu-grundlagen.md](01-apdu-grundlagen.md)

1. **Die Karte ist passiv.** Sie beantwortet nur Kommandos, genau eine Antwort pro Kommando.
2. **Eine APDU ist ein Funktionsaufruf:** CLA (Namensraum), INS (Funktion), P1/P2
   (Parameter), Lc + Data (Argument), Le (erwartete Antwortlänge). Die Antwort besteht aus
   Daten und einem Statuswort (`9000` = OK).
3. **Die vier Cases** ergeben sich aus der Länge nach dem Header. Le ist ein Maximum, `00` = 256.
   Wer Antwortdaten will, muss Le schicken.
4. **Die AID (RID + PIX) ist ein ISO-Begriff** (7816-5) und gilt für alle Karten, auch für
   EMV/girocard. Ein Applet wird per SELECT über die AID ausgewählt.
5. **Ein Applet ist ein `switch` über INS**, Fehler werden per `ISOException` gemeldet.
   Kommando und Antwort teilen sich einen APDU-Buffer.
6. **Objekte sind persistent**, die VM pausiert nur. `install()` legt alles an, `process()`
   legt nichts an.

> Eine Smartcard ist ein passiver Kommando-Empfänger. Jede Funktion ist eine APDU, die Karte
> antwortet mit Daten und Statuswort. Ein Applet wird per AID selektiert.

---

## Übung 2 – PIN-Prüfung → [02-pin-pruefung.md](02-pin-pruefung.md)

1. **Die PIN wird auf der Karte geprüft**, die Referenz-PIN verlässt die Karte nie.
2. **Der Fehlbedienungszähler ist persistent und wird vor dem Vergleich dekrementiert**
   (gegen Tearing). Nach dem letzten Fehlversuch gilt `6983`, auch für die richtige PIN.
3. **Die Freischaltung ist transient:** Reset oder Deselect heben sie auf.
4. **Bei Java Card implementiert das Applet ISO-Kommandos wie VERIFY selbst.** Native
   Betriebssysteme (CardOS, STARCOS) bringen sie mit.
5. **Die Spezifikation regelt das Verhalten, der Schutz kommt vom Chip:** Secure Controller
   mit verschlüsseltem Speicher, Sensoren, Seitenkanalschutz. Die Karte ist
   *tamper-resistant*, ein HSM *tamper-responsive*.

> Die PIN geht per VERIFY an die Karte und wird dort geprüft. Der Zähler ist persistent und
> wird vor dem Vergleich dekrementiert, die Freischaltung ist transient.

---

## Zwischenübung – Personalisierung → [03-personalisierung.md](03-personalisierung.md)

1. **Der Code ist schon auf dem Chip. Verschlüsselt transportiert werden die Daten**
   (PIN, Schlüssel, Inhaberdaten).
2. **Key Diversification:** Kartenschlüssel = f(Master im HSM, Seriennummer). Daten passen
   nur auf genau eine Karte, eine geknackte Karte gefährdet keine andere.
3. **Erst MAC prüfen, dann entschlüsseln.** Geheimes wird verschlüsselt, alles wird gemact.
   Der Tag gehört in den MAC.
4. **Klartext nur kurz im RAM**, danach den Puffer komplett nullen.
5. **Lebenszyklus als Einbahnstraße:** Der Abschluss schaltet den Status um und löscht die
   Personalisierungsschlüssel. Das sind zwei unabhängige Sperren.
6. **Ohne Secure Channel (SCP03)** ist jedes APDU wiederholbar (Replay). Echte Karten nutzen
   Sitzungsschlüssel aus Challenges.

> Personalisierungsdaten gehen verschlüsselt und integritätsgeschützt unter einem
> kartenindividuellen Schlüssel auf die Karte. Danach wechselt der Lebenszyklus, und die
> Personalisierungsschlüssel werden gelöscht oder gewechselt.

---

## Zwischenübung – Issuer Scripts (K_SCRIPT) → [04-issuer-scripts.md](04-issuer-scripts.md)

1. **Wer nach der Ausgabe verwalten will, braucht einen Schlüssel, der die Personalisierung
   überlebt.** Hier ist das K_SCRIPT, bei EMV die Secure-Messaging-Schlüssel, bei GP der
   operative Schlüsselsatz der Security Domain.
2. **Sitzungsschlüssel aus einem Zähler:** Jedes Kommando hat eigene Schlüssel. Ein höherer
   Zähler ist Pflicht, das ist der Replay-Schutz.
3. **Der Header gehört in den MAC**, sonst lässt sich ein Kommando umetikettieren
   (aus PIN-Setzen wird PUT KEY).
4. **Schlüsselgenerationen:** PUT KEY bringt den neuen Schlüssel verschlüsselt unter dem
   alten. Ein Rückfall auf eine alte Version ist ausgeschlossen.
5. **Drei Schlüsselebenen:** Master (HSM), kartenindividuell (persistent), Sitzung (transient).

> Nach der Ausgabe verwaltet der Herausgeber die Karte über einen kartenindividuellen
> Verwaltungsschlüssel. Jedes Kommando ist mit zählerabhängigen Sitzungsschlüsseln geschützt:
> das Prinzip der EMV Issuer Scripts bzw. von GP Secure Channel.

---

## Übung 3 – On-Card-Schlüssel und Signatur → [05-schluessel-und-signatur.md](05-schluessel-und-signatur.md)

1. **Die Karte ist ein Schlüssel-Tresor mit Rechenwerk.** Das Schlüsselpaar entsteht auf der
   Karte, nur der öffentliche Schlüssel geht an die CA, kein Kommando gibt den privaten
   Schlüssel aus.
2. **Die PIN regelt, *wer* signiert, nicht *was*.** In der Praxis bekommt die Karte nur einen
   Hash und sieht das Dokument nie (WYSIWYS). Deshalb braucht es vertrauenswürdige
   Signaturanwendungen, PIN-Pad-Leser und die PIN pro Signatur bei der QES.
3. **„Der Schlüssel verlässt die Karte nie“ ist notwendig, aber nicht hinreichend.**
   Im Simulator verriet eine einzige ECDSA-Signatur den Schlüssel, weil die Nonce aus einem
   kaputten Zufallsgenerator kam (vgl. PS3, ROCA).
4. **Das ist eine Architekturfrage:** zertifizierte RNGs (AIS 20/31), RFC-6979-ECDSA und im
   **Trust Center Schlüsselprüfungen bei der Zertifikatsausstellung** (schwache Schlüssel,
   Duplikate).
5. **Bei Verschlüsselungsschlüsseln** ist zentrale Erzeugung mit Backup oft gewollt, bei
   Signatur- und Authentisierungsschlüsseln nicht.

> On-Card-Erzeugung sorgt dafür, dass der private Schlüssel die Karte nie verlässt. Ob er
> geheim bleibt, hängt an der Implementierung. Deshalb gehören zertifizierte RNGs und
> Schlüsselprüfungen bei der Zertifikatsausstellung zusammen.

---

## Übung 3, Variante 2 – PQC (ML-DSA, ML-KEM) → [06-pqc-ml-dsa-ml-kem.md](06-pqc-ml-dsa-ml-kem.md)

1. **Gleiches Prinzip, andere Größen:** ML-DSA-65 hat 1952 Bytes Public Key und 3309 Bytes
   Signatur (P-256: 65 / ca. 72).
2. **Das Protokoll wird zur Architekturentscheidung:** Extended Length (2 APDUs) oder
   Chaining + GET RESPONSE (21 APDUs). Jedes Glied der Kette (Karte, Leser, Middleware) muss
   die gewählte Variante können.
3. **ML-KEM kapselt, statt zu verschlüsseln.** Bei Manipulation gibt es keinen Fehler,
   sondern ein falsches Geheimnis (implicit rejection), deshalb ist eine
   Schlüsselbestätigung nötig.
4. **Keine Standard-API in Java Card 3.0.5.** Hersteller bauen PQC in den OS-Kern ein.
   Krypto-Agilität wird zum Auswahlkriterium der Plattform.
5. **Prioritäten:** Schlüsseleinigung vor Signatur („Harvest now, decrypt later“),
   in der Übergangszeit hybrid, symmetrisch AES-256.
6. **Zustandslogik ist fehleranfällig:** Die Tests fanden eine falsch behandelte
   unterbrochene Kette (`6985` statt `6883`).

> PQC ändert nicht das Prinzip, aber alles darunter: Kilobyte-Daten erzwingen Extended
> Length oder Chaining, mehr Speicher und größere Zertifikate, und die ganze Kette muss
> mitziehen.

---

## Querschnitt: Grenzen des Simulators

- bildet Logik ab, keine physische Sicherheit
- kein CAP, kein GP Card Manager, kein echter SCP03
- **Zufall kaputt:** gleicher erster Schlüssel auf jeder „Karte“, Nonce = privater Schlüssel
- PQC nur als Protokoll-Simulation mit externer Bibliothek
