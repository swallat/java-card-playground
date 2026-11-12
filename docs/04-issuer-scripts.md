# 04 – Verwaltung nach der Ausgabe: Issuer Scripts mit K_SCRIPT

Code: [`PersoApplet.java`](../src/main/java/playground/perso/PersoApplet.java) (`issuerScript()`, `deriveSessionKey()`),
[`IssuerHost.java`](../src/test/java/playground/perso/IssuerHost.java) (Autorisierungszentrale),
[`IssuerScriptTest.java`](../src/test/java/playground/perso/IssuerScriptTest.java).

## Ausgangsfrage

Wenn beim Abschluss der Personalisierung alle Schlüssel gelöscht würden, könnte der
Herausgeber die Karte nie wieder verwalten. Banken bzw. Autorisierungszentralen ändern
bei Bankkarten aber nachträglich PINs, Limits und Schlüssel. Dafür braucht es einen
**eigenen, dauerhaften Verwaltungsschlüssel**: hier **K_SCRIPT**.

## Das Vorbild: EMV Issuer Scripts

```
Karte ──ARQC──▶ Terminal ──▶ Netz ──▶ Autorisierungszentrale (HSM)
                                              │ prüft ARQC, entscheidet
Karte ◀──ARPC + Issuer Script──── Terminal ◀──┘
```

1. Online-Zahlung: Die Karte schickt ein Kryptogramm (ARQC) mit Transaktionszähler (ATC).
2. Die Autorisierungszentrale hängt an die Antwort ein **Issuer Script**: APDUs, die das
   Terminal nach der Transaktion ungelesen an die Karte durchreicht.
3. Die Skript-Kommandos sind mit **Secure Messaging** geschützt (CLA `84`).

Typische Inhalte: PIN ändern/entsperren, Anwendung sperren, Limits/Zähler (`PUT DATA`),
Datensätze (`UPDATE RECORD`), je nach Kartenprofil auch Schlüsselwechsel. Auf der Karte
liegen dafür kartenindividuelle Secure-Messaging-Schlüssel (bei EMV aus Issuer Master Key
und PAN abgeleitet). Daraus entstehen pro Skript Sitzungsschlüssel über den ATC.

Wie genau die girocard das umsetzt (welche Schlüssel per Skript tauschbar sind,
Versionierung, Kartenprofil der Deutschen Kreditwirtschaft), ist hier nicht im Detail
belegt. Das Prinzip ist der EMV-Weg.

**Gegenstück GlobalPlatform** (PKI-Karten, SIM): Die Security Domain behält den operativen
Schlüsselsatz (ENC/MAC/DEK, versioniert). Ein Verwaltungssystem öffnet einen SCP03-Kanal und
schickt `PUT KEY`, `INSTALL`/`DELETE`, `STORE DATA`. Bei SIM-Karten geschieht das sogar
per Funk (OTA).

## Umsetzung in der Demo

1. **Personalisierung:** STORE DATA Tag `03` bringt K_SCRIPT (Generation 1), verschlüsselt
   unter K_ENC (**Key Wrapping**). Der Abschluss löscht K_ENC/K_MAC, **K_SCRIPT bleibt**.
2. **Nach der Ausgabe** (nur im Zustand PERSONALIZED, nur mit CLA `84`):
   - `84 24 00 81`: neue PIN setzen. Setzt nebenbei den Fehlbedienungszähler zurück (**Entsperren**).
   - `84 D8 <Version> 00`: **PUT KEY**, K_SCRIPT durch die nächste Generation ersetzen.
3. **Schutz jedes Kommandos:**
   - Sitzungsschlüssel: `S_MAC = AES_K_SCRIPT(Zähler || 01 || 00…)`, `S_ENC = … || 02 || …`
   - MAC (mit S_MAC) über **Header (CLA INS P1 P2) || Zähler || Kryptogramm**
   - Nutzdaten (PIN-Block bzw. neuer Schlüssel) verschlüsselt mit S_ENC
   - Zähler muss **größer** sein als der zuletzt akzeptierte (Replay-Schutz)

```
84 24 00 81 22 │ 00 01 │ AD D7 8C 73 … CE F0 │ 80 AD A1 28 … 78 09 4A
CLA INS P1 P2 Lc│Zähler │ neue PIN, S_ENC     │ MAC, S_MAC
```

### Ablauf auf der Karte (`issuerScript()`)

1. `state != PERSONALIZED` → `6985`, CLA ≠ `84` → `6E00`, Länge ≠ 34 → `6700`
2. Sitzungsschlüssel aus K_SCRIPT und Zähler ableiten (in **transienten Schlüsselobjekten**,
   `TYPE_AES_TRANSIENT_DESELECT`)
3. MAC prüfen → `6988` (ISO: Secure-Messaging-Daten falsch)
4. Zähler prüfen → `6985`, dann **vor der Ausführung** persistent hochsetzen
   (ein Stromausfall verbraucht dann das Skript, ermöglicht aber kein Replay)
5. Mit S_ENC entschlüsseln, ausführen, Puffer nullen
6. Bei PUT KEY: neue Version muss höher sein als die alte → sonst `6A86`

## Schlüsselhierarchie

```
HSM des Herausgebers                       Karte
─────────────────────                      ─────────────────────────────
KMC_ENC, KMC_MAC   ─ableiten(Seriennr.)─▶  K_ENC, K_MAC   (gelöscht beim Abschluss)
KMC_SCRIPT_V1      ─ableiten(Seriennr.)─▶  K_SCRIPT Gen. 1 ┐
KMC_SCRIPT_V2      ─ableiten(Seriennr.)─▶  K_SCRIPT Gen. 2 ┘ ersetzt per PUT KEY
                                                │ ableiten(Zähler)
                                                ▼
                                           S_MAC, S_ENC  (transient, pro Kommando)
```

Drei Ebenen: **Master** (HSM, nie auf der Karte), **kartenindividuell** (persistent),
**Sitzung** (pro Kommando, nur RAM).

## Tests

| Szenario | Ergebnis | Mechanismus |
|---|---|---|
| neue PIN per Skript | `9000`, danach gilt 2580 statt 4711 | K_SCRIPT überlebt die Personalisierung |
| gesperrte PIN entsperren | `6983` → Skript → `9000` | `pin.update()` setzt den Zähler zurück |
| Replay desselben APDUs | `9000`, dann `6985` | Zähler nicht höher |
| 1 Bit gekippt | `6988` | MAC |
| PIN-Skript als PUT KEY umetikettiert | `6988` | Header im MAC |
| Skript vor Personalisierung | `6985` | Lebenszyklus |
| PUT KEY Gen. 1 → 2 | `9000` | neuer Schlüssel verschlüsselt unter dem alten |
| Host mit Gen. 1 nach Wechsel | `6988` | trotz hohem Zähler: Schlüssel veraltet |

Unterschiedliche Zähler ergeben unterschiedliche Kryptogramme. In der reinen
Personalisierungs-Demo war das PIN-APDU bei jedem Lauf identisch, das war die Replay-Lücke.

## Vereinfachungen gegenüber EMV

- **Zähler:** EMV nutzt den ATC der Karte (die Karte zählt selbst). Hier schickt der Host
  eine Sequenznummer mit. Replay-Schutz gibt es in beiden Fällen.
- **Schlüssel:** EMV nutzt zwei getrennte Kartenschlüssel (Integrität, Vertraulichkeit).
  Hier gibt es einen K_SCRIPT, aus dem zwei Sitzungsschlüssel mit verschiedenen Konstanten
  abgeleitet werden. Die Trennung nach Zweck bleibt erhalten.
- **Bestätigung:** `IssuerHost` stellt bei PUT KEY sofort um. Real wartet der Host auf die
  Bestätigung, sonst laufen Host und Karte auseinander, wenn das Skript verloren geht.

## Merksatz

> Nach der Ausgabe verwaltet der Herausgeber die Karte über einen eigenen,
> kartenindividuellen Verwaltungsschlüssel, der die Personalisierung überlebt. Jedes
> Kommando ist mit Sitzungsschlüsseln aus einem Zähler geschützt, das verhindert Replays.
> So lassen sich PINs entsperren und der Verwaltungsschlüssel selbst auf eine neue
> Generation umstellen: das Prinzip der EMV Issuer Scripts bzw. der GlobalPlatform-Verwaltung
> per Secure Channel.
