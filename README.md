# Java Card Playground

Lernprojekt, um Java Card praktisch zu verstehen: APDU-Abläufe, PIN-Prüfung,
Personalisierung, Verwaltung nach der Ausgabe und (folgt) On-Card-Schlüsselerzeugung.
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
| `src/test/java/playground/support/Card.java` | Testhelfer: Simulator, Installation, APDU-Trace |
| `src/test/java/playground/perso/DataPreparation.java` | spielt HSM und Personalisierungsmaschine |
| `src/test/java/playground/perso/IssuerHost.java` | spielt die Autorisierungszentrale nach der Ausgabe |

Applet-Code (`src/main`) wird mit Java 7 übersetzt, weil der spätere CAP-Converter das verlangt.
Testcode (`src/test`) ist Host-Code und nutzt Java 11.

## Dokumentation

| Dokument | Thema |
|---|---|
| [docs/01-apdu-grundlagen.md](docs/01-apdu-grundlagen.md) | APDU, Cases 1–4, Statuswörter, AID/RID, Applet-Lebenszyklus, Code von Übung 1 |
| [docs/02-pin-pruefung.md](docs/02-pin-pruefung.md) | VERIFY, OwnerPIN, Fehlbedienungszähler, persistent vs. transient, Speicherschutz |
| [docs/03-personalisierung.md](docs/03-personalisierung.md) | Personalisierungsphasen, Key Diversification, STORE DATA, Code-Walkthrough, Zustandsdiagramme |
| [docs/04-issuer-scripts.md](docs/04-issuer-scripts.md) | Verwaltung nach der Ausgabe mit K_SCRIPT, Replay-Schutz, Schlüsselwechsel |
| [docs/hintergrund-standards-und-plattformen.md](docs/hintergrund-standards-und-plattformen.md) | ISO 7816 / EMV / girocard, Karten-Betriebssysteme, Java Card vs. nativ |

## Grenzen des Simulators

jCardSim bildet die **Logik** ab (APDUs, Statuswörter, persistent vs. transient, Krypto-API),
aber nichts von der physischen Sicherheit einer echten Karte (Speicherverschlüsselung,
Sensoren, Seitenkanalschutz). Es gibt keine CAP-Konvertierung, keinen GlobalPlatform
Card Manager, keine Security Domains und keinen echten Secure Channel (SCP03).

## Ausblick

- Übung 3: ECC-P-256-Schlüsselpaar auf der Karte erzeugen, signieren, off-card verifizieren
- GlobalPlatform: Security Domains, Lebenszyklus, SCP03
- Übung 4: Transaktionen (`JCSystem.beginTransaction`) und Stromausfall
- Echte Karten: CAP-Build mit [ant-javacard](https://github.com/martinpaljak/ant-javacard),
  Laden mit [GlobalPlatformPro](https://github.com/martinpaljak/GlobalPlatformPro)
