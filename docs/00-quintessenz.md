# Quintessenz aller Übungen

Was man aus jeder Übung mitnimmt, jeweils mit Verweis auf die Aufgabe, in der man es selbst
beobachtet oder umsetzt. Details stehen in den verlinkten Dokumenten, die Aufgabenzettel unter
[`aufgaben/`](../aufgaben/), die Musterlösungen unter `playground.loesungen`.

---

## Übung 1 – APDU-Grundlagen

Doku: [01-apdu-grundlagen.md](01-apdu-grundlagen.md) · Aufgaben: [uebung-01-apdu.md](../aufgaben/uebung-01-apdu.md)

1. **Die Karte ist passiv:** genau eine Antwort pro Kommando, nie von sich aus.
2. **Eine APDU ist ein Funktionsaufruf:** CLA, INS, P1/P2, Lc + Data, Le. Die Antwort besteht
   aus Daten und Statuswort. *Aufgabe 1* zerlegt SELECT, HELLO und ECHO Byte für Byte.
3. **Der Case ergibt sich aus der Länge nach dem Header.** Le ist ein Maximum, `00` = 256.
   *Aufgabe 1* bestimmt die Cases, *Aufgabe 4* zeigt, was bei zu kleinem Le passiert.
4. **Ein Applet ist ein `switch` über INS**, Fehler kommen per `ISOException` als Statuswort.
   *Aufgabe 3* ergänzt ein eigenes Kommando (`INS_VERSION`).
5. **Auch SELECT landet in `process()`.** *Aufgabe 5* zeigt, warum man ihn abfangen muss.
6. **Die AID (RID + PIX) ist ein ISO-Begriff** und gilt für alle Karten. *Aufgabe 2* wählt eine
   unbekannte AID.

**Fazit:** Jede Kartenfunktion ist ein APDU-Kommando mit Statuswort-Antwort, und ein Applet
wird über seine AID angesprochen.

---

## Übung 2 – PIN-Prüfung

Doku: [02-pin-pruefung.md](02-pin-pruefung.md) · Aufgaben: [uebung-02-pin.md](../aufgaben/uebung-02-pin.md)

1. **Die PIN wird auf der Karte geprüft**, die Referenz verlässt die Karte nie.
2. **Statuswörter:** `9000`, `63Cx` (Restversuche), `6983` (gesperrt), `6982` (ohne PIN).
   *Aufgabe 1* sagt die Folge voraus, *Aufgabe 2* ändert das Limit und sieht die Wirkung.
3. **Der Zähler ist persistent und wird vor dem Vergleich dekrementiert** (gegen Tearing).
   *Aufgabe 5* durchdenkt, was bei umgekehrter Reihenfolge passieren würde.
4. **Die Freischaltung ist transient:** Reset und Deselect heben sie auf (Aufgabe 1, letzter Schritt).
5. **Bei Java Card implementiert das Applet VERIFY & Co. selbst.** *Aufgabe 3* baut
   CHANGE REFERENCE DATA, *Aufgabe 4* das Entsperren mit PUK.
6. **Der physische Schutz kommt vom Chip**, nicht von der Spezifikation. Der Simulator bildet
   ihn nicht ab.

**Fazit:** PIN-Prüfung heißt Vergleich auf der Karte, ein persistenter Zähler, der vor dem
Vergleich sinkt, und eine Freischaltung, die mit dem Strom verschwindet.

---

## Zwischenübung – Personalisierung

Doku: [03-personalisierung.md](03-personalisierung.md) · Aufgaben: [uebung-03-personalisierung.md](../aufgaben/uebung-03-personalisierung.md)

1. **Der Code ist schon auf dem Chip, personalisiert werden Daten**, verschlüsselt und
   integritätsgeschützt. *Aufgabe 1* verfolgt das im Debugger bis zur Klartext-PIN im RAM.
2. **Key Diversification:** Kartenschlüssel = f(Master, Seriennummer). *Aufgabe 2* zeigt, dass
   Daten nur auf genau eine Karte passen.
3. **Erst MAC prüfen, dann entschlüsseln, und der Tag gehört in den MAC.** *Aufgabe 3* nimmt
   den Tag heraus, und der Umetikettierungs-Angriff gelingt.
4. **Klartext nur kurz im RAM**, danach den Puffer komplett nullen (Aufgabe 1c).
5. **Der Abschluss ist eine Einbahnstraße:** Status umschalten, Schlüssel löschen. *Aufgabe 5*
   zeigt, warum die Reihenfolge zählt.
6. **Ohne Secure Channel sind APDUs wiederholbar.** *Aufgabe 4* spielt ein PIN-APDU erneut ein.

**Fazit:** Personalisierung lädt kartenindividuell geschützte Daten und schließt den Vorgang
endgültig ab. Replay-Schutz braucht zusätzlich Sitzungsschlüssel (SCP03).

---

## Zwischenübung – Issuer Scripts (K_SCRIPT)

Doku: [04-issuer-scripts.md](04-issuer-scripts.md) · Aufgaben: [uebung-04-issuer-scripts.md](../aufgaben/uebung-04-issuer-scripts.md)

1. **Verwaltung nach der Ausgabe braucht einen Schlüssel, der die Personalisierung überlebt.**
   *Aufgabe 1* zerlegt ein Skript-APDU.
2. **Sitzungsschlüssel aus einem Zähler, und nur ein höherer Zähler gilt.** *Aufgabe 2* dreht
   den Zähler zurück und wird abgewiesen, obwohl der MAC stimmt.
3. **Schlüsselgenerationen:** PUT KEY bringt den neuen Schlüssel verschlüsselt unter dem alten.
   *Aufgabe 3* zeigt, dass ein Rückfall auf eine alte Generation scheitert.
4. **Der Header gehört in den MAC**, sonst wird aus PIN-Setzen ein PUT KEY (Aufgabe 5).
5. **Skripte erweitern den Lebenszyklus.** *Aufgabe 4* baut „Karte sperren“.
6. **Drei Schlüsselebenen:** Master, kartenindividuell, Sitzung.

**Fazit:** Ein überlebender Verwaltungsschlüssel plus zählerabhängige Sitzungsschlüssel
ermöglicht sichere Änderungen nach der Ausgabe, das Prinzip der EMV Issuer Scripts.

---

## Übung 3 – On-Card-Schlüssel und Signatur

Doku: [05-schluessel-und-signatur.md](05-schluessel-und-signatur.md) · Aufgaben: [uebung-05-signatur.md](../aufgaben/uebung-05-signatur.md)

1. **Das Schlüsselpaar entsteht auf der Karte**, nur der öffentliche Schlüssel geht hinaus.
   *Aufgabe 1* liest Punkt und DER-Signatur.
2. **Die PIN regelt, *wer* signiert, nicht *was*.** *Aufgabe 2* verlangt die PIN pro Signatur,
   *Aufgabe 3* schickt nur noch den Hash, und die Karte sieht das Dokument nicht mehr.
3. **„Der Schlüssel verlässt die Karte nie“ reicht nicht.** Eine schlechte ECDSA-Nonce verrät
   ihn mathematisch. *Aufgabe 5* rechnet den privaten Schlüssel aus einer bzw. zwei Signaturen aus.
4. **Schlüsselprüfungen bei der Zertifikatsausstellung** fangen solche Fehler ab. *Aufgabe 4*
   baut eine Mini-CA mit Duplikatprüfung.

**Fazit:** On-Card-Erzeugung schützt den Schlüssel vor Kopien, seine Geheimhaltung hängt aber
an der Implementierung, besonders am Zufall.

---

## Übung 3, Variante 2 – PQC (ML-DSA, ML-KEM)

Doku: [06-pqc-ml-dsa-ml-kem.md](06-pqc-ml-dsa-ml-kem.md) · Aufgaben: [uebung-06-pqc.md](../aufgaben/uebung-06-pqc.md)

1. **Gleiches Prinzip, viel größere Daten:** ML-DSA-65 hat 1952 Bytes Public Key und 3309 Bytes
   Signatur. *Aufgabe 3* rechnet das für ML-DSA-87 durch.
2. **Zwei Protokolle für große Daten:** Extended Length (*Aufgabe 1*) oder Chaining und
   GET RESPONSE (*Aufgabe 2* verfolgt die `61xx`-Folge, *Aufgabe 4* variiert die Teilgröße).
3. **ML-KEM kapselt, statt zu verschlüsseln.** *Aufgabe 5* zeigt die implicit rejection:
   kein Fehler, aber ein falsches Geheimnis.
4. **Die Eingabe lässt sich verkleinern, die Ausgabe nicht** (External μ, Aufgabe 6).
5. **Java Card 3.0.5 hat keine PQC-API.** Die Übung ist deshalb eine Protokoll-Simulation.

**Fazit:** PQC ändert nicht das Prinzip, aber die Datenmengen, und damit Protokoll, Speicher
und Zertifikate entlang der ganzen Kette.

---

## Übung 4 – Speicherarten und Transaktionen

Doku: [07-transaktionen.md](07-transaktionen.md) · Aufgaben: [uebung-07-transaktionen.md](../aufgaben/uebung-07-transaktionen.md)

1. **Drei Speicherarten:** persistent, `CLEAR_ON_RESET`, `CLEAR_ON_DESELECT`. *Aufgabe 1* sagt
   ihr Verhalten bei Applet-Wechsel und Reset voraus.
2. **Mehrere Schreibzugriffe ohne Schutz** hinterlassen bei Stromausfall inkonsistente Daten.
   In der Börsen-Demo verschwinden 100 Einheiten.
3. **Atomarität auf drei Wegen:** JCRE-Transaktion, eigenes Undo-Journal (*Aufgabe 2*) oder
   ein einziger atomarer `Util.arrayCopy` (*Aufgabe 3*).
4. **Beim Journal zählt die Reihenfolge:** erst sichern, dann Flag (Aufgabe 5).
5. **Der Simulator rollt Transaktionen nicht zurück.** *Aufgabe 4* stellt Simulator und echte
   Karte gegenüber.

**Fazit:** Persistente Änderungen aus mehreren Schreibzugriffen müssen atomar sein. Ob das auf
der Karte klappt, lässt sich nur auf echter Hardware prüfen.

---

## GlobalPlatform

Doku: [08-globalplatform.md](08-globalplatform.md) · Aufgaben: [uebung-08-globalplatform.md](../aufgaben/uebung-08-globalplatform.md)

1. **Java Card regelt das Laufen, GlobalPlatform das Verwalten.**
2. **SCP03-Handshake:** INITIALIZE UPDATE und EXTERNAL AUTHENTICATE mit Challenges beider
   Seiten. *Aufgabe 1* zerlegt die Antwort, *Aufgabe 3* berechnet das Host-Kryptogramm.
3. **MAC-Kette und neue Challenges** verhindern Replay und Umsortieren. *Aufgabe 2* sagt die
   Abwehr dreier Angriffe voraus.
4. **Kartenlebenszyklus:** OP_READY → INITIALIZED → SECURED ⇄ CARD_LOCKED → TERMINATED.
   *Aufgabe 4* testet erlaubte und verbotene Übergänge.
5. **Privilegien** steuern, was eine Security Domain darf. *Aufgabe 5* dekodiert `9E`.
6. **Personalisierung über die Security Domain** würde die eigene Kanal-Logik ersetzen
   (Aufgabe 6).

**Fazit:** GlobalPlatform macht die Karte verwaltbar: Security Domains mit Privilegien, ein
Lebenszyklus und SCP03 als gegenseitig authentisierter, MAC-verketteter Kanal.

---

## Querschnitt: Grenzen des Simulators

In den Aufgaben selbst beobachtet:

| Grenze | Wo sichtbar |
|---|---|
| keine physische Sicherheit (Speicherschutz, Sensoren, Seitenkanäle) | Übung 2 |
| SELECT einer unbekannten AID liefert `6999` statt `6A82` | Übung 1, Aufgabe 2 |
| Le wird ignoriert | Übung 1, Aufgabe 4 |
| Zufall kaputt: gleicher erster Schlüssel auf jeder Karte, Nonce = privater Schlüssel | Übung 3, Aufgaben 4 und 5 |
| Transaktionen ohne Wirkung, Commit-Kapazität ist ein Platzhalter | Übung 4, Aufgabe 4 |
| kein CAP, kein GP Card Manager, keine PQC-API | GP und PQC als Protokoll-Simulation |
