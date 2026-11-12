# 01 – APDU-Grundlagen (Übung 1)

Code: [`HelloApplet.java`](../src/main/java/playground/hello/HelloApplet.java),
Test: [`HelloAppletTest.java`](../src/test/java/playground/hello/HelloAppletTest.java)

## Worum es geht

Die Karte ist **passiv**: Sie tut nichts von sich aus, sondern beantwortet nur Kommandos
des Terminals. Jedes Kommando ist eine **Command APDU**, jede Antwort eine **Response APDU**
(Daten + 2 Byte Statuswort). Das Prinzip ähnelt einem HSM: definierte Kommandos rein,
Ergebnisse raus, Schlüssel bleiben drin.

## APDU = Application Protocol Data Unit

Die Nachrichteneinheit der **Anwendungsschicht** nach **ISO/IEC 7816-4**. Kommando und
Antwort treten immer als Paar auf, und die Karte antwortet genau einmal pro Kommando.

| | Richtung | Aufbau |
|---|---|---|
| Command APDU | Host → Karte | `CLA INS P1 P2 [Lc Data] [Le]` |
| Response APDU | Karte → Host | `[Data] SW1 SW2` |

Analogie zum Funktionsaufruf:

| Feld | entspricht |
|---|---|
| CLA | Namensraum (`00` = ISO-Standard, `80` = proprietär) |
| INS | Funktionsname |
| P1, P2 | kleine Parameter (je 1 Byte) |
| Lc + Data | Argument (Byte-Array), Lc = Length of command data |
| Le | erwartete maximale Antwortlänge (Length expected) |
| Data der Antwort | Rückgabewert |
| SW1 SW2 | Return-Code (`9000` = OK) |

### APDU vs. Übertragung

```
Anwendung:   APDU  (ISO 7816-4)            ← sehen Applet und Host-Software
Transport:   T=0 / T=1 (ISO 7816-3)        kontaktbehaftet
             oder ISO 14443-4              kontaktlos
Physik:      Kontakte bzw. Funkfeld
```

Auf der Transportschicht heißen die Pakete TPDU. T=0 ist byteorientiert und liefert
Antwortdaten indirekt (`61xx`, dann `GET RESPONSE`). T=1 und ISO 14443-4 transportieren
die APDU am Stück. **Dieselbe APDU funktioniert kontaktbehaftet und kontaktlos.**

### Short vs. Extended Length

| | Lc / Le | max. Daten |
|---|---|---|
| Short | je 1 Byte | 255 rein, 256 raus |
| Extended | 2–3 Byte | ca. 64 KB |

Klassische Signaturen passen in Short APDUs (ECDSA P-256 ca. 72 Byte, RSA-2048: 256 Byte).
**PQC** wie ML-DSA-65 (Signatur ca. 3,3 KB) braucht Extended Length oder Command Chaining,
und das müssen Karte, Leser und Middleware unterstützen.

## Die vier APDU-Cases

Nach den 4 Header-Bytes entscheidet die **Länge des Rests**, was ein Byte bedeutet:

| Was nach dem Header kommt | Case | Bedeutung |
|---|---|---|
| nichts | 1 | weder Daten noch Antwort |
| genau 1 Byte | 2 | dieses Byte ist **Le** |
| 1 Byte + so viele Datenbytes, wie es angibt | 3 | Lc + Data |
| Lc + Data + noch 1 Byte | 4 | Lc + Data + Le |

Le = `00` bedeutet **256**. „0 Bytes erwartet“ wäre sinnlos, man lässt Le dann weg.
Le ist ein **Maximum**: Die Karte darf weniger liefern. Ist die Antwort größer als Le,
meldet die Karte typischerweise `6Cxx` („richtige Länge ist xx“).

**Lehrreicher Fehler aus dieser Übung:** Ein Case-3-APDU (ohne Le), auf das das Applet
Daten zurückschicken wollte, endete mit `6F00`. Wer Antwortdaten will, muss Le schicken
(Case 2 oder 4).

## Der Trace, Byte für Byte

```
--> 00 A4 04 00 06 F0 00 00 00 01 01      SELECT
<-- 90 00
```

| Byte | Feld | Bedeutung |
|---|---|---|
| `00` | CLA | ISO-Standardkommando |
| `A4` | INS | SELECT |
| `04` | P1 | Auswahl per Name (AID) |
| `00` | P2 | erstes / einziges Vorkommen |
| `06` | Lc | 6 Datenbytes |
| `F0 … 01` | Data | AID |

```
--> 80 01 00 00 00                        HELLO (Case 2, Le = 00 → bis 256 Bytes)
<-- 48 65 6C 6C 6F 20 43 61 72 64 9000    "Hello Card"

--> 80 02 00 00 03 AA BB CC 00            ECHO (Case 4: Lc=03, 3 Datenbytes, Le=00)
<-- AA BB CC 9000

--> 80 7F 00 00  → 6D00                   INS nicht unterstützt
--> 00 01 00 00  → 6E00                   CLA nicht unterstützt
```

## Statuswörter

| SW | Bedeutung |
|---|---|
| `9000` | OK |
| `6D00` | INS unbekannt |
| `6E00` | CLA unbekannt |
| `6F00` | unbestimmter Fehler (Exception im Applet) |
| `6A82` | Datei / Applet nicht gefunden (z. B. SELECT auf falsche AID) |
| `6700` | falsche Länge |
| `6Cxx` | falsches Le, richtig wäre xx |
| `63Cx` | PIN falsch, x Versuche übrig (Übung 2) |
| `6982` | Sicherheitsstatus nicht erfüllt (Übung 2) |
| `6983` | Authentisierungsmethode gesperrt (Übung 2) |
| `6985` | Benutzungsbedingungen nicht erfüllt (Personalisierung) |
| `6988` | Secure-Messaging-Daten falsch (Issuer Scripts) |

## AID, RID und PIX

Die **AID (Application Identifier)** ist der weltweit eindeutige Name einer Anwendung
(ISO/IEC 7816-4 und 7816-5), 5–16 Bytes:

```
 A0 00 00 03 59 | 10 10 02 80 01
 └──── RID ────┘ └──── PIX ────┘
   5 Bytes         0–11 Bytes
```

- **RID** (Registered Application Provider Identifier): Anbieter, vergeben von einer
  Registrierungsstelle nach ISO/IEC 7816-5. Vergleichbar mit einem registrierten OID-Arc.
- **PIX** (Proprietary Application Identifier Extension): vom Anbieter frei vergeben.

| Anfang | Kategorie |
|---|---|
| `A…` | international registriert |
| `D…` | national registriert (`D2 76` = Deutschland) |
| `E8…` | aus einer OID abgeleitet |
| `F…` | proprietär, nicht registriert (Tests, Eigenbau) |

Beispiele: Visa `A0 00 00 00 03…`, Mastercard `A0 00 00 00 04…`,
girocard `A0 00 00 03 59 10 10 02 80 01`,
GlobalPlatform Issuer Security Domain `A0 00 00 01 51 00 00 00`.

**RID und AID sind ISO-Begriffe, nicht Java-Card-spezifisch.** Sie gelten für alle
Chipkarten und haben bei EMV/girocard dieselbe Bedeutung. Bei EMV dient der RID zusätzlich
als Index für die CA-Schlüssel im Terminal (RID + CA Public Key Index).

In Java Card gibt es drei Arten von AIDs: **Package-AID** (geladener Code),
**Applet-Klassen-AID** und **Instanz-AID** (die, die man per SELECT anspricht).

SELECT-Varianten: P2 = `02` iteriert über Anwendungen mit gleichem AID-Präfix
(Partial Selection). EMV nutzt zusätzlich ein Verzeichnis (PSE/PPSE).

## Applet-Lebenszyklus

| Schritt | GlobalPlatform | Was im Chip passiert |
|---|---|---|
| Load | `INSTALL [for load]` + `LOAD` | CAP-Datei (Bytecode) in den Flash: nur **Code** |
| Install | `INSTALL [for install]` | `install()` legt die **Instanz** an und registriert sie |
| Make selectable | meist mit Install kombiniert | Applet per SELECT erreichbar |

- Aus einem Paket können **mehrere Instanzen** unter verschiedenen AIDs entstehen.
- `register()` trägt die Instanz mit ihrer AID in die Applet-Registry ein und gibt ihr ihren
  **Firewall-Kontext** (Applet-Isolation).
- Installation ist **alles oder nichts**: Scheitert `install()`, wird alles verworfen.
- **Objekte sind standardmäßig persistent.** Die VM wird nie beendet, sondern beim
  Ziehen der Karte nur pausiert. Lokale Variablen, Stack und APDU-Buffer liegen im RAM.
- Entfernen nur per GP `DELETE`. Ein Update heißt klassisch Löschen + Neuinstallieren,
  wobei die Daten verloren gehen.
- Im Simulator ist die „Karte“ ein Java-Objekt im Testprozess. `sim.reset()` simuliert
  „Karte raus und wieder rein“.

## Der Code von Übung 1

**Konstanten:** `byte` und `short` statt `int`, weil viele Karten kein 32-Bit-`int` haben.
`(byte) 0x80` ist nötig, weil Javas `byte` vorzeichenbehaftet ist (128 passt nicht, das
Bitmuster ist dasselbe wie −128). Strings gibt es nicht, nur Byte-Arrays.

**`install()`:** einmal beim Installieren. `new` + `register()`. Faustregel: **alle Objekte
im Konstruktor anlegen, nie in `process()`**, weil oft kein Garbage Collector vorhanden ist.

**`process()`:** pro APDU.
1. `selectingApplet()`: SELECT abfangen, normales `return` ergibt `9000`.
2. `apdu.getBuffer()`: **ein Buffer** (typisch 261 Bytes) für Kommando und Antwort.
   Feste Offsets: `OFFSET_CLA`=0, `INS`=1, `P1`=2, `P2`=3, `LC`=4, `CDATA`=5.
3. Fehler per `ISOException.throwIt(sw)`: bricht ab, die Runtime sendet das SW.
4. `switch` über INS: das „API“ der Karte.
5. Senden: Daten in den Buffer, dann `setOutgoingAndSend(offset, länge)`.
6. Empfangen: `setIncomingAndReceive()` holt die Datenbytes erst wirklich in den Buffer.

**`arrayCopy` vs. `arrayCopyNonAtomic`:** Atomar heißt, bei Stromausfall bleibt der alte
Inhalt vollständig erhalten. Das ist teuer und nur bei persistenten Zielen nötig. Ziel
APDU-Buffer (RAM) → `NonAtomic` reicht.

**Testseite (`Card.java`):** `CardSimulator` + `javax.smartcardio` (`CommandAPDU`,
`ResponseAPDU`). Das ist dieselbe API wie für echte PC/SC-Leser. Pro Test gibt es eine frische
Karte (`@Before`).

## Merksatz

> Eine Smartcard ist ein passiver Kommando-Empfänger. Jede Funktion ist eine APDU
> (CLA/INS/P1/P2/Lc/Data/Le), die Karte antwortet mit Daten und einem Statuswort.
> Ein Applet wird per AID selektiert und verarbeitet danach alle Kommandos.
