# Hintergrund: Standards, Kartenbetriebssysteme, Hersteller

## ISO/IEC 7816 als Fundament (Beispiel girocard)

```
┌──────────────────────────────────────────────────────────┐
│ Personalisierung durch die Bank                          │  Schlüssel, Zertifikate, Daten
├──────────────────────────────────────────────────────────┤
│ girocard-Spezifikation (Deutsche Kreditwirtschaft)       │  nationales Debit-Schema, AID,
│                                                          │  Online-PIN, CA-Hierarchie
├──────────────────────────────────────────────────────────┤
│ EMV (EMVCo, Books 1–4)                                   │  GPO, READ RECORD, GENERATE AC,
│                                                          │  SDA/DDA/CDA, CAPK
├──────────────────────────────────────────────────────────┤
│ ISO/IEC 7816-4, -5, -6                                   │  APDU, SW, SELECT, VERIFY,
│                                                          │  AID, BER-TLV
├──────────────────────────────────────────────────────────┤
│ ISO/IEC 7816-1/-2/-3   bzw.   ISO/IEC 14443              │  Physik, Kontakte, T=0/T=1
│ (kontaktbehaftet)             (kontaktlos)               │  bzw. Funk
└──────────────────────────────────────────────────────────┘
      darunter: Kartenbetriebssystem, z. B. Java Card + GlobalPlatform
```

Jede Schicht **übernimmt und profiliert** die darunterliegende.

| Teil | Inhalt |
|---|---|
| 7816-1, -2 | Format, Kontakte |
| 7816-3 | Elektrik, Reset, ATR, Protokolle T=0/T=1 |
| **7816-4** | APDU, Statuswörter, Basiskommandos, Dateisystem, Secure Messaging |
| 7816-5 | AID-Aufbau, Registrierung (RID/PIX) |
| 7816-6 | Datenobjekte/Tags (BER-TLV), z. B. EMV-Tags `5A`, `5F24` |
| 7816-8, -9, -15 | Sicherheitsoperationen (z. B. Signatur), Kartenverwaltung, Kryptoinformationen; wichtig für PKI-Karten |

ISO 14443 ersetzt nur die Funkschnittstelle. Darüber laufen dieselben 7816-4-APDUs.

**EMV:** Book 1 (Schnittstelle, Application Selection), Book 2 (Sicherheit,
Schlüsselmanagement, CAPK nach RID + Index), Book 3 (Kommandos wie `GENERATE AC`),
Book 4 (Terminal). Gleiches APDU-Format, CLA `80` für proprietäre Kommandos.

Bezug zu den Übungen: Übung 1 und 2 bewegen sich auf ISO 7816-4/-5 (VERIFY ist dasselbe
Kommando wie bei der EMV-Offline-PIN). Übung 3 entspricht konzeptionell ISO 7816-8
(`PERFORM SECURITY OPERATION`).

## Zwei Arten von Kartenbetriebssystemen

### Nativ, dateisystembasiert

Beispiele: **SECCOS** (Kreditgewerbe, Basis vieler girocards/GeldKarte), **STARCOS** (G+D),
**CardOS** (ursprünglich Siemens, heute Atos/Eviden), **TCOS** (T-Systems).

- OS fest im Chip, **ISO-Kommandos eingebaut**
- Anwendungen werden **konfiguriert**: DF mit AID, darin EFs für Daten, Schlüssel, PINs,
  jeweils mit Zugriffsbedingungen
- nach der Ausgabe kein neuer Code ladbar

### Offene Multi-Applikations-Plattformen

Beispiele: **Java Card**, **MULTOS**.

- virtuelle Maschine, Anwendungen sind **Programme** (Applets)
- Laden, Installieren, Löschen auch nach der Ausgabe, verwaltet über **GlobalPlatform**
- das Applet implementiert seinen Kommandosatz selbst

**Java Card ist eine Spezifikation, kein Produkt.** Implementierungen: NXP JCOP,
G+D Sm@rtCafé, Thales, Infineon u. a. Austauschbar ist der **Hersteller**, nicht die
**Sprache**: Applets werden in einer Java-Teilmenge geschrieben.

```
┌───────────────────────────────┐
│ Applets                       │
├───────────────────────────────┤
│ GlobalPlatform Card Manager   │  Laden, Install, Lifecycle, SCP
├───────────────────────────────┤
│ Java Card Runtime + API + VM  │  Spezifikation
├───────────────────────────────┤
│ natives Hersteller-OS         │  Speicher, Krypto-Treiber, I/O
├───────────────────────────────┤
│ Secure Controller (Hardware)  │  z. B. Infineon, NXP
└───────────────────────────────┘
```

Andere Sprachen auf anderen Plattformen: MULTOS (vor allem C → MEL-Bytecode),
BasicCard (BASIC-Dialekt), .NET Card (C#, eingestellt).

### Vergleich

| | Nativ | Java Card / MULTOS |
|---|---|---|
| Anwendung durch | Konfiguration | Programmierung |
| nach Ausgabe änderbar | praktisch nein | ja (GlobalPlatform) |
| Zertifizierung | Gesamtprodukt | Plattform und Applet getrennt |
| Leistung | schlank | VM-Overhead |
| Herstellerbindung | hoch | geringer |

Für das Terminal ist der Unterschied unsichtbar: Es sieht nur AIDs und APDUs.

## STARCOS

Natives OS von Giesecke+Devrient. Implementiert ISO 7816-4/-8/-9. Anwendungen entstehen
bei der Personalisierung (DFs, Schlüssel-/PIN-Dateien, Zugriffsregeln).

Einsatz (ohne Prüfung der Versionsdetails): Telematikinfrastruktur im Gesundheitswesen
(eGK, HBA, SMC-B auf STARCOS-3-Varianten), Signaturkarten, Banking.

Middleware: G+D **StarSign** (PKCS#11, Windows-Minidriver). G+D bietet daneben auch
Java Card (Sm@rtCafé) an.

## Lieferkette einer Chipkarte: Ebenen unterscheiden

„Die Karte kommt von Hersteller X“ ist meist zu ungenau. An einer Karte sind typischerweise
mehrere Firmen beteiligt:

| Ebene | typische Anbieter |
|---|---|
| Halbleiter-Chip (Secure Controller) | Infineon, NXP, ST |
| Kartenbetriebssystem | G+D (STARCOS), Eviden (CardOS), NXP (JCOP), … |
| Kartenkörper, Druck, optische Personalisierung | Kartenhersteller und Sicherheitsdruckereien |
| elektrische Personalisierung (Schlüssel, Zertifikate) | Personalisierungsdienstleister oder das Trust Center des Herausgebers |

An jeder Übergabe zwischen diesen Ebenen hängen Schlüssel: Transportschlüssel des
Chipherstellers, kartenindividuelle Schlüssel, Schlüssel der Security Domain. Wo die
Schlüsselhoheit liegt und wie Schlüssel übergeben werden (Key Ceremony, Transport unter
einem KEK), ist eine zentrale Architekturfrage.

### Architekturfragen für ein Kartensystem

- Natives Karten-OS oder Java Card, und wie geht man mit Applet-Updates im Kartenlebenszyklus um?
- Wie ist die Lieferkette aufgeteilt, und wo liegt die Schlüsselhoheit beim Übergang zur
  elektrischen Personalisierung?
- Gibt es eine kontaktlose Schnittstelle (z. B. Zutritt), getrennt von der PKI-Funktion?
- Wie läuft der Kartenwechsel bei Zertifikatsablauf oder neuen Algorithmen (PQC)?
- Welche Prüfungen laufen bei der Zertifikatsausstellung auf eingereichte Schlüssel?
