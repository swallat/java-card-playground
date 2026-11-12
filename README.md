# Java Card Playground

Lernprojekt, um Java Card praktisch zu verstehen: APDU-Abläufe, PIN-Prüfung,
Personalisierung, Verwaltung nach der Ausgabe sowie On-Card-Schlüsselerzeugung und Signatur.
Alles läuft im Simulator [jCardSim](https://github.com/licel/jcardsim), es wird keine Hardware gebraucht.

Zielplattform: **Java Card 3.0.5 Classic**.

## Voraussetzungen

- [mise](https://mise.jdx.dev/) installiert. JDK und Maven werden **nur für dieses Verzeichnis**
  über [`mise.toml`](mise.toml) bereitgestellt (Temurin 17, Maven 3.9), nichts global.
- Einmalig: `mise install`

## Tests ausführen

```bash
mise exec -- mvn test
```

Einzelne Testklasse oder einzelner Test:

```bash
mise exec -- mvn test -Dtest=PinAppletTest
mise exec -- mvn test -Dtest=HelloAppletTest#helloReturnsText
```

Jede APDU wird als Hex-Trace ausgegeben (`-->` Kommando, `<--` Antwort mit Statuswort).
Nach dem Lauf steht der Trace auch in `target/surefire-reports/*-output.txt`.

**IntelliJ:** Projekt als Maven-Projekt öffnen, JDK 17 aus mise als SDK wählen
(`%LOCALAPPDATA%\mise\installs\java\temurin-17…`). Tests lassen sich dann direkt starten und debuggen.

## Struktur

| Pfad | Inhalt |
|---|---|
| `src/main/java/playground/hello` | Übung 1: minimales Applet, eigene Kommandos |
| `src/main/java/playground/pin` | Übung 2: PIN-Prüfung mit Fehlbedienungszähler |
| `src/main/java/playground/perso` | Zwischenübung: Personalisierung und Issuer Scripts (K_SCRIPT) |
| `src/main/java/playground/sign` | Übung 3: ECC-P-256-Schlüsselpaar auf der Karte, ECDSA-Signatur |
| `src/test/java/playground/support/Card.java` | Testhelfer: Simulator, Installation, APDU-Trace |
| `src/test/java/playground/perso/DataPreparation.java` | spielt HSM und Personalisierungsmaschine |
| `src/test/java/playground/perso/IssuerHost.java` | spielt die Autorisierungszentrale nach der Ausgabe |
| `src/test/java/playground/pqc` | PQC-**Simulation** (ML-DSA, ML-KEM via BouncyCastle), nicht CAP-fähig |

Applet-Code (`src/main`) wird mit Java 7 übersetzt, weil der spätere CAP-Converter das verlangt.
Testcode (`src/test`) ist Host-Code und nutzt Java 11.

## Dokumentation

| Dokument | Thema |
|---|---|
| [docs/00-quintessenz.md](docs/00-quintessenz.md) | **Kernaussagen aller Übungen auf einen Blick** |
| [docs/01-apdu-grundlagen.md](docs/01-apdu-grundlagen.md) | APDU, Cases 1–4, Statuswörter, AID/RID, Applet-Lebenszyklus, Code von Übung 1 |
| [docs/02-pin-pruefung.md](docs/02-pin-pruefung.md) | VERIFY, OwnerPIN, Fehlbedienungszähler, persistent vs. transient, Speicherschutz |
| [docs/03-personalisierung.md](docs/03-personalisierung.md) | Personalisierungsphasen, Key Diversification, STORE DATA, Code-Walkthrough, Zustandsdiagramme |
| [docs/04-issuer-scripts.md](docs/04-issuer-scripts.md) | Verwaltung nach der Ausgabe mit K_SCRIPT, Replay-Schutz, Schlüsselwechsel |
| [docs/05-schluessel-und-signatur.md](docs/05-schluessel-und-signatur.md) | On-Card-Schlüsselerzeugung, ECDSA-Signatur, Off-Card-Prüfung, Nonce-Schwäche des Simulators |
| [docs/06-pqc-ml-dsa-ml-kem.md](docs/06-pqc-ml-dsa-ml-kem.md) | PQC-Variante: ML-DSA-65, ML-KEM-768, Extended Length APDUs, Auswirkungen auf Karten |
| [docs/hintergrund-standards-und-plattformen.md](docs/hintergrund-standards-und-plattformen.md) | ISO 7816 / EMV / girocard, Karten-Betriebssysteme, Java Card vs. nativ |

## Aufgabenzettel

Je Übung ein Zettel mit Lernziel, Aufgaben, Kontrollfragen und aufklappbaren Lösungshinweisen.
Dazu gibt es je Übung ein **Testbed** (`playground.aufgaben.uebungNN`): Applet-Kopien und eine
Prüfklasse `UebungNNAufgaben`, in denen die Eingabestellen mit `// >>> AUFGABE n` markiert sind.
Bei Beobachtungsaufgaben trägt man eine Vorhersage ein, der Test vergleicht sie mit der Karte.
Die Prüfklassen laufen **nicht** im normalen `mvn test` mit, sondern gezielt:

```bash
mise exec -- mvn test -Dtest=Uebung01Aufgaben
mise exec -- mvn test -Dtest='Uebung0*Aufgaben'
```

| Zettel | Übung |
|---|---|
| [aufgaben/uebung-01-apdu.md](aufgaben/uebung-01-apdu.md) | Übung 1 – APDU-Grundlagen |
| [aufgaben/uebung-02-pin.md](aufgaben/uebung-02-pin.md) | Übung 2 – PIN-Prüfung |
| [aufgaben/uebung-03-personalisierung.md](aufgaben/uebung-03-personalisierung.md) | Zwischenübung – Personalisierung |
| [aufgaben/uebung-04-issuer-scripts.md](aufgaben/uebung-04-issuer-scripts.md) | Zwischenübung – Issuer Scripts |
| [aufgaben/uebung-05-signatur.md](aufgaben/uebung-05-signatur.md) | Übung 3 – On-Card-Schlüssel und Signatur |
| [aufgaben/uebung-06-pqc.md](aufgaben/uebung-06-pqc.md) | Übung 3, Variante 2 – PQC |

## Grenzen des Simulators

jCardSim bildet die **Logik** ab (APDUs, Statuswörter, persistent vs. transient, Krypto-API),
aber nichts von der physischen Sicherheit einer echten Karte (Speicherverschlüsselung,
Sensoren, Seitenkanalschutz). Es gibt keine CAP-Konvertierung, keinen GlobalPlatform
Card Manager, keine Security Domains und keinen echten Secure Channel (SCP03).

Weitere beobachtete Abweichungen: jCardSim **ignoriert Le** bei `setOutgoingAndSend` (eine
echte Karte liefert z. B. `6C0A`) und antwortet auf SELECT einer unbekannten AID mit `6999`
statt des ISO-üblichen `6A82`.

**Achtung, Zufall:** jCardSim 3.0.6.0 erzeugt Schlüssel und ECDSA-Nonces aus einem
ungeseedeten Generator. Jede simulierte Karte bekommt denselben ersten Schlüssel, und eine
einzige Signatur verrät den privaten Schlüssel. Details und Beweis in
[docs/05-schluessel-und-signatur.md](docs/05-schluessel-und-signatur.md).

## Ausblick

- GlobalPlatform: Security Domains, Lebenszyklus, SCP03
- Übung 4: Transaktionen (`JCSystem.beginTransaction`) und Stromausfall
- Echte Karten: CAP-Build mit [ant-javacard](https://github.com/martinpaljak/ant-javacard),
  Laden mit [GlobalPlatformPro](https://github.com/martinpaljak/GlobalPlatformPro)
