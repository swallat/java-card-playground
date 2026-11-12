# APDU-Cheatsheet

Alle in diesem Projekt verwendeten APDUs mit Bedeutung und Antworten. Details in [`docs/`](docs/).

## Aufbau

```
Command:   CLA INS P1 P2 [Lc Data] [Le]        Response:  [Data] SW1 SW2
```

| Case | Aufbau | Beispiel |
|---|---|---|
| 1 | Header | `80 7F 00 00` |
| 2 | Header + Le | `80 01 00 00 00` (Le `00` = 256) |
| 3 | Header + Lc + Data | `00 20 00 81 04 31 32 33 34` |
| 4 | Header + Lc + Data + Le | `80 02 00 00 03 AA BB CC 00` |
| 2E/3E/4E | Extended: `00` + Lc/Le mit 2 Bytes | `00 46 80 01 00 00 00` (Le = 65536) |

CLA: `00` ISO, `80` proprietär, `84` proprietär mit Secure Messaging, `10`-Bit = Command Chaining („es folgt mehr“).

## ISO / GlobalPlatform-Standardkommandos

| APDU | Kommando | Antworten |
|---|---|---|
| `00 A4 04 00 Lc <AID>` | SELECT per AID | `9000`; unbekannte AID: `6A82` (ISO) bzw. `6999` (jCardSim) |
| `00 20 00 81 Lc <PIN>` | VERIFY | `9000`, `63Cx` (x Versuche), `6983` (gesperrt) |
| `00 20 00 81` | VERIFY ohne Daten = Statusabfrage | `9000` (verifiziert) oder `63Cx` |
| `00 24 00 81 Lc <alt‖neu>` | CHANGE REFERENCE DATA | `9000`, `63Cx` |
| `00 2C 00 81 Lc <PUK>` | RESET RETRY COUNTER | `9000`, `63Cx` |
| `00 46 80 00 00` | GENERATE ASYMMETRIC KEY PAIR (ISO 7816-8) | `04‖X‖Y` (P-256: 65 Bytes) |
| `00 46 81 00 00` | öffentlichen Schlüssel lesen | `04‖X‖Y` |
| `00 2A 9E 9A Lc <Daten> 00` | PSO: COMPUTE DIGITAL SIGNATURE (Daten) | DER-Signatur |
| `00 2A 9E 90 20 <Hash> 00` | PSO: COMPUTE DIGITAL SIGNATURE (Hash, Testbed) | DER-Signatur |
| `00 2A 80 86 <Chiffrat>` | PSO: DECIPHER (hier ML-KEM-Decapsulation) | 32 Bytes Schlüsselbestätigung |
| `00 C0 00 00 Le` | GET RESPONSE | nächster Teil, `61xx` = noch xx Bytes |
| `80 CA 9F 7F 00` | GET DATA (CPLC-Tag, hier Seriennummer) | 8 Bytes |
| `80 E2 <Tag> 00 Lc <Daten‖MAC>` | STORE DATA (Personalisierung) | `9000`, `6982` (MAC), `6985` (Zustand) |
| `80 50 KVN 00 08 <Host-Challenge> 00` | INITIALIZE UPDATE (SCP03) | 29 Bytes: Div.-Daten, `30 03 70`, Karten-Challenge, Karten-Kryptogramm |
| `84 82 SL 00 10 <Host-Krypt.‖C-MAC>` | EXTERNAL AUTHENTICATE | `9000`, `6300` (Kryptogramm/MAC falsch) |
| `84 F2 80/40 00 Lc 4F 00 <C-MAC> 00` | GET STATUS (ISD / Anwendungen) | `L‖AID‖Lebenszyklus‖Privilegien` je Eintrag |
| `84 F0 80 <Zustand> 08 <C-MAC>` | SET STATUS (Kartenlebenszyklus) | `9000`, `6985` (Übergang verboten) |

## Projekteigene Kommandos

| APDU | Applet | Bedeutung |
|---|---|---|
| `80 01 00 00 00` | Hello | „Hello Card“ |
| `80 02 00 00 Lc <Daten> 00` | Hello | Echo |
| `80 10 00 00 00` | Pin | GET SECRET (nur nach PIN) |
| `80 12 00 00 00` | Perso | GET HOLDER (nur nach PIN) |
| `84 24 00 81 22 <Zähler‖PIN-Block‖MAC>` | Perso | Issuer Script: PIN setzen/entsperren |
| `84 D8 <Version> 00 22 <…>` | Perso | Issuer Script: PUT KEY (K_SCRIPT-Generation) |
| `84 1E 00 00 22 <…>` | Perso | Issuer Script: Anwendung sperren (Aufgabe) |
| `00 46 80 01/02/03 00 00 00` | PQC | ML-DSA-65 / ML-KEM-768 / ML-DSA-87 erzeugen |
| `80 10 / 80 12 / 80 14` | Tx | Zähler erhöhen / lesen / Abort-Demo |
| `80 30 P1 P2 02 <Betrag>` | Tx | Abbuchung, P1 = Schutzvariante, P2 `01` = simulierter Stromausfall |
| `80 32 00 00 00` | Tx | Börse lesen |

## STORE-DATA-Tags (Personalisierung)

| Tag | Inhalt | Schutz |
|---|---|---|
| `01` | PIN-Block | verschlüsselt (K_ENC) + MAC |
| `02` | Name | MAC |
| `03` | K_SCRIPT | verschlüsselt (K_ENC) + MAC |
| `7F` | Abschluss | MAC |

## Statuswörter

| SW | Bedeutung | wo gesehen |
|---|---|---|
| `9000` | OK | überall |
| `61xx` | noch xx Bytes abholbar (GET RESPONSE), `6100` = 256 oder mehr | PQC Short-APDUs |
| `6300` | Authentisierung fehlgeschlagen | SCP03 EXTERNAL AUTHENTICATE |
| `63Cx` | PIN falsch, x Versuche übrig | VERIFY |
| `6700` | falsche Länge | |
| `6883` | letztes Kommando der Kette erwartet | unterbrochenes Command Chaining |
| `6982` | Sicherheitsstatus nicht erfüllt | ohne PIN, MAC falsch, ohne Secure Channel |
| `6983` | Authentisierungsmethode gesperrt | PIN nach 3 Fehlversuchen |
| `6985` | Benutzungsbedingungen nicht erfüllt | falscher Lebenszyklus, Replay (Zähler), verbotener Übergang |
| `6988` | Secure-Messaging-Daten falsch | Issuer-Script-MAC |
| `6999` | Applet-Auswahl fehlgeschlagen | jCardSim bei unbekannter AID |
| `6A80` | falsche Daten | |
| `6A81` | Funktion nicht unterstützt | offene Aufgaben im Testbed |
| `6A82` | Datei/Anwendung nicht gefunden | ISO bei unbekannter AID |
| `6A86` | P1/P2 falsch | Schlüssel-Rollback bei PUT KEY |
| `6A88` | referenzierte Daten nicht gefunden | unbekannte KVN |
| `6Cxx` | falsches Le, richtig wäre xx | echte Karte (jCardSim ignoriert Le) |
| `6D00` | INS nicht unterstützt | |
| `6E00` | CLA nicht unterstützt | |
| `6F00` | unbestimmter Fehler (Exception im Applet) | |
| `6F01` | (projektspezifisch) simulierter Stromausfall | Tx |

## Wichtige AIDs

| AID | Anwendung |
|---|---|
| `A0 00 00 01 51 00 00 00` | GlobalPlatform Issuer Security Domain |
| `A0 00 00 03 59 10 10 02 80 01` | girocard |
| `A0 00 00 00 03 …` / `A0 00 00 00 04 …` | Visa / Mastercard |
| `E8 07 04 00 7F 00 07 03 02` | eID-Anwendung Personalausweis |
| `F0 00 00 00 01 xx` | Projekt-Applets (F = proprietär, nicht registriert) |
