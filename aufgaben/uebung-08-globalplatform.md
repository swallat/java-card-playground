# Aufgabenzettel GlobalPlatform – Security Domains, Lebenszyklus, SCP03

**Lernziel:** Den SCP03-Handshake Byte für Byte verstehen, die Schutzwirkung (gegenseitige
Authentisierung, MAC-Kette) an Angriffen sehen, den Kartenlebenszyklus und die Privilegien
einer Security Domain lesen.

**Hinweis:** Die ISD ist eine Protokoll-Simulation (jCardSim hat keinen Card Manager).

**Material:** [`GpCardEmulator.java`](../src/test/java/playground/gp/GpCardEmulator.java),
[`Scp03Host.java`](../src/test/java/playground/gp/Scp03Host.java),
[`Scp03.java`](../src/test/java/playground/gp/Scp03.java),
[`GpCardTest.java`](../src/test/java/playground/gp/GpCardTest.java),
[docs/08-globalplatform.md](../docs/08-globalplatform.md)

**Ausführen:**
```bash
mise exec -- mvn test -Dtest=GpCardTest
```

## Testbed

| | Datei |
|---|---|
| Host-Kopie (Aufgabe 3) | [`src/test/java/playground/aufgaben/uebung08/Scp03HostAufgabe.java`](../src/test/java/playground/aufgaben/uebung08/Scp03HostAufgabe.java) |
| Prüfung + Vorhersagen (Aufgaben 1, 2, 4, 5) | [`src/test/java/playground/aufgaben/uebung08/Uebung08Aufgaben.java`](../src/test/java/playground/aufgaben/uebung08/Uebung08Aufgaben.java) |

```bash
mise exec -- mvn test -Dtest=Uebung08Aufgaben
```

---

## Aufgabe 1: INITIALIZE UPDATE zerlegen (10 Min.)

Führe `GpCardTest#initializeUpdateResponseStructure` aus und zerlege die Antwort: Wie lang
ist sie, wo steht die Key Version Number, welche SCP-Kennung folgt, wie lang ist die
Karten-Challenge?

<details><summary>Lösungshinweis</summary>

29 Bytes: Diversifizierungsdaten (10, Index 0–9), KVN an Index 10 (`30`), SCP-Kennung `03`,
Parameter i (`70`), Karten-Challenge (8), Karten-Kryptogramm (8).
</details>

## Aufgabe 2: Angriffe vorhersagen (10 Min.)

Sage die Statuswörter voraus:
a) GET STATUS ohne Secure Channel
b) ein korrektes MAC-geschütztes GET STATUS, das exakt so ein zweites Mal gesendet wird
c) das EXTERNAL AUTHENTICATE einer alten Sitzung, gesendet nach einem neuen INITIALIZE UPDATE

<details><summary>Lösungshinweis</summary>

a) `6982`. b) `6982`: Die MAC-Kette ist weitergelaufen, der alte MAC passt nicht mehr, und
die Sitzung wird geschlossen. c) `6300`: Neue Challenges ergeben neue Sitzungsschlüssel, das
alte Host-Kryptogramm ist wertlos.
</details>

## Aufgabe 3: Host-Kryptogramm berechnen (10 Min.)

Ergänze in `Scp03HostAufgabe` die Berechnung des Host-Kryptogramms.

<details><summary>Lösungshinweis</summary>

`byte[] hostCryptogram = Scp03.kdf(sMac, Scp03.DERIVE_HOST_CRYPTOGRAM, 64, context);`
Karten- und Host-Kryptogramm unterscheiden sich nur in der Konstante (`00` / `01`). Damit
beweist jede Seite der anderen, dass sie K-MAC kennt, ohne ihn zu übertragen.
</details>

## Aufgabe 4: Lebenszyklus (10 Min.)

Start in `OP_READY`. Sage die Statuswörter voraus für: → SECURED, → INITIALIZED,
→ CARD_LOCKED, → SECURED, → TERMINATED, danach INITIALIZE UPDATE.

<details><summary>Lösungshinweis</summary>

`6985` (INITIALIZED darf nicht übersprungen werden), `9000`, `6985` (sperren erst aus
SECURED), `9000`, `9000`, `6985` (TERMINATED ist endgültig).
</details>

## Aufgabe 5: Privilegien lesen (5 Min.)

Die ISD meldet die Privilegien `9E`. Welche der Privilegien Security Domain, DAP Verification,
Delegated Management, Card Lock und Card Terminate sind gesetzt?

<details><summary>Lösungshinweis</summary>

`9E` = `1001 1110`: Security Domain (`80`), Card Lock (`10`), Card Terminate (`08`),
außerdem Card Reset (`04`) und CVM Management (`02`). Nicht gesetzt: DAP Verification,
Delegated Management.
</details>

## Aufgabe 6: Transfer (Gedankenexperiment, 10 Min.)

Wie würde die Personalisierungs-Demo (`PersoApplet`) aussehen, wenn sie über die ISD mit
SCP03 statt mit eigenen K_ENC/K_MAC liefe? Welche Teile fielen weg?

<details><summary>Lösungshinweis</summary>

Das Applet bräuchte keine eigenen Transportschlüssel und keine eigene MAC-Prüfung. Die
Personalisierungsmaschine öffnet SCP03 zur ISD, schickt `INSTALL [for personalization]` und
dann `STORE DATA`. Die SD prüft MAC und Kette, entschlüsselt (C-DECRYPTION bzw. DEK) und
reicht die Daten an das Applet weiter (Interface `Application.processData`). Replay-Schutz
und gegenseitige Authentisierung kommen damit automatisch.
</details>

---

## Kontrollfragen

1. Wofür ist GlobalPlatform zuständig, wofür Java Card?
2. Was unterscheidet ISD und SSD? Wozu dient Delegated Management, wozu DAP?
3. Welche Kartenzustände gibt es, und welche Übergänge sind verboten?
4. Wie authentisieren sich Karte und Host gegenseitig bei SCP03, ohne Schlüssel zu übertragen?
5. Warum verhindert die MAC-Kette Replay und Umsortieren?
6. Was ist der erste Schritt mit einer neuen Entwicklerkarte, und warum?
