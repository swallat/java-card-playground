# 02 – PIN-Prüfung (Übung 2)

Code: [`PinApplet.java`](../src/main/java/playground/pin/PinApplet.java),
Test: [`PinAppletTest.java`](../src/test/java/playground/pin/PinAppletTest.java)

## Worum es geht

Eine PIN koppelt **Besitz** (Karte) an **Wissen** (PIN):

1. **Die Prüfung passiert auf der Karte.** Die PIN geht per `VERIFY` hin, die Karte
   vergleicht intern und antwortet nur ja/nein. Die Referenz-PIN verlässt die Karte nie.
2. **Ein Fehlbedienungszähler** begrenzt das Raten. Er ist **persistent**, sonst ließe er
   sich durch Ziehen der Karte zurücksetzen.
3. **Der Status „PIN verifiziert“ ist transient:** Er gilt nur bis Reset bzw. Deselect.

| SW | Bedeutung (ISO 7816-4) |
|---|---|
| `9000` | PIN korrekt |
| `63Cx` | PIN falsch, x Versuche übrig |
| `6983` | PIN gesperrt |
| `6982` | geschützte Funktion ohne vorherige PIN |

## Das VERIFY-Kommando

```
00 20 00 81 04 31 32 33 34
CLA INS P1 P2 Lc  "1234" (ASCII)
```

| Byte | Bedeutung |
|---|---|
| `00` | ISO-Kommando |
| `20` | VERIFY |
| `00` | P1 |
| `81` | P2 = Referenz der PIN (Karten können mehrere PINs haben) |
| `04` | Lc |
| `31 32 33 34` | PIN im Klartext (Case 3) |

`00 20 00 81` **ohne Daten** (Case 1) ist eine **Statusabfrage**, die keinen Versuch
verbraucht: `63C3` = nicht verifiziert, 3 Versuche. Middleware nutzt das, um vor der
Eingabe die Restversuche anzuzeigen.

## Die Abläufe im Trace

```
80 10 00 00 00                → 6982      ohne PIN gesperrt
00 20 00 81 04 31 32 33 34    → 9000      richtige PIN
80 10 00 00 00                → "Geheim" 9000

falsch → 63C2   falsch → 63C1   richtig → 9000       Zähler zurück auf 3
falsch → 63C2   falsch → 63C1   falsch → 63C0   richtig → 6983 (!)   gesperrt

falsch → 63C2  | RESET |  Status → 63C2      Zähler überlebt (persistent)
richtig → 9000 | RESET |  GET SECRET → 6982  Freischaltung weg (transient)
```

Nach der Sperre wird auch die **richtige** PIN abgewiesen, sonst könnte man weiterraten.

## Der Code

- **`OwnerPIN`** (Java-Card-API), angelegt im Konstruktor: `new OwnerPIN(3, 8)`, also
  3 Versuche und max. 8 Bytes, dazu `update(...)` mit der Initial-PIN.
- **`pin.check(...)`** ist sicherheitskritisch. Laut Spezifikation läuft es so ab:
  1. Validierungsflag auf `false`
  2. **Zähler dekrementieren** (persistent)
  3. erst dann vergleichen
  4. bei Treffer: Zähler zurück auf 3, Flag `true`

  Grund: Ein Angreifer könnte nach dem Vergleich, aber vor dem Herunterzählen den Strom
  kappen (**Tearing-Angriff**). Wenn erst gezählt und dann verglichen wird, kostet jeder
  Versuch garantiert einen Zähler.
- **Sperrprüfung vor `check()`:** `getTriesRemaining() == 0` → `6983`.
- **`deselect()`** ruft `pin.reset()` auf, damit kein anderes Applet eine offene Sitzung erbt.
- **Geschützte Funktion:** `if (!pin.isValidated()) throwIt(6982)`.
- `63Cx` und `6983` sind in `ISO7816` nicht als Konstanten definiert und werden selbst gesetzt.

## Muss man ISO-Kommandos selbst bauen?

**Bei Java Card: ja.** Die Runtime übernimmt nur `SELECT`, der GlobalPlatform Card Manager
die GP-Kommandos. **Alles andere implementiert das Applet in `process()`**, auch VERIFY.
Die API liefert nur Bausteine (`OwnerPIN`, `ISO7816`-Konstanten, Kryptoklassen).

Bei **nativen Karten-OS** (CardOS, STARCOS, SECCOS) sind die ISO-Kommandos eingebaut,
man konfiguriert nur PIN-Objekte und Zugriffsregeln.

In der Praxis nutzt man fertige Applets: zertifizierte PKI-Applets der Hersteller, oder
Open Source wie IsoApplet (ISO 7816 + PKCS#15), PivApplet, SmartPGP.

## Wie ist die PIN gespeichert?

Die Spezifikation schreibt das **Verhalten** vor, nicht den Speicherschutz. Der kommt vom
zertifizierten **Secure Controller** (Common Criteria, typisch EAL5+/6+, BSI-Schutzprofil
für Sicherheits-ICs):

| Schutz | gegen |
|---|---|
| Speicherverschlüsselung (chipindividuelle Schlüssel), Bus-Verwürfelung | Auslesen nach Abschleifen (Microprobing) |
| aktives Schild | physisches Abgreifen |
| Sensoren (Spannung, Takt, Temperatur, Licht) | Fault Injection (Glitch, Laser) |
| Fehlererkennung im Speicher | verfälschte Speicherinhalte |
| Seitenkanal-Gegenmaßnahmen | Strom- / EM-Analyse |

Dazu kommt eine **gehärtete PIN-Implementierung** der Plattform: Vergleich mit konstanter
Laufzeit, redundante Prüfungen, Dekrement vor Vergleich. Seit Java Card 3.0.5 kann die
Plattform über `OwnerPINx` / `OwnerPINBuilder` gehärtete Varianten liefern.
**Regel: keine eigene PIN-Prüfung mit `Util.arrayCompare` bauen.**

„EEPROM“ ist im Kartenjargon der Sammelbegriff für persistenten Speicher. Moderne Chips
nutzen meist Flash.

**Chipkarte vs. HSM:** Ein HSM ist *tamper-responsive* (eigene Stromversorgung, löscht aktiv
bei Eindringen). Eine Chipkarte ist *tamper-resistant*: Ohne Strom kann sie nicht reagieren
und muss Angriffe durch Verschlüsselung, Abschirmung und Verschleierung unwirtschaftlich machen.

Im **Simulator** ist die PIN ein ungeschütztes Byte-Array im Java-Heap.

## Bezug zur Praxis

- **Smartcard-Logon (z. B. Dienstausweis):** Die Middleware (PKCS#11 / Windows-Minidriver)
  schickt `00 20 00 xx …`, erst danach erlaubt die Karte die Signatur für die Anmeldung.
- **EMV / girocard Offline-PIN:** ebenfalls VERIFY (INS `20`). P2 `80` = Klartext-PIN,
  `88` = verschlüsselte PIN. Die PIN steckt in einem PIN-Block (ISO 9564 Format 2).
- **Kontaktlos** wird der Kanal vorher abgesichert (z. B. PACE beim Personalausweis,
  allgemein Secure Messaging).

## Zum Selbstausprobieren

1. `PIN_TRY_LIMIT = 5` setzen: Welche Tests schlagen fehl und warum?
2. `CHANGE REFERENCE DATA` (`00 24 00 81`, Daten = alte + neue PIN) einbauen.
3. PUK (zweites `OwnerPIN`) + `RESET RETRY COUNTER` (`00 2C`) mit `pin.resetAndUnblock()`.

## Merksatz

> Die PIN wird per VERIFY an die Karte geschickt und dort gegen eine Referenz geprüft, die
> die Karte nie verlässt. Der Fehlbedienungszähler ist persistent und wird vor dem Vergleich
> dekrementiert (gegen Tearing). Nach drei Fehlversuchen kommt 6983. Die Freischaltung ist
> transient und verfällt beim Reset.
