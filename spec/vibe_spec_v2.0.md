# Formcycle-BayBIS-Connector (Vibe Spec v2.0)

## 1. Phasen-Strategie

**Phase I (AKTUELL): "Raw-XML Connector"**
Ein Plugin, das eine fertige XMeld-XML-Datei (Text) nimmt, sie an BayBIS sendet und die Antwort als JSON zurückgibt.
*   **Ziel:** Validierung der Transportstrecke (SOAP/Zertifikate) und des Response-Parsings ohne Komplexität der XML-Erstellung.

**Phase II (ZUKUNFT): "Form-to-XML Builder"**
Erweiterung des Plugins, um XMLs dynamisch aus Formulareingaben zu generieren.
*   **Status:** Gesperrt, bis Phase I erfolgreich läuft.

## 2. Phase I: Spezifikation (XML-to-JSON)

**Workflow im Formcycle:**
*   **Input:** Eine Textdatei (oder Prozessvariable), die den vollständigen, validen XML-Code (Nachricht 1332) enthält.
*   **Verarbeitung:** Plugin liest XML -> Base64 Encoding -> SOAP Request -> Base64 Decoding -> XML Parsing.
*   **Output:** Ein JSON-Objekt in einer Prozessvariable (z.B. `pvBayBisResult`), das die Trefferdaten für den weiteren Workflow nutzbar macht.

### Technische Komponenten (Phase I)

#### A. BayBisRawRequestAction (Plugin Entry Point)
*   **Funktion:** Implementiert `IFCWorkflowAction`.
*   **Parameter:**
    *   `XmlInputSource`: Name der Formcycle-Datei oder Variable mit dem XML-Content.
    *   `TargetVariable`: Name der Variable für das JSON-Ergebnis.
*   **Logik:**
    1.  Liest den String/Byte-Stream der XML-Datei.
    2.  Ruft `BayBisSoapClient` auf.
    3.  Ruft `XMeldResponseParser` auf.
    4.  Schreibt JSON in den Context.

#### B. BayBisSoapClient (Transport Layer)
*   **Input:** `byte[]` oder `String` (Das rohe XMeld XML).
*   **Aufgabe:**
    1.  Verpackt den Payload in den `callApplicationByte`-Wrapper.
    2.  Führt den SOAP-Call gegen den AKDB-Endpoint aus.
    3.  Fängt SOAP-Faults (z.B. "AuthorityKeys missing") ab und wirft saubere Java-Exceptions.
*   **Output:** `String` (Das dekodierte XMeld 1333 Antwort-XML).

#### C. XMeldResponseParser (Converter)
*   **Input:** `String` (XMeld 1333 XML).
*   **Aufgabe:**
    1.  Parsing der Nachricht (DOM oder JAXB).
    2.  Prüfung auf XMeld-Fehlercodes im Header (`xink:fehlermeldung`).
    3.  Extraktion der Trefferliste (`xmeld:ergebnis.trefferliste`).
*   **Output (JSON-Struktur):**
    ```json
    {
      "status": "SUCCESS",
      "trefferAnzahl": 1,
      "treffer": [
        {
          "nachname": "Sippl",
          "vorname": "Jasmin",
          "ags": "09462000",
          "strasse": "Joachimsthaler Str",
          "hausnummer": "6",
          "plz": "95447",
          "ort": "Bayreuth",
          "wohnungStatus": "F" // F=Hauptwohnung
        }
      ],
      "rawXml": "..." // Optional für Debugging
    }
    ```

## 3. Test-Daten & Validierung (für Phase I)

Nutzen Sie für Phase I ausschließlich das manuell korrigierte XML aus unserem Chatverlauf als Input-Datei.
*   **Test-Datei (Golden Master):** Speichern Sie das XML aus meiner letzten Antwort als `test-payload-1332.xml`.
*   **Wichtig:** Ersetzen Sie vor dem Testen die Platzhalter `HIER_DIE_BAYBIS_TEST_KENNUNG` und `HIER_IHRE_CLIENT_ID` mit Ihren echten AKDB-Zugangsdaten.

**Erwartetes Verhalten:**
*   Wenn das Plugin läuft, erhalten Sie ein JSON zurück.
*   Wenn "Code 16" kommt -> Das Input-XML im Formcycle prüfen (wurde es verändert?).
*   Wenn "AuthorityKeys" Fehler kommt -> SoapClient prüfen, ob Header korrekt gesetzt sind.

## 4. Prompting-Guide für Windsurf (Phase I Start)

Kopieren Sie dies in Ihren AI-Editor, um loszulegen:
"Wir starten Phase I des Formcycle-BayBIS-Connectors. Erstelle ein Java-Modul mit Maven.
**Schritt 1: Core-Logik** Implementiere eine Klasse `BayBisSoapClient`. Sie soll einen String `xmlContent` entgegennehmen, ihn Base64-kodieren und an den Mock-Endpunkt der AKDB `callApplicationByte` senden. Nutze `java.net.http.HttpClient` oder JAX-WS für den SOAP-Request. Der Response-Body (Base64) soll dekodiert und zurückgegeben werden.
**Schritt 2: Parsing** Erstelle einen `XMeldToJSONConverter`. Nutze `org.json` und `w3c.dom`. Der Parser soll aus einem XMeld 1333 String folgende Felder extrahieren: AGS (Gemeindeschlüssel), Strasse, Hausnummer, PLZ, Ort.
**Schritt 3: Formcycle Action** Erstelle die Klasse `BayBisRawAction` (implements `IFCWorkflowAction`). Sie soll eine Datei aus dem Workflow-Kontext lesen (Name konfigurierbar), an den Client übergeben und das JSON-Ergebnis in eine Prozessvariable schreiben.
**Referenz:** Nutze das angehängte PDF BayBIS-Testfälle.pdf, um zu verstehen, welche Daten in der Antwort (Typ 1333) zu erwarten sind."

## 5. Ausblick Phase II (GESPERRT)

Sobald Phase I stabil Daten liefert, wird ein XMeldBuilder entwickelt.
Dieser ersetzt die statische XML-Datei durch dynamische Generierung basierend auf Formularfeldern.
*   **Trigger:** Erfolgreicher Integrationstest von Phase I mit JSON-Output im Formcycle.
