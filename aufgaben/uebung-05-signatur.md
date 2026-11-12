# Aufgabenzettel Übung 3 – On-Card-Schlüssel und Signatur

**Lernziel:** Schlüsselerzeugung und Signatur auf der Karte, Prüfung off-card, und
verstehen, warum die Qualität der Implementierung (Zufall!) über die Sicherheit entscheidet.

**Material:** [`SignApplet.java`](../src/main/java/playground/sign/SignApplet.java),
[`SignAppletTest.java`](../src/test/java/playground/sign/SignAppletTest.java),
[`NonceReuseAttack.java`](../src/test/java/playground/sign/NonceReuseAttack.java),
[docs/05-schluessel-und-signatur.md](../docs/05-schluessel-und-signatur.md)

**Ausführen:**
```bash
mise exec -- mvn test -Dtest=SignAppletTest
```

## Testbed

| | Datei |
|---|---|
| Applet-Kopie (Aufgaben 2, 3) | [`src/main/java/playground/aufgaben/uebung05/SignAufgabeApplet.java`](../src/main/java/playground/aufgaben/uebung05/SignAufgabeApplet.java) |
| Mini-CA (Aufgabe 4) | [`src/test/java/playground/aufgaben/uebung05/MiniCa.java`](../src/test/java/playground/aufgaben/uebung05/MiniCa.java) |
| Prüfung + Eingaben (Aufgaben 1, 5) | [`src/test/java/playground/aufgaben/uebung05/Uebung05Aufgaben.java`](../src/test/java/playground/aufgaben/uebung05/Uebung05Aufgaben.java) |

- **Aufgabe 3:** Das Kommando `00 2A 9E 90` (P2 `90` = „Eingabe ist ein Hashwert“) ist
  verdrahtet. Du ergänzt nur den Aufruf `signPreComputedHash`. jCardSim unterstützt ihn.
- **Aufgabe 5:** Drei Methoden `berechneK`, `berechneDausK`, `berechneDbeiKgleichD`. Der Test
  holt echte Signaturen von der Referenzkarte, fälscht mit deinem d und prüft gegen den
  Kartenschlüssel.

```bash
mise exec -- mvn test -Dtest=Uebung05Aufgaben
```

## Musterlösung

Erst selbst versuchen! Die vollständige Lösung liegt im Paket `playground.loesungen.uebung05` und läuft im normalen Build (und in der CI) mit:

| | Datei |
|---|---|
| Lösungstest | [`Uebung05LoesungTest.java`](../src/test/java/playground/loesungen/uebung05/Uebung05LoesungTest.java) |
| | [`SignLoesungApplet.java`](../src/main/java/playground/loesungen/uebung05/SignLoesungApplet.java) |
| | [`MiniCa.java`](../src/test/java/playground/loesungen/uebung05/MiniCa.java) |

```bash
mise exec -- mvn test -Dtest=Uebung05LoesungTest
```

---

## Aufgabe 1: Öffentlichen Schlüssel und Signatur lesen (10 Min.)

a) Wie ist die Antwort auf `00 46 80 00 00` aufgebaut (65 Bytes)?
b) Zerlege die Signatur `30 44 02 20 … 02 20 …`. Was bedeuten `30`, `02`, `20`, `44`?
c) Warum ist eine der Signaturen im Trace `30 45 … 02 21 00 …`?

<details><summary>Lösungshinweis</summary>

a) `04` (unkomprimierter Punkt) + X (32) + Y (32).
b) `30` = DER SEQUENCE, `44` = Länge 68, `02` = INTEGER, `20` = 32 Bytes (r bzw. s).
c) Ist das oberste Bit eines Integers gesetzt, stellt DER eine `00` voran (sonst wäre er
negativ). Dann ist der Integer 33 Bytes lang, die Sequenz 69 (`45`).
</details>

## Aufgabe 2: PIN pro Signatur (10 Min.)

Rufe in `computeSignature()` nach dem Signieren `pin.reset()` auf, so wie Karten für
qualifizierte Signaturen. Welche Tests müssen angepasst werden?

<details><summary>Lösungshinweis</summary>

Alle Tests, die mehrmals hintereinander signieren, ohne dazwischen VERIFY zu senden
(z. B. `simulatorReusesEcdsaNonce`, `nonceReuseLeaksPrivateKey`). Sie bekommen ab der zweiten
Signatur `6982`. Begründung für die QES: Malware soll nicht im Hintergrund weitersignieren können.
</details>

## Aufgabe 3: Nur den Hash an die Karte (20 Min.)

Stelle auf Off-Card-Hashing um: Der Host berechnet SHA-256 und schickt 32 Bytes. Die Karte
nutzt `ecdsa.signPreComputedHash(...)`. Die Off-Card-Prüfung bleibt `SHA256withECDSA` über
das Dokument.

<details><summary>Lösungshinweis</summary>

`sigLen = ecdsa.signPreComputedHash(buf, cdata, (short) 32, sigBuffer, (short) 0);`
Die Signatur verifiziert als `SHA256withECDSA` über das Dokument, weil ECDSA ohnehin nur
den Hash signiert. Nach der Umstellung sieht die Karte das Dokument nicht mehr: WYSIWYS.
</details>

## Aufgabe 4: Duplikatprüfung wie im Trust Center (20 Min.)

Schreibe eine kleine „CA“-Klasse im Test, die eingereichte öffentliche Schlüssel speichert
und ein Duplikat ablehnt. Lass zwei frische Karten je einen Schlüssel erzeugen und
einreichen. Was passiert im Simulator?

<details><summary>Lösungshinweis</summary>

Die zweite Karte wird abgelehnt, weil der Simulator auf jeder Karte denselben ersten
Schlüssel erzeugt. In einem echten Trust Center würde genau so eine Prüfung einen kaputten
RNG oder eine fehlerhafte Charge entdecken.
</details>

## Aufgabe 5: Den Angriff nachrechnen (15 Min.)

Lies `NonceReuseAttack`. Leite aus `s = k⁻¹ (z + r·d)` her:
a) die Formel für k bei zwei Signaturen mit gleichem k
b) die Formel für d, wenn k = d gilt

<details><summary>Lösungshinweis</summary>

a) `s1 − s2 = k⁻¹ (z1 − z2)` ⇒ `k = (z1 − z2) / (s1 − s2) mod n`, dann `d = (s1·k − z1) / r`.
b) `s·d = z + r·d` ⇒ `d (s − r) = z` ⇒ `d = z / (s − r) mod n`.
</details>

---

## Kontrollfragen

1. Warum sollen Signatur- und Authentisierungsschlüssel auf der Karte entstehen,
   Verschlüsselungsschlüssel oft nicht?
2. Was schützt die PIN, und was schützt sie **nicht**?
3. Warum ist „der private Schlüssel verlässt die Karte nie“ keine vollständige
   Sicherheitsaussage?
4. Nenne drei Gegenmaßnahmen gegen schlechten Zufall, davon eine im Trust Center.
5. Was war ROCA, und warum ist es für On-Card-Schlüsselerzeugung relevant?
