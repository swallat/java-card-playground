# Aufgabenzettel Zwischenübung – Personalisierung

**Lernziel:** Den Personalisierungsablauf (Vorpersonalisierung, Datenaufbereitung,
STORE DATA, Abschluss) nachvollziehen und die Schutzmechanismen durch gezielte Angriffe
verstehen.

**Material:** [`PersoApplet.java`](../src/main/java/playground/perso/PersoApplet.java),
[`DataPreparation.java`](../src/test/java/playground/perso/DataPreparation.java),
[`PersoAppletTest.java`](../src/test/java/playground/perso/PersoAppletTest.java),
[docs/03-personalisierung.md](../docs/03-personalisierung.md)

**Ausführen:**
```bash
mise exec -- mvn test -Dtest=PersoAppletTest
```

## Testbed

Eine schlanke Kopie der Personalisierung **ohne K_SCRIPT**, damit nichts ablenkt.

| | Datei |
|---|---|
| Applet-Kopie (Breakpoints Aufgabe 1, Schalter Aufgabe 3) | [`src/main/java/playground/aufgaben/uebung03/PersoAufgabeApplet.java`](../src/main/java/playground/aufgaben/uebung03/PersoAufgabeApplet.java) |
| Host-Seite (nichts ändern) | [`src/test/java/playground/aufgaben/uebung03/DataPreparationAufgabe.java`](../src/test/java/playground/aufgaben/uebung03/DataPreparationAufgabe.java) |
| Prüfung + Vorhersagen (Aufgaben 2, 3, 4) | [`src/test/java/playground/aufgaben/uebung03/Uebung03Aufgaben.java`](../src/test/java/playground/aufgaben/uebung03/Uebung03Aufgaben.java) |

- **Aufgabe 1:** Die Breakpoint-Stellen sind im Applet als `>>> AUFGABE 1a/1b/1c` markiert.
  Starte `aufgabe1_debuggerEinstieg` im Debugger.
- **Aufgabe 3:** ein einziger Schalter `TAG_IM_MAC` im Applet, den Karte **und** Host nutzen.

```bash
mise exec -- mvn test -Dtest=Uebung03Aufgaben
```

---

## Aufgabe 1: Ablauf mit dem Debugger (20 Min.)

Setze in IntelliJ Breakpoints in `PersoApplet` auf `install()` (Zeile mit `register`), auf
`cbcMac.init(...)` in `storeData()`, auf die Zeile nach `aesCbc.doFinal(...)` (PIN) und auf
`state = STATE_PERSONALIZED`. Debugge `fullPersonalizationThenUse`.

a) Welche Bytes stehen in `bArray`? Ordne AID, Privileges, Seriennummer, K_ENC, K_MAC zu.
b) Wie sieht die MAC-Eingabe in `scratch` für PIN und für den Namen aus? Warum ist sie
   einmal 32, einmal 16 Bytes lang?
c) Wo steht die PIN im Klartext, und was steht danach noch im Puffer?

<details><summary>Lösungshinweis</summary>

b) PIN: Tag + 16 Bytes Kryptogramm + Padding = 2 Blöcke. Name: Tag + 14 Bytes + `80` = 1 Block.
c) `scratch[0..4]` = `04 34 37 31 31`. Dahinter Reste der MAC-Eingabe (`C3 80`). Deshalb wird
der ganze Puffer genullt.
</details>

## Aufgabe 2: Falsche Karte (5 Min.)

Ändere in `dataPreparedForAnotherCardIsRejected` die „andere“ Seriennummer auf die echte.
Was passiert, und was lernst du daraus über Key Diversification?

<details><summary>Lösungshinweis</summary>

Der Test schlägt fehl: Die Karte nimmt die Daten an (`9000`). Die Daten passen genau dann,
wenn Seriennummer und damit Kartenschlüssel übereinstimmen.
</details>

## Aufgabe 3: Tag aus dem MAC nehmen (15 Min.)

Sage voraus, was passiert, wenn man das PIN-Kryptogramm mit Tag `02` (Name) einspielt.
Setze dann im Testbed `TAG_IM_MAC = false` und sage erneut voraus. (In der Referenz wäre die
Stelle `scratch[0] = tag` in `PersoApplet` plus die MAC-Berechnung in `DataPreparation`.)

<details><summary>Lösungshinweis</summary>

Mit Tag im MAC: `6982`. Ohne Tag im MAC: `9000`. Das PIN-Kryptogramm wird als „Name“
gespeichert, der Name wird zu unlesbarem Binärzeug. In der Referenz würde
`swappingTheTagIsRejected` rot. Allgemein: Was nicht im MAC ist, kann umetikettiert werden.
</details>

## Aufgabe 4: Replay vor dem Abschluss (15 Min.)

PIN „4711“ einspielen und dann **dasselbe PIN-APDU noch einmal** senden, vor dem Abschluss.
Was antwortet die Karte? Was würde ein Angreifer mit einem mitgeschnittenen PIN-APDU
erreichen? Im Testbed schreibst du nur die eine Zeile zum erneuten Senden.

<details><summary>Lösungshinweis</summary>

`9000`: Die Karte akzeptiert die Wiederholung, weil es keinen Zähler und keine
Sitzungsschlüssel gibt. Genau das verhindert ein GP Secure Channel (SCP03) mit Challenges.
</details>

## Aufgabe 5: Reihenfolge beim Abschluss (Gedankenexperiment, 5 Min.)

Was passiert bei Stromausfall, wenn man **erst** die Schlüssel löscht und **dann** den
Status umschaltet?

<details><summary>Lösungshinweis</summary>

Die Schlüssel sind weg, der Status steht noch auf PRE-PERSO. Die Karte nimmt kein gültiges
STORE DATA mehr an und ist unbrauchbar („gebrickt“). Die richtige Lösung ist eine
Transaktion (Übung 4).
</details>

---

## Kontrollfragen

1. Was ist auf dem Chip, bevor personalisiert wird, und was kommt verschlüsselt?
2. Wie hängen KMC, Seriennummer und K_ENC zusammen? Warum kann man aus K_ENC nicht auf KMC schließen?
3. Warum prüft die Karte den MAC, bevor sie entschlüsselt?
4. Warum ist der Name nur gemact und nicht verschlüsselt?
5. Welche Schlüssel werden beim Abschluss gelöscht, welche bleiben?
6. Was fehlt der Demo gegenüber SCP03?
