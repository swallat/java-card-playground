# Aufgabenzettel Übung 1 – APDU-Grundlagen

**Lernziel:** Eine APDU Byte für Byte lesen, die vier Cases unterscheiden, Statuswörter
deuten und ein eigenes Kommando in ein Applet einbauen.

**Material:** [`HelloApplet.java`](../src/main/java/playground/hello/HelloApplet.java),
[`HelloAppletTest.java`](../src/test/java/playground/hello/HelloAppletTest.java),
[docs/01-apdu-grundlagen.md](../docs/01-apdu-grundlagen.md)

**Ausführen:**
```bash
mise exec -- mvn test -Dtest=HelloAppletTest
```

## Testbed

Du arbeitest an einer **Kopie** des Applets, die Referenzlösung bleibt unberührt.

| | Datei |
|---|---|
| Applet (Aufgaben 3, 5) | [`src/main/java/playground/aufgaben/uebung01/HelloAufgabeApplet.java`](../src/main/java/playground/aufgaben/uebung01/HelloAufgabeApplet.java) |
| Prüfung + Eingaben (Aufgaben 1, 2, 4, 5) | [`src/test/java/playground/aufgaben/uebung01/Uebung01Aufgaben.java`](../src/test/java/playground/aufgaben/uebung01/Uebung01Aufgaben.java) |

Eingabestellen sind mit `// >>> AUFGABE n` … `// <<< AUFGABE n` markiert. Bei
Beobachtungsaufgaben trägst du **vorher** deine Vorhersage ein, der Test vergleicht sie mit
der Karte.

```bash
mise exec -- mvn test -Dtest=Uebung01Aufgaben
mise exec -- mvn test -Dtest=Uebung01Aufgaben#aufgabe3_eigenesKommando
```

## Musterlösung

Erst selbst versuchen! Die vollständige Lösung liegt im Paket `playground.loesungen.uebung01` und läuft im normalen Build (und in der CI) mit:

| | Datei |
|---|---|
| Lösungstest | [`Uebung01LoesungTest.java`](../src/test/java/playground/loesungen/uebung01/Uebung01LoesungTest.java) |
| | [`HelloLoesungApplet.java`](../src/main/java/playground/loesungen/uebung01/HelloLoesungApplet.java) |
| | [`HelloOhneSelectLoesungApplet.java`](../src/main/java/playground/loesungen/uebung01/HelloOhneSelectLoesungApplet.java) |

Für Aufgabe 5 gibt es eine eigene Variante ohne `selectingApplet()`.

```bash
mise exec -- mvn test -Dtest=Uebung01LoesungTest
```

---

## Aufgabe 1: Trace lesen (10 Min.)

Führe die Tests aus und öffne `target/surefire-reports/playground.hello.HelloAppletTest-output.txt`.

a) Zerlege `00 A4 04 00 06 F0 00 00 00 01 01` in CLA, INS, P1, P2, Lc, Data.
b) Welcher Case ist `80 01 00 00 00`? Was bedeutet das letzte Byte?
c) Welcher Case ist `80 02 00 00 03 AA BB CC 00`? Woran erkennst du das?

<details><summary>Lösungshinweis</summary>

a) CLA `00` (ISO), INS `A4` (SELECT), P1 `04` (per Name), P2 `00`, Lc `06`, Data = AID.
b) Case 2: Nach dem Header folgt genau ein Byte, also Le. `00` = bis 256 Bytes.
c) Case 4: `03` = Lc, danach genau 3 Datenbytes, dann noch ein Byte (Le).
</details>

## Aufgabe 2: Falsche AID (5 Min.)

Ändere in `HelloAppletTest` die AID beim SELECT (z. B. letztes Byte `02`). Welches
Statuswort kommt zurück? Ist es das ISO-übliche?

<details><summary>Lösungshinweis</summary>

jCardSim liefert **`6999`** (`ISO7816.SW_APPLET_SELECT_FAILED`, „Applet selection failed“),
die Java-Card-Runtime-Variante. Viele echte Karten bzw. ihr Card Manager antworten mit dem
ISO-üblichen `6A82` (Datei/Anwendung nicht gefunden). Die Antwort auf ein fehlgeschlagenes
SELECT kommt von der Runtime, nicht vom Applet.
</details>

## Aufgabe 3: Eigenes Kommando (15 Min.)

Baue `INS_VERSION = 0x03` ein. Es soll die zwei Bytes `01 00` zurückgeben. Schreibe einen
Test dazu.

<details><summary>Lösungshinweis</summary>

Neuer `case INS_VERSION:` im `switch`: `buf[0] = 0x01; buf[1] = 0x00;
apdu.setOutgoingAndSend((short) 0, (short) 2);`. Test mit `card.sendLe(0x80, 0x03, 0, 0, 256)`,
dann `9000` und Daten `{0x01, 0x00}` prüfen.
</details>

## Aufgabe 4: Le zu klein (5 Min.)

Sende HELLO mit `card.sendLe(0x80, 0x01, 0x00, 0x00, 5)`. Was passiert?

<details><summary>Lösungshinweis</summary>

jCardSim liefert trotzdem **alle 10 Bytes mit `9000`**: Der Simulator ignoriert Le bei
`setOutgoingAndSend`. Eine echte Karte (vor allem mit T=0) antwortet typischerweise `6C0A`
(„falsches Le, richtig wäre 10“). Das ist eine Grenze des Simulators: Le-Fehler fallen
erst auf echter Hardware auf.
</details>

## Aufgabe 5: SELECT nicht abfangen (5 Min.)

Entferne in `process()` den Block `if (selectingApplet()) return;`. Was passiert beim SELECT
und warum?

<details><summary>Lösungshinweis</summary>

Das SELECT-APDU (CLA `00`) läuft in die CLA-Prüfung und endet mit `6E00`, denn auch SELECT
landet in `process()`. In jCardSim ist das Applet **trotzdem ausgewählt**: Das folgende
HELLO liefert `9000`. Der Host sieht also einen Fehler, obwohl die Auswahl geklappt hat.
Genau deshalb fängt man SELECT ab.
</details>

---

## Kontrollfragen

1. Warum kann die Karte nie von sich aus Daten senden?
2. Was ist der Unterschied zwischen Lc und Le?
3. Warum braucht ein Case-3-Kommando, auf das Daten zurückkommen sollen, ein Le?
4. Wofür stehen RID und PIX, und ist der RID Java-Card-spezifisch?
5. Warum legt man in `process()` keine Objekte mit `new` an?
6. Wann reicht `Util.arrayCopyNonAtomic`, wann braucht man `Util.arrayCopy`?
