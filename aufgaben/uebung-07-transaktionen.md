# Aufgabenzettel Übung 4 – Speicherarten und Transaktionen

**Lernziel:** Die drei Speicherarten sicher einordnen, Tearing-Schäden erkennen und
Atomarität selbst herstellen: per Journal und per atomarem Schreibzugriff.

**Material:** [`TxApplet.java`](../src/main/java/playground/tx/TxApplet.java),
[`TxAppletTest.java`](../src/test/java/playground/tx/TxAppletTest.java),
[docs/07-transaktionen.md](../docs/07-transaktionen.md)

**Ausführen:**
```bash
mise exec -- mvn test -Dtest=TxAppletTest
```

## Testbed

| | Datei |
|---|---|
| Applet-Kopie (Aufgaben 2, 3) | [`src/main/java/playground/aufgaben/uebung07/TxAufgabeApplet.java`](../src/main/java/playground/aufgaben/uebung07/TxAufgabeApplet.java) |
| Prüfung + Vorhersagen (Aufgaben 1, 4) | [`src/test/java/playground/aufgaben/uebung07/Uebung07Aufgaben.java`](../src/test/java/playground/aufgaben/uebung07/Uebung07Aufgaben.java) |

Der „Stromausfall“ wird per P2 = `01` mitten im Vorgang ausgelöst. Danach startet der Test
die Karte neu (Reset + SELECT). `select()` ruft `recoverJournal()` auf, das ist das
„Einschalten“.

```bash
mise exec -- mvn test -Dtest=Uebung07Aufgaben
```

## Musterlösung

Erst selbst versuchen! Die vollständige Lösung liegt im Paket `playground.loesungen.uebung07` und läuft im normalen Build (und in der CI) mit:

| | Datei |
|---|---|
| Lösungstest | [`Uebung07LoesungTest.java`](../src/test/java/playground/loesungen/uebung07/Uebung07LoesungTest.java) |
| | [`TxLoesungApplet.java`](../src/main/java/playground/loesungen/uebung07/TxLoesungApplet.java) |

```bash
mise exec -- mvn test -Dtest=Uebung07LoesungTest
```

---

## Aufgabe 1: Speicherarten vorhersagen (10 Min.)

Sage für die drei Zähler (persistent, `CLEAR_ON_RESET`, `CLEAR_ON_DESELECT`) voraus, welche
Werte sie haben: nach 3 × INCREMENT, dann nach einem Applet-Wechsel, dann nach einem Reset.

<details><summary>Lösungshinweis</summary>

`{3, 3, 3}`, `{3, 3, 0}`, `{3, 0, 0}`.
</details>

## Aufgabe 2: Undo-Journal wiederherstellen (15 Min.)

Implementiere `recoverJournal()`: Wurde ein Vorgang angefangen, aber nicht abgeschlossen,
soll der gesicherte Datensatz zurückgeschrieben werden.

<details><summary>Lösungshinweis</summary>

```java
if (journalValid) {
    Util.arrayCopy(journalBackup, (short) 0, purse, (short) 0, RECORD_LEN);
    journalValid = false;
}
```
Zum Nachdenken: Warum `arrayCopy` und nicht `arrayCopyNonAtomic`? Was passiert, wenn der
Strom **während** der Wiederherstellung ausfällt? (Antwort: Das Flag ist noch gesetzt, also
wird beim nächsten Einschalten einfach noch einmal wiederhergestellt. Die Wiederherstellung
ist idempotent.)
</details>

## Aufgabe 3: Ein atomarer Schreibzugriff (10 Min.)

Ergänze in `MODE_ATOMIC_COPY` die Übernahme des im RAM vorbereiteten Datensatzes.

<details><summary>Lösungshinweis</summary>

`Util.arrayCopy(work, (short) 0, purse, (short) 0, RECORD_LEN);`
Ein Ausfall **vor** dieser Zeile ändert nichts, denn `work` liegt im RAM. Die Zeile selbst ist
laut Spezifikation atomar.
</details>

## Aufgabe 4: Simulator gegen echte Karte (5 Min.)

Welches Guthaben steht nach einer Abbuchung von 100 **mit JCRE-Transaktion** und
Stromausfall da, im Simulator und auf einer echten Karte?

<details><summary>Lösungshinweis</summary>

Simulator: 900, weil jCardSim Transaktionen nicht zurückrollt. Echte Karte: 1000, die
Runtime rollt beim nächsten Einschalten zurück.
</details>

## Aufgabe 5: Reihenfolge im Journal (Gedankenexperiment, 5 Min.)

Was wäre, wenn man im Journal **zuerst** `journalValid = true` setzt und **danach** den
Datensatz sichert, und genau dazwischen der Strom ausfällt?

<details><summary>Lösungshinweis</summary>

Beim Einschalten ist das Flag gesetzt, `journalBackup` enthält aber einen alten oder leeren
Stand. Die „Wiederherstellung“ würde gültige Daten mit Müll überschreiben. Darum gilt:
erst sichern, dann Flag.
</details>

---

## Kontrollfragen

1. Welche drei Speicherarten gibt es, und wann wird welche gelöscht?
2. Warum ist Tearing bei kontaktlosen Karten besonders relevant?
3. Was gehört in eine Transaktion, was nicht? Was passiert mit transienten Daten bei `abortTransaction()`?
4. Warum ist der Commit-Puffer begrenzt, und was folgt daraus für das Applet-Design?
5. Welche drei Wege zur Atomarität kennst du, und wann nimmt man welchen?
6. Wie hängt das mit `OwnerPIN.check()` aus Übung 2 zusammen?
