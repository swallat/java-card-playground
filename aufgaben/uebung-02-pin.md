# Aufgabenzettel Übung 2 – PIN-Prüfung

**Lernziel:** VERIFY und die PIN-Statuswörter sicher beherrschen, Fehlbedienungszähler und
persistent/transient verstehen, PIN-Verwaltung selbst erweitern.

**Material:** [`PinApplet.java`](../src/main/java/playground/pin/PinApplet.java),
[`PinAppletTest.java`](../src/test/java/playground/pin/PinAppletTest.java),
[docs/02-pin-pruefung.md](../docs/02-pin-pruefung.md)

**Ausführen:**
```bash
mise exec -- mvn test -Dtest=PinAppletTest
```

## Testbed

| | Datei |
|---|---|
| Applet-Kopie (Aufgaben 2, 3, 4) | [`src/main/java/playground/aufgaben/uebung02/PinAufgabeApplet.java`](../src/main/java/playground/aufgaben/uebung02/PinAufgabeApplet.java) |
| Prüfung + Vorhersagen (Aufgabe 1) | [`src/test/java/playground/aufgaben/uebung02/Uebung02Aufgaben.java`](../src/test/java/playground/aufgaben/uebung02/Uebung02Aufgaben.java) |

Die Kommandos `00 24` (Aufgabe 3) und `00 2C` (Aufgabe 4) sind schon verdrahtet, inklusive
Empfang und Längenprüfung. Du schreibst nur die Prüflogik. Aufgabe 1 geht von
`PIN_TRY_LIMIT = 3` aus, also Aufgabe 2 danach machen bzw. wieder zurückstellen.

```bash
mise exec -- mvn test -Dtest=Uebung02Aufgaben
```

## Musterlösung

Erst selbst versuchen! Die vollständige Lösung liegt im Paket `playground.loesungen.uebung02` und läuft im normalen Build (und in der CI) mit:

| | Datei |
|---|---|
| Lösungstest | [`Uebung02LoesungTest.java`](../src/test/java/playground/loesungen/uebung02/Uebung02LoesungTest.java) |
| | [`PinLoesungApplet.java`](../src/main/java/playground/loesungen/uebung02/PinLoesungApplet.java) |

Die Lösung nutzt `PIN_TRY_LIMIT = 5` (Aufgabe 2). Die Vorhersagen in Aufgabe 1 sind darauf angepasst.

```bash
mise exec -- mvn test -Dtest=Uebung02LoesungTest
```

---

## Aufgabe 1: Statuswörter vorhersagen (10 Min.)

Sage **vor** dem Lauf voraus, welche Statuswörter diese Folge liefert. Prüfe dann im Trace.

```
VERIFY falsch, VERIFY falsch, VERIFY (ohne Daten), VERIFY richtig, VERIFY (ohne Daten),
RESET, VERIFY (ohne Daten)
```

<details><summary>Lösungshinweis</summary>

`63C2`, `63C1`, `63C1` (Statusabfrage verbraucht keinen Versuch), `9000`, `9000`
(verifiziert), Reset, `63C3` (Zähler wieder voll durch den Erfolg, Freischaltung weg).
</details>

## Aufgabe 2: Fehlbedienungszähler auf 5 (5 Min.)

Setze `PIN_TRY_LIMIT = 5`. Welche Tests schlagen fehl, und warum?

<details><summary>Lösungshinweis</summary>

Alle Tests, die `63C2`/`63C1`/`63C0`/`6983` nach 1–3 Fehlversuchen erwarten, und die
Statusabfrage (`63C3` → `63C5`). Die Statuswörter kodieren die Restversuche.
</details>

## Aufgabe 3: PIN ändern (20 Min.)

Baue `CHANGE REFERENCE DATA` ein: `00 24 00 81 Lc <alte PIN || neue PIN>`. Vereinfachung:
beide PINs je 4 Bytes. Die neue PIN darf nur gesetzt werden, wenn die alte stimmt.

<details><summary>Lösungshinweis</summary>

`pin.check(buf, cdata, 4)` auf die alte PIN. Bei Fehler `63Cx`. Bei Erfolg
`pin.update(buf, cdata + 4, 4)`. Zum Nachdenken: Warum sollte auch dieser Versuch den
Fehlbedienungszähler belasten?
</details>

## Aufgabe 4: Entsperren mit PUK (Bonus, 25 Min.)

Füge ein zweites `OwnerPIN` als PUK hinzu (z. B. „12345678“, 10 Versuche) und das Kommando
`RESET RETRY COUNTER` (`00 2C 00 81 Lc <PUK>`). Bei korrekter PUK: `pin.resetAndUnblock()`.

<details><summary>Lösungshinweis</summary>

Testfolge: 3× falsch → `6983`, `00 2C` mit PUK → `9000`, VERIFY richtig → `9000`.
Auch die PUK hat einen eigenen Zähler. Ist sie gesperrt, ist die Karte endgültig gesperrt.
</details>

## Aufgabe 5: Gedankenexperiment Tearing (5 Min.)

Was wäre, wenn `check()` erst vergleicht und den Zähler nur bei Misserfolg verringert?

<details><summary>Lösungshinweis</summary>

Ein Angreifer misst den Stromverbrauch, erkennt den Fehlschlag und kappt die Versorgung,
bevor der Zähler geschrieben ist. Damit hätte er unbegrenzt viele Versuche.
</details>

---

## Kontrollfragen

1. Warum muss der Fehlbedienungszähler persistent, die Freischaltung aber transient sein?
2. Was unterscheidet `6982` von `6983`?
3. Wer implementiert VERIFY bei Java Card, wer bei CardOS/STARCOS?
4. Woher kommt der physische Schutz der PIN? Was davon bildet der Simulator ab?
5. Was bedeutet *tamper-resistant* im Gegensatz zu *tamper-responsive*?
