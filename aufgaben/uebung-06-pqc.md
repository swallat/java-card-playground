# Aufgabenzettel Übung 3, Variante 2 – PQC (ML-DSA, ML-KEM)

**Lernziel:** Die Protokollfolgen von Post-Quanten-Kryptografie auf Chipkarten verstehen:
Größen, Extended Length vs. Chaining/GET RESPONSE, KEM-Semantik.

**Hinweis:** Die Krypto kommt aus BouncyCastle, weil Java Card 3.0.5 keine PQC-API hat.
Die „Karte“ ist eine Protokoll-Simulation und nicht CAP-fähig.

**Material:** [`PqcCardApplet.java`](../src/test/java/playground/pqc/PqcCardApplet.java),
[`PqcCardTest.java`](../src/test/java/playground/pqc/PqcCardTest.java),
[`Card.java`](../src/test/java/playground/support/Card.java) (`sendShortApdusOnly`),
[docs/06-pqc-ml-dsa-ml-kem.md](../docs/06-pqc-ml-dsa-ml-kem.md)

**Ausführen:**
```bash
mise exec -- mvn test -Dtest=PqcCardTest
```

## Testbed

| | Datei |
|---|---|
| Prüfung + Vorhersagen (alle Aufgaben) | [`src/test/java/playground/aufgaben/uebung06/Uebung06Aufgaben.java`](../src/test/java/playground/aufgaben/uebung06/Uebung06Aufgaben.java) |

Hier gibt es nichts zu programmieren: Du rechnest und sagst voraus, der Test misst. ML-DSA-87
ist auf der simulierten Karte bereits verfügbar (`ALG_ML_DSA_87`). Die Teilgröße für
Aufgabe 4 stellst du direkt im Testbed ein (`A4_TEILGROESSE`).

```bash
mise exec -- mvn test -Dtest=Uebung06Aufgaben
```

## Musterlösung

Erst selbst versuchen! Die vollständige Lösung liegt im Paket `playground.loesungen.uebung06` und läuft im normalen Build (und in der CI) mit:

| | Datei |
|---|---|
| Lösungstest | [`Uebung06LoesungTest.java`](../src/test/java/playground/loesungen/uebung06/Uebung06LoesungTest.java) |

Reine Vorhersage-Aufgaben: Die Lösung ist die ausgefüllte Prüfklasse.

```bash
mise exec -- mvn test -Dtest=Uebung06LoesungTest
```

---

## Aufgabe 1: Extended-APDUs zerlegen (10 Min.)

a) `00 46 80 01 00 00 00`: Woran erkennt man Extended Length? Welchen Wert hat Le?
b) `00 2A 80 86 00 04 40 <1088 Bytes> 00 00`: Zerlege Lc und Le. An welcher Position
   (Index ab 0) steht der Marker?

<details><summary>Lösungshinweis</summary>

a) Nach dem Header steht `00` als Marker, dann 2 Bytes Le: `00 00` = 65536.
b) `00` Marker an Index 4 (direkt nach CLA INS P1 P2), Lc `04 40` = 1088 (2 Bytes), Daten,
Le `00 00` (ohne neuen Marker).
</details>

## Aufgabe 2: GET RESPONSE im Trace verfolgen (10 Min.)

Führe `mlDsaShortApdusOnly` aus. Was bedeuten `6100`, `61A0`, `61ED`? Warum endet die
Signatur-Abholung mit `00 C0 00 00 ED`?

<details><summary>Lösungshinweis</summary>

`61xx` = „xx Bytes liegen noch bereit“, `6100` = 256 oder mehr. `A0` = 160, `ED` = 237.
3309 = 12 × 256 + 237: Der letzte GET RESPONSE fordert genau die restlichen 237 Bytes an.
</details>

## Aufgabe 3: ML-DSA-87 statt ML-DSA-65 (15 Min.)

Rechne aus, wie viele APDUs Variante B (nur Short) für Schlüssel + Signatur mit
**ML-DSA-87** braucht (Größen aus FIPS 204 bzw. der Tabelle in docs/06). Trage die Zahl im
Testbed ein, der Test misst nach.

<details><summary>Lösungshinweis</summary>

ML-DSA-87: Public Key 2592, Signatur 4627 Bytes (im Test gemessen).
2592 / 256 → 11 Antworten, 4627 / 256 → 19 Antworten, zusammen 30 APDUs. Extended: weiterhin 2.
</details>

## Aufgabe 4: Kleinere Kettenglieder (10 Min.)

Mit einer Teilgröße von 128 statt 255 Bytes (z. B. für eine Strecke mit kleinem Puffer):
Wie viele APDUs braucht das ML-KEM-Chiffrat jetzt? Probiere danach weitere Teilgrößen aus.

<details><summary>Lösungshinweis</summary>

1088 / 128 = 8,5 → 9 APDUs (8 × 128 + 64).
</details>

## Aufgabe 5: Implicit Rejection verstehen (10 Min.)

Lies `mlKemTamperedCiphertextGivesWrongSecretWithoutError`. Warum meldet ML-KEM keinen
Fehler? Was muss ein Protokoll deshalb zusätzlich vorsehen?

<details><summary>Lösungshinweis</summary>

Ein Fehlercode wäre ein Orakel für Angriffe mit manipulierten Chiffraten. ML-KEM liefert
deshalb ein pseudozufälliges falsches Geheimnis. Erkannt wird das erst durch eine
Schlüsselbestätigung oder die folgende authentisierte Verschlüsselung.
</details>

## Aufgabe 6: Nur μ an die Karte (Bonus, Recherche + Umsetzung)

Informiere dich in FIPS 204 über „external μ“ und HashML-DSA. Wie viele Bytes müssten dann
noch zur Karte? Ändert das etwas an der Antwortgröße?

<details><summary>Lösungshinweis</summary>

μ hat 64 Bytes, die Eingabe passt also in ein Short-APDU. Die Signatur bleibt 3309 Bytes,
Extended Length oder GET RESPONSE bleiben für die Antwort nötig.
</details>

---

## Kontrollfragen

1. Nenne öffentlichen Schlüssel und Signaturgröße von ML-DSA-65 im Vergleich zu P-256.
2. Wann wählt man Extended Length, wann Chaining? Welche Komponenten müssen jeweils mitspielen?
3. Was ist bei einem KEM anders als bei „mit dem Public Key verschlüsseln“?
4. Warum hat die Schlüsseleinigung bei der PQC-Migration Vorrang vor der Signatur?
5. Warum ist Krypto-Agilität ein Kriterium bei der Auswahl einer Kartenplattform?
6. Welcher Protokollfehler wurde bei der Entwicklung durch einen Test gefunden?
