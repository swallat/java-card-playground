# 07 – Speicherarten, Transaktionen und Stromausfall (Übung 4)

Code: [`TxApplet.java`](../src/main/java/playground/tx/TxApplet.java),
Test: [`TxAppletTest.java`](../src/test/java/playground/tx/TxAppletTest.java)

## Worum es geht

Eine Karte bekommt ihren Strom vom Leser. Wird sie aus dem Feld gezogen, endet die
Ausführung **mitten im Code**. Das ist Alltag, gerade kontaktlos. Daraus folgen zwei Fragen:

1. **Was überlebt?**
2. **Was passiert mit einer Änderung, die aus mehreren Schreibzugriffen besteht?**

## Teil A: Drei Speicherarten

| Art | Anlegen | Lebensdauer |
|---|---|---|
| persistent | Felder, `new` | überlebt alles (EEPROM/Flash) |
| transient `CLEAR_ON_RESET` | `JCSystem.makeTransient…Array(…, CLEAR_ON_RESET)` | bis Reset / Stromverlust (RAM) |
| transient `CLEAR_ON_DESELECT` | `…(…, CLEAR_ON_DESELECT)` | bis ein anderes Applet ausgewählt wird (RAM) |

Gemessen (zwei Mal INCREMENT, dann Applet-Wechsel, dann Reset):

```
nach 2 x INCREMENT       persistent 2   RESET 2   DESELECT 2
nach Applet-Wechsel      persistent 2   RESET 2   DESELECT 0
nach Reset               persistent 2   RESET 0   DESELECT 0
```

Einsatz: PIN-Freischaltung, Sitzungsschlüssel und Zwischenergebnisse gehören in transienten
Speicher, Guthaben, Zähler und Schlüssel in persistenten.

## Teil B: Tearing an einer Börse

Börse als Datensatz: **Guthaben ‖ Anzahl Buchungen ‖ Summe Buchungen**.
Invariante: **Guthaben + Summe Buchungen = 1000**. Eine Abbuchung besteht aus zwei
Schreibzugriffen: Guthaben senken, Buchung protokollieren.

Der „Stromausfall“ wird per Testschalter (P2 = `01`) mitten im Vorgang ausgelöst. Danach
wird die Karte neu gestartet (Reset + SELECT). Eine echte Karte würde gar nicht mehr
antworten. Hier antwortet sie mit `6F01`.

| P1 | Variante | Ergebnis nach Stromausfall |
|---|---|---|
| `00` | ohne Schutz | Guthaben 900, keine Buchung → **Invariante verletzt**, 100 Einheiten verschwunden |
| `01` | JCRE-Transaktion | echte Karte: Rollback → 1000. **Simulator: 900, kein Rollback** (siehe unten) |
| `02` | eigenes Undo-Journal | 1000, Rollback beim nächsten SELECT |
| `03` | ein atomarer Schreibzugriff | 1000, persistent wurde nichts geändert |

### JCRE-Transaktion (`01`)

```java
JCSystem.beginTransaction();
Util.setShort(purse, OFF_BALANCE, newBalance);
Util.setShort(purse, OFF_COUNT, newCount);
Util.setShort(purse, OFF_TOTAL, newTotal);
JCSystem.commitTransaction();     // erst hier wird alles gemeinsam gültig
```

- Alle **persistenten** Schreibzugriffe dazwischen gelten ganz oder gar nicht.
- Endet `process()` mit offener Transaktion (Exception) oder fällt der Strom aus, rollt die
  Runtime zurück, beim Stromausfall beim nächsten Einschalten.
- **Transiente Daten sind nicht Teil der Transaktion** und werden nie zurückgerollt.
- Die Runtime braucht dafür einen **Commit-Puffer** im persistenten Speicher, der begrenzt
  ist (`JCSystem.getMaxCommitCapacity()`). Große Änderungen in einer Transaktion können
  `TransactionException` (BUFFER_FULL) auslösen.
- Verschachtelte Transaktionen gibt es nicht: Ein zweites `beginTransaction()` wirft eine
  `TransactionException`.

### Eigenes Undo-Journal (`02`): so funktioniert der Commit-Puffer

```java
Util.arrayCopy(purse, 0, journalBackup, 0, 6);   // 1. alten Zustand sichern (atomar)
journalValid = true;                             // 2. "Vorgang läuft" (atomares Byte)
... beliebig viele Schreibzugriffe ...           // 3. ändern
journalValid = false;                            // 4. Commit
```
Beim nächsten SELECT („Einschalten“): Ist `journalValid` gesetzt, wird `journalBackup`
zurückkopiert. Die **Reihenfolge** ist entscheidend: Erst sichern, dann Flag setzen. Sonst
könnte ein Ausfall zwischen beiden Schritten eine halbe Sicherung „wiederherstellen“.

### Ein atomarer Schreibzugriff (`03`)

```java
// neuen Datensatz komplett im RAM (transient) vorbereiten ...
Util.arrayCopy(work, 0, purse, 0, 6);            // ... und mit EINEM atomaren Zugriff übernehmen
```
`Util.arrayCopy` ist laut Spezifikation atomar: entweder alle Bytes oder keine. Für kleine
Datensätze ist das das einfachste Muster. `arrayCopyNonAtomic` bietet diese Garantie nicht.

## Befund: Transaktionen wirken im Simulator nicht

Per `javap` geprüft (jCardSim 3.0.6.0): `beginTransaction`/`commitTransaction`/
`abortTransaction` setzen nur eine **Verschachtelungstiefe**. Es gibt **kein Journal und
keinen Rollback**.

- `abortTransaction()` lässt den persistenten Zähler auf 101 statt 1.
- Abbuchung mit Transaktion und Stromausfall: Guthaben 900 statt 1000.
- `getUnusedCommitCapacity()` liefert pauschal 32767.

Wer Tearing-Sicherheit testen will, braucht **echte Karten** bzw. Testwerkzeuge der
Hersteller und Prüflabore (gezielte Stromunterbrechung). In der Common-Criteria-Evaluierung
ist das ein Standard-Prüfpunkt.

## Bezüge

- **Übung 2 (PIN):** `OwnerPIN.check()` dekrementiert den Zähler vor dem Vergleich. Das ist
  derselbe Gedanke: Ein Abbruch darf nie zu einem günstigeren Zustand für den Angreifer führen.
- **Personalisierung:** Der Abschluss (Status umschalten + Schlüssel löschen) besteht aus
  mehreren Schreibzugriffen. Mit einer Transaktion wäre er atomar.
- **Banking:** Die GeldKarte und jede Offline-Börse leben von genau dieser Invariante.
  Verschwundenes oder doppeltes Geld durch Kartenziehen wäre ein Systemfehler.

## Merksatz

> Die Karte kann jederzeit stromlos werden, auch mitten im Code. Persistente Änderungen aus
> mehreren Schreibzugriffen müssen deshalb atomar sein: per JCRE-Transaktion (Commit-Puffer,
> begrenzt), per eigenem Journal oder durch einen einzigen atomaren `Util.arrayCopy`.
> Transiente Daten sind nie Teil einer Transaktion. Der Simulator bildet Transaktionen nicht
> ab, das muss auf echter Hardware geprüft werden.
