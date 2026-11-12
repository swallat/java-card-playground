# Aufgabenzettel Zwischenübung – Issuer Scripts (K_SCRIPT)

**Lernziel:** Verstehen, wie ein Herausgeber eine Karte nach der Ausgabe sicher verwaltet:
Verwaltungsschlüssel, zählerabhängige Sitzungsschlüssel, Replay-Schutz, Schlüsselwechsel.

**Material:** [`PersoApplet.java`](../src/main/java/playground/perso/PersoApplet.java)
(`issuerScript()`, `deriveSessionKey()`),
[`IssuerHost.java`](../src/test/java/playground/perso/IssuerHost.java),
[`IssuerScriptTest.java`](../src/test/java/playground/perso/IssuerScriptTest.java),
[docs/04-issuer-scripts.md](../docs/04-issuer-scripts.md)

**Ausführen:**
```bash
mise exec -- mvn test -Dtest=IssuerScriptTest
```

## Testbed

| | Datei |
|---|---|
| Prüfung + Eingaben (Aufgaben 1, 2, 3) | [`src/test/java/playground/aufgaben/uebung04/Uebung04Aufgaben.java`](../src/test/java/playground/aufgaben/uebung04/Uebung04Aufgaben.java) |
| Aufgabe 4 (Implementierung) | [`src/main/java/playground/perso/PersoApplet.java`](../src/main/java/playground/perso/PersoApplet.java), zwei Stellen `>>> AUFGABE 4 (Issuer Scripts, Teil 1/2)` |

Für Aufgabe 4 ist alles drumherum fertig: Kommando `84 1E`, Zustand `STATE_BLOCKED`,
Host-Methode `IssuerHost.applicationBlock()`. Die Personalisierung erledigt `setUp()`.

```bash
mise exec -- mvn test -Dtest=Uebung04Aufgaben
```

## Musterlösung

Erst selbst versuchen! Die vollständige Lösung liegt im Paket `playground.loesungen.uebung04` und läuft im normalen Build (und in der CI) mit:

| | Datei |
|---|---|
| Lösungstest | [`Uebung04LoesungTest.java`](../src/test/java/playground/loesungen/uebung04/Uebung04LoesungTest.java) |
| | [`PersoScriptLoesungApplet.java`](../src/main/java/playground/loesungen/uebung04/PersoScriptLoesungApplet.java) |

Die Lösung von Aufgabe 4 ist eine Kopie von `PersoApplet` mit APPLICATION BLOCK. Die Referenz bleibt das Gerüst.

```bash
mise exec -- mvn test -Dtest=Uebung04LoesungTest
```

---

## Aufgabe 1: Skript-APDU zerlegen (10 Min.)

Zerlege `84 24 00 81 22 00 01 AD D7 … CE F0 80 AD … 78 09 4A` aus dem Trace: CLA, INS, P1, P2,
Lc, Zähler, Kryptogramm, MAC. Warum ist Lc = `22`?

<details><summary>Lösungshinweis</summary>

CLA `84` (Secure Messaging), INS `24`, P1 `00`, P2 `81`, Lc `22` = 34 = 2 (Zähler)
+ 16 (Kryptogramm) + 16 (MAC). Zähler `00 01`.
</details>

## Aufgabe 2: Zähler zurückdrehen (10 Min.)

Schreibe einen Test: zwei Skripte senden (Zähler 1, 2), dann mit `issuer.setCounter(0)`
ein drittes bauen (Zähler 1). Was antwortet die Karte, und in welchem Schritt von
`issuerScript()` wird es abgewiesen?

<details><summary>Lösungshinweis</summary>

Der MAC ist gültig, weil der Host den korrekten K_SCRIPT kennt. Die Zählerprüfung schlägt
an: `6985`. Gültige Kryptografie allein reicht nicht, die Frische muss geprüft werden.
</details>

## Aufgabe 3: Schlüssel-Rollback (10 Min.)

Nach `putKey(2)`: Was passiert, wenn derselbe Host danach `putKey(1)` sendet?

<details><summary>Lösungshinweis</summary>

MAC und Zähler passen (der Host nutzt noch Generation 2 zum Schützen). Die Versionsprüfung
schlägt an: `6A86`. Ein Rückfall auf eine ältere, womöglich kompromittierte Generation ist
ausgeschlossen. Achtung: `IssuerHost` hat intern trotzdem auf Generation 1 umgestellt. Das
ist die Vereinfachung „Host wartet nicht auf Bestätigung“ aus der Doku.
</details>

## Aufgabe 4: Neues Skript-Kommando „Karte sperren“ (Bonus, 30 Min.)

Baue `84 1E 00 00` (APPLICATION BLOCK) fertig. Danach soll jedes VERIFY und GET HOLDER
`6985` liefern, Skripte sollen weiter funktionieren. Das Kommandoformat (16 Bytes
Dummy-Kryptogramm, MAC, Zähler) ist schon fertig. Du setzt nur den Zustand (Teil 1) und
lässt Skripte auch im gesperrten Zustand zu (Teil 2).

<details><summary>Lösungshinweis</summary>

Im Zustandsdiagramm kommt ein Übergang PERSONALIZED → BLOCKED dazu, ausgelöst per Skript.
Real würde man auch ein APPLICATION UNBLOCK vorsehen. So sperren Banken Karten nach einer
Verlustmeldung beim nächsten Online-Kontakt.
</details>

## Aufgabe 5: Header aus dem MAC nehmen (Gedankenexperiment, 5 Min.)

Welcher Angriff wäre möglich, wenn nur Zähler + Kryptogramm gemact würden?

<details><summary>Lösungshinweis</summary>

Ein gültiges PIN-Skript könnte als `PUT KEY` (INS `D8`, P1 = neue Version) eingespielt
werden. Die Karte würde den entschlüsselten PIN-Block als neuen K_SCRIPT übernehmen.
Der Test `changePinCannotBeTurnedIntoPutKey` deckt genau das ab.
</details>

---

## Kontrollfragen

1. Warum darf K_SCRIPT beim Abschluss der Personalisierung nicht gelöscht werden?
2. Wozu dienen die Sitzungsschlüssel, wenn K_SCRIPT schon kartenindividuell ist?
3. Was ist der Unterschied zwischen unserem Zähler und dem ATC bei EMV?
4. Wie gelangt ein Issuer Script bei einer girocard zur Karte?
5. Wie heißt das Gegenstück bei GlobalPlatform?
