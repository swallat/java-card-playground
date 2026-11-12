# 05 – On-Card-Schlüsselerzeugung und Signatur (Übung 3)

Code: [`SignApplet.java`](../src/main/java/playground/sign/SignApplet.java),
Test: [`SignAppletTest.java`](../src/test/java/playground/sign/SignAppletTest.java),
Angriff: [`NonceReuseAttack.java`](../src/test/java/playground/sign/NonceReuseAttack.java)

## Worum es geht

Das ist der Kern jeder PKI-Karte (Dienstausweis, Signaturkarte, Heilberufsausweis):

1. **Das Schlüsselpaar entsteht auf der Karte**, aus deren Zufallszahlengenerator.
2. **Nur der öffentliche Schlüssel verlässt die Karte.** Er geht an die CA.
3. **Signieren nur nach PIN.** Daten rein, Signatur raus.
4. **Es gibt kein Kommando, das den privaten Schlüssel ausgibt.**
5. **Prüfung off-card** mit Standard-Krypto, nur mit dem öffentlichen Schlüssel.

### Warum On-Card-Erzeugung?

Ein Schlüssel, den nie jemand in der Hand hatte, kann nicht verloren, kopiert oder
missbraucht werden, auch nicht vom Herausgeber. Für Authentisierung und Signatur ist das
zentral (qualifizierte Signatur: „alleinige Kontrolle“ des Inhabers).

**Verschlüsselungsschlüssel** werden in Unternehmens-PKIs dagegen oft bewusst zentral
erzeugt und gesichert (Key Backup/Escrow), damit verschlüsselte Daten nach Kartenverlust
lesbar bleiben. Dann werden sie per Personalisierung importiert (siehe 03).

### Einbettung in den PKI-Ablauf

```
Karte: GENERATE KEY PAIR ──▶ öffentlicher Schlüssel
Middleware: baut CSR (PKCS#10) ──▶ Karte signiert den CSR-Inhalt (Proof of Possession)
CA: prüft, stellt Zertifikat aus ──▶ Zertifikat wird auf die Karte geschrieben
```

## Kommandos (angelehnt an ISO 7816-8)

| Kommando | APDU | Antwort |
|---|---|---|
| VERIFY | `00 20 00 81 04 31 32 33 34` | `9000` |
| GENERATE ASYMMETRIC KEY PAIR | `00 46 80 00 00` | `04 ‖ X ‖ Y` (65 Bytes) |
| öffentlichen Schlüssel lesen | `00 46 81 00 00` | dito, ohne PIN |
| PSO: COMPUTE DIGITAL SIGNATURE | `00 2A 9E 9A Lc <Daten> 00` | DER-Signatur, ca. 70–72 Bytes |

P1/P2 `9E 9A` (ISO 7816-8): `9E` = digitale Signatur ausgeben, `9A` = zu signierende Daten.

```
--> 00 46 80 00 00
<-- 04 2B 49 5A 9B … CA EB 49 | DE E3 77 C9 … 6D D6 9000
       └──── X (32 Bytes) ───┘ └──── Y (32 Bytes) ──┘

--> 00 2A 9E 9A 1A 44 69 65 6E 73 74 … 34 37 31 31 00     "Dienstreiseantrag Nr. 4711"
<-- 30 44 02 20 <r, 32 Bytes> 02 20 <s, 32 Bytes> 9000     DER SEQUENCE { INTEGER r, INTEGER s }
```

## Der Code

- **Kurvenparameter explizit setzen** (`setP256()`): p, a, b, Basispunkt G, Ordnung n,
  Kofaktor. Viele echte Karten kennen die Kurve nicht von selbst.
- **Schlüsselobjekte** `ECPrivateKey`/`ECPublicKey` über `KeyBuilder`, zusammengefasst in
  `KeyPair`. `keyPair.genKeyPair()` erzeugt den privaten Schlüssel **direkt im Schlüsselobjekt**.
- **Öffentlicher Schlüssel:** `publicKey.getW()` liefert den Punkt W = d·G.
- **Signatur:** `Signature.ALG_ECDSA_SHA_256`. Die Karte hasht und signiert in einem Schritt.
  Die Ausgabe geht in einen eigenen transienten Puffer, damit sich Ein- und Ausgabe im
  APDU-Buffer nicht überschreiben.
- **Persistenz:** Der private Schlüssel überlebt einen Reset, die PIN-Freischaltung nicht.

## Tests

| Test | zeigt |
|---|---|
| `generateWithoutPinIsRejected` | Erzeugung nur nach PIN (`6982`) |
| `signWithoutKeyIsRejected` | ohne Schlüssel keine Signatur (`6985`) |
| `generateReturnsUncompressedPublicPoint` | 65 Bytes, `04`-Präfix, gültiger P-256-Punkt |
| `signWithoutPinIsRejected` | nach Reset wieder PIN nötig |
| `signOnCardVerifyOffCard` | Off-Card-Prüfung mit `SHA256withECDSA` ok, verändertes Dokument scheitert |
| `privateKeySurvivesReset` | Schlüssel persistent |
| `regeneratingReplacesTheKey` | neues Paar: alte Zertifikate passen nicht mehr |
| `thereIsNoWayToReadThePrivateKey` | kein Kommando liefert den privaten Schlüssel |

## Befund: Der Zufall im Simulator ist kaputt

Erwartet war: ECDSA verwendet pro Signatur eine frische Zufallszahl k, also unterscheiden
sich zwei Signaturen desselben Dokuments. Im Simulator war das **nicht** so.

**Ursache (jCardSim 3.0.6.0, per `javap` geprüft):** `Signature.init(…, MODE_SIGN)` erzeugt
jedes Mal einen `SecureRandomNullProvider`, einen SHA-1-`DigestRandomGenerator` **ohne Seed**.

**Folgen, als Tests belegt:**

| Test | Befund |
|---|---|
| `simulatorReusesEcdsaNonce` | zwei verschiedene Dokumente → **gleiches r**, also gleiche Nonce k |
| `nonceReuseLeaksPrivateKey` | aus zwei Signaturen wird d berechnet. Mit d gefälschte Signatur verifiziert mit dem Kartenschlüssel. |
| `simulatorGeneratesSameFirstKeyOnEveryCard` | zwei frische „Karten“ erzeugen **denselben** ersten Schlüssel |
| `singleSignatureLeaksKeyBecauseNonceEqualsKey` | r = x(W), also **k = d**. **Eine** Signatur genügt: d = z / (s − r) |

Mathematik (n = Gruppenordnung, z = SHA-256(Nachricht)):

```
r = x(k·G) mod n         s = k⁻¹ · (z + r·d) mod n

gleiches k, zwei Nachrichten:   k = (z1 − z2) / (s1 − s2)      d = (s1·k − z1) / r
k = d (hier):                   s·d = z + r·d   →   d = z / (s − r)
```

**Das ist eine Eigenschaft des Simulators, nicht des Applets.** Auf einer echten Karte
kommt k aus einem zertifizierten Zufallszahlengenerator.

### Was man daraus lernt

- **„Der private Schlüssel verlässt die Karte nie“ reicht nicht.** Schlechter Zufall bei
  der Signatur verrät ihn trotzdem, mathematisch und ohne die Karte anzugreifen.
- **Reale Vorfälle:**
  - Sony PlayStation 3 (2010): feste ECDSA-Nonce, Signaturschlüssel berechnet.
  - Android-Bitcoin-Wallets (2013): fehlerhafter `SecureRandom`, wiederholte Nonces.
  - **ROCA (2017, CVE-2017-15361):** fehlerhafte RSA-Schlüsselerzeugung in einer
    Infineon-Bibliothek, betroffen u. a. Smartcards und TPMs (bekanntester Fall: die
    estnische ID-Karte). On-Card-Erzeugung verlagert das Vertrauen auf die
    Schlüsselerzeugung der Karte.
- **Gegenmaßnahmen:**
  - Zufallszahlengeneratoren nach **BSI AIS 20/31** bewerten (Teil der CC-Evaluierung).
  - **Deterministisches ECDSA (RFC 6979):** k wird aus d und Nachricht abgeleitet und ist
    unabhängig vom Zufall.
  - **Prüfungen auf CA-Seite:** öffentliche Schlüssel bei der Zertifikatsausstellung auf
    bekannte Schwächen (z. B. ROCA-Fingerprint) und **Duplikate** prüfen. Eine solche
    Duplikatprüfung hätte den Simulator-Fehler sofort aufgedeckt: Jede Karte hätte
    denselben Schlüssel eingereicht.

## Grenzen des Simulators (ergänzt)

- Zufall für Schlüsselerzeugung und Signatur ist deterministisch (siehe oben).
- Keine realistischen Laufzeiten. Auf echten Karten dauert P-256-Erzeugung typischerweise
  Sekundenbruchteile, RSA-2048 eher Sekunden.
- jCardSim gibt bei asymmetrischen Algorithmen Debug-Zeilen aus
  („getInstance of assymetric algo: 33“), das ist Rauschen.

## Merksatz

> Das Schlüsselpaar wird auf der Karte erzeugt, nur der öffentliche Schlüssel geht an die
> CA. Signiert wird auf der Karte nach PIN-Prüfung, und es gibt kein Kommando, das den
> privaten Schlüssel ausgibt. Diese Garantie steht und fällt aber mit der Qualität der
> Zufallszahlen: Eine wiederverwendete ECDSA-Nonce verrät den Schlüssel mathematisch.
> Deshalb gehören RNG-Bewertung (AIS 20/31), deterministisches ECDSA und Schlüsselprüfungen
> bei der CA zur Architektur.
