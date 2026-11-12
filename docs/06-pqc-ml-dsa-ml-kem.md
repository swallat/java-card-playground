# 06 – Post-Quanten-Kryptografie: ML-DSA und ML-KEM (Variante 2 zu Übung 3)

Code: [`PqcCardApplet.java`](../src/test/java/playground/pqc/PqcCardApplet.java),
Test: [`PqcCardTest.java`](../src/test/java/playground/pqc/PqcCardTest.java)

## Wichtige Einschränkung vorweg

**Java Card 3.0.5 hat keine API für ML-DSA oder ML-KEM**, und jCardSim implementiert sie
nicht. Hersteller bauen PQC derzeit in den **Betriebssystemkern** ihrer Karten ein und
bieten es über eigene Schnittstellen an. Einen Standard dafür gab es bei der Recherche
(12.11.2026) nicht. Im JDK sind ML-KEM/ML-DSA erst ab Java 24 enthalten (JEP 496/497).

Diese Variante ist deshalb eine **Protokoll-Simulation**: Die „Karte“ rechnet mit
BouncyCastle 1.80 (finale FIPS 203/204). Der Code liegt bewusst unter `src/test`, weil er
**nicht CAP-konvertierbar** ist. Realistisch ist die **APDU-Seite**: Größen, Extended
Length, Kommandoabläufe.

## Die Verfahren

| | Zweck | ersetzt | Standard |
|---|---|---|---|
| **ML-DSA** (Module-Lattice Digital Signature Algorithm, ex Dilithium) | Signatur | ECDSA, RSA-Signatur | FIPS 204 |
| **ML-KEM** (Module-Lattice Key Encapsulation Mechanism, ex Kyber) | Schlüsseleinigung | ECDH, RSA-Verschlüsselung | FIPS 203 |

## Größenvergleich (Bytes)

| Verfahren | öffentlicher Schlüssel | privater Schlüssel | Signatur / Chiffrat |
|---|---|---|---|
| ECDSA P-256 | 65 | 32 | ca. 72 (DER) |
| RSA-2048 | ca. 260 | ca. 1200 (CRT) | 256 |
| ML-DSA-44 | 1312 | 2560 (oder 32-Byte-Seed) | 2420 |
| **ML-DSA-65** | **1952** | 4032 (oder 32-Byte-Seed) | **3309** |
| ML-DSA-87 | 2592 | 4896 (oder 32-Byte-Seed) | 4627 |
| ML-KEM-512 | 800 | 1632 (oder 64-Byte-Seed) | 768 |
| **ML-KEM-768** | **1184** | 2400 (oder 64-Byte-Seed) | **1088** (Geheimnis: 32) |
| ML-KEM-1024 | 1568 | 3168 (oder 64-Byte-Seed) | 1568 |

Fett gedruckt: im Test verwendet und gemessen. Die übrigen Werte stammen aus FIPS 203/204
(aus dem Gedächtnis, nicht einzeln nachgeprüft).

## Zwei Protokollvarianten für große Daten (ISO 7816-4)

| | A) Extended Length | B) nur Short-APDUs |
|---|---|---|
| Eingabe > 255 Bytes | ein APDU, Lc mit 2 Bytes | **Command Chaining**: Teile à 255 Bytes, alle außer dem letzten mit CLA-Bit `0x10` |
| Antwort > 256 Bytes | ein APDU, Le mit 2 Bytes | **Response Chaining**: erste 256 Bytes + SW `61xx`, Rest per `GET RESPONSE` (`00 C0 00 00 Le`) |
| Voraussetzung | Karte, Leser, Treiber, Middleware können Extended Length | funktioniert mit jedem Leser |
| APDUs für ML-DSA-65 (Schlüssel + Signatur) | **2** | **21** (8 + 13) |
| APDUs für ML-KEM-768-Chiffrat (1088 Bytes) | **1** | **5** (4 × 255 + 68) |

Die APDU-Zahlen sind im Test gemessen (`Card.apduCount`).

## Trace Variante B: nur Short-APDUs

```
--> 00 46 80 01 00                       GENERATE ML-DSA-65, Short Le = 256
<-- <256 Bytes> 6100                     6100 = "256 oder mehr Bytes folgen"
--> 00 C0 00 00 00                       GET RESPONSE, Le = 256
<-- <256 Bytes> 6100
    … (insgesamt 7 x 256) …
<-- <256 Bytes> 61A0                     61A0 = "noch 160 Bytes"
--> 00 C0 00 00 A0                       GET RESPONSE, Le = 160
<-- <160 Bytes> 9000                     zusammen: 1952 Bytes

--> 10 2A 80 86 FF <255 Bytes>           Chiffrat, Teil 1: CLA 10 = "es kommt noch mehr"
<-- 9000
--> 10 2A 80 86 FF <255 Bytes>           Teil 2
--> 10 2A 80 86 FF <255 Bytes>           Teil 3
--> 10 2A 80 86 FF <255 Bytes>           Teil 4
--> 00 2A 80 86 44 <68 Bytes> 00         letzter Teil: CLA 00, Le = 256
<-- <32 Bytes Schlüsselbestätigung> 9000 Karte rechnet erst jetzt
```

Protokoll-Fehlerfälle (getestet):
- **Unterbrochene Kette:** Kommt während einer Kette ein anderes Kommando, antwortet die
  Karte `6883` („letztes Kommando der Kette erwartet“). Diese Prüfung muss **vor allen
  anderen** stehen. Beim ersten Entwurf prüfte das Applet zuerst, ob ein Schlüssel da ist,
  und lieferte deshalb fälschlich `6985`.
- **GET RESPONSE ohne bereitliegende Daten:** `6985`.
- Jedes andere Kommando verwirft eine nicht abgeholte Antwort.

## Trace Variante A: Extended Length

```
--> 00 46 80 01 00 00 00                 Extended Le: 00 (Marker) + 00 00 (= 65536)
<-- AC 4F 67 45 … [1952 Bytes] … 8C DF 9000

--> 00 2A 9E 9A 00 00 1A <26 Bytes Dokument> 00 00
         │        └─┬───┘                    └─┬─┘
         │      Extended Lc (26)          Extended Le
<-- 91 6D 3C B0 … [3309 Bytes] … 1B 1F 9000          ML-DSA-65-Signatur

--> 00 46 80 02 00 00 00                 ML-KEM-768 erzeugen
<-- 03 A1 3C BD … [1184 Bytes] … B2 52 9000

--> 00 2A 80 86 00 04 40 <1088 Bytes Chiffrat> 00 00     PSO: DECIPHER, Lc = 0x0440 = 1088
<-- 55 6D 4D 72 … (32 Bytes) 9000                        nur Schlüsselbestätigung
```

### Extended Length, Byte für Byte

| Fall | Kodierung |
|---|---|
| Short | Lc 1 Byte (1–255), Le 1 Byte (`00` = 256) |
| Extended Case 2 | `00` + Le 2 Bytes (`00 00` = 65536) |
| Extended Case 3/4 | `00` + Lc 2 Bytes, Daten, ggf. Le 2 Bytes (ohne erneuten Marker) |

Das Applet muss `javacardx.apdu.ExtendedLength` implementieren, Eingaben über
`receiveBytes()` einsammeln und Ausgaben mit `setOutgoingLength()` + `sendBytesLong()` senden.
Für Variante B hält es eine Kette (Eingabepuffer + INS/P1/P2) und eine noch nicht
abgeholte Antwort als Zustand. Die Host-Seite steht in `Card.sendShortApdusOnly()`.

## ML-DSA: Ablauf

Wie bei ECDSA: Das Schlüsselpaar entsteht auf der Karte, signiert wird nach PIN, die
Prüfung läuft off-card nur mit dem öffentlichen Schlüssel. Ein verändertes Dokument fällt durch.

## ML-KEM: anders als Verschlüsselung

Ein KEM „verschlüsselt“ nicht, es **kapselt ein Geheimnis**:

```
Karte:  erzeugt (ek, dk), gibt ek (1184 Bytes) heraus
Host:   (Chiffrat, ss) = Encaps(ek)        ss = 32-Byte-Geheimnis
Host ──Chiffrat (1088 Bytes)──▶ Karte:   ss = Decaps(dk, Chiffrat)
```

Danach haben beide dasselbe ss und leiten daraus z. B. einen AES-Schlüssel ab. Die Karte
gibt ss im Test **nicht** heraus, sondern nur SHA-256("KEY-CONFIRM" ‖ ss).

**Implicit Rejection:** Ein manipuliertes Chiffrat führt **nicht** zu einem Fehler. ML-KEM
liefert dann ein falsches, zufällig wirkendes Geheimnis (Schutz gegen Angriffe, die
Fehlermeldungen auswerten). Ob die Einigung geklappt hat, zeigt erst eine
Schlüsselbestätigung bzw. die folgende authentisierte Verschlüsselung.

## Was PQC für Chipkarten bedeutet

1. **APDU:** Entweder Extended Length (wenige APDUs, aber jedes Glied muss es können) oder
   Command/Response Chaining (läuft überall, aber ca. 10-mal so viele APDUs und mehr
   Zustandslogik auf der Karte). Karte, Leser, Treiber und Middleware (PKCS#11, Minidriver)
   müssen die gewählte Variante unterstützen. Gerade ältere Leser und kontaktlose Strecken
   sind ein Risiko, und mehr APDUs bedeuten mehr Latenz, z. B. beim Smartcard-Logon.
2. **Speicher:** öffentliche und private Schlüssel im KB-Bereich statt 32–65 Bytes.
   Abhilfe: nur den **Seed** speichern (32 bzw. 64 Bytes) und den Schlüssel bei Bedarf
   expandieren, was Rechenzeit kostet. Der RAM-Bedarf beim Signieren ist deutlich höher als
   bei ECDSA. Smartcard-Implementierungen müssen dafür speziell optimiert werden.
3. **Zertifikate:** Ein X.509-Zertifikat mit ML-DSA-Schlüssel und ML-DSA-Signatur der CA
   ist mehrere KB groß statt einige hundert Bytes. Mehr Platz für Zertifikatsdateien auf der
   Karte, längere Ketten, größere Logon-Datenmengen.
4. **Übergangszeit:** **Hybride** Verfahren (ECDH + ML-KEM, ECDSA + ML-DSA bzw. Composite-
   Zertifikate), damit die Sicherheit nicht allein an den neuen Verfahren hängt. Das BSI
   empfiehlt hybride Verfahren in der Übergangszeit.
5. **Dringlichkeit:** Bei Verschlüsselung (KEM) droht „Harvest now, decrypt later“: heute
   mitgeschnittene Daten werden später entschlüsselt. Signaturen sind erst angreifbar, wenn
   ein Quantencomputer existiert. **Schlüsseleinigung ist daher dringlicher als Signatur.**
6. **Symmetrische Verfahren:** AES bleibt nutzbar. Grover halbiert effektiv die
   Schlüssellänge, deshalb langfristig AES-256 statt AES-128 (betrifft z. B. K_ENC, K_MAC,
   K_SCRIPT und SCP03-Schlüssel).
7. **Kartenlebenszyklus:** Karten sind mehrere Jahre im Feld. Algorithmenwechsel bedeutet
   Kartentausch oder, bei Java Card mit GlobalPlatform, nachladbare Applets (sofern die
   Plattform PQC im OS mitbringt). Das ist ein Argument für **Krypto-Agilität** bei der
   Plattformwahl.

## Grenzen dieser Simulation

- Keine echten Karten-Ressourcen: Laufzeit, RAM und Persistenz verhalten sich wie normales
  Java, nicht wie ein Secure Controller.
- Die Kommandos sind an ISO 7816-8 angelehnt, aber nicht standardisiert für PQC.
- Der Zufall kommt hier aus `java.security.SecureRandom`, nicht aus jCardSim, und ist
  deshalb nicht von der Nonce-Schwäche aus Übung 3 betroffen.

## Merksatz

> ML-DSA und ML-KEM funktionieren auf der Karte nach demselben Muster wie ECDSA/ECDH:
> privater Schlüssel bleibt drin, nur öffentliche Werte gehen raus. Die Größen ändern aber
> alles darunter: Schlüssel und Signaturen im Kilobyte-Bereich erzwingen Extended Length APDUs
> bzw. Chaining, mehr Speicher und größere Zertifikate, und jedes Glied der Kette (Karte, Leser,
> Middleware, CA) muss das können. In der Übergangszeit setzt man hybrid ein, und die
> Schlüsseleinigung hat wegen „Harvest now, decrypt later“ Vorrang.
