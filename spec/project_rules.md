# Project: Formcycle-BayBIS-Connector
# Phase: I (Raw-XML Connector)
# Tech Stack: Java 11+, Maven, JAXB, JAX-WS, org.json

# -----------------------------------------------------------------------------
# 1. CODING STANDARDS & ARCHITECTURE
# -----------------------------------------------------------------------------

## Java & Maven Best Practices
- **Java Version:** Nutze Java 11 oder höher Features (var, streams) wo sinnvoll.
- **Dependencies:** Halte `pom.xml` sauber. Nutze `org.json` für JSON und `javax.xml.ws` / `jakarta.xml.ws` für SOAP.
- **Null Safety:** Nutze `Optional<T>` anstatt null zurückzugeben. Prüfe Input-Parameter am Anfang von Methoden (`Objects.requireNonNull`).

## Logging (Crucial for Debugging)
- **Library:** Nutze `slf4j` oder `java.util.logging`.
- **Leveling:**
  - `INFO`: Start/Ende von Hauptprozessen (z.B. "Start BayBIS Anfrage für ID: ...").
  - `DEBUG`: Rohe XML-Payloads (Base64 decoded) und SOAP-Envelopes.
  - `ERROR`: Stacktraces bei SOAP-Faults oder Parsing-Fehlern.
- **PII-Schutz:** Logge NIEMALS echte Personendaten (Namen, Geburtsdaten) im INFO-Level. Maskiere sie im DEBUG-Level, falls möglich.

## Error Handling
- Fange niemals generische `Exception`. Fange spezifische `SOAPFaultException`, `JAXBException`, `IOException`.
- Wrappe technische Fehler in eine eigene `BayBisConnectorException` mit Fehlercode.
- Wenn BayBIS einen Fehler zurückgibt, parse den `xink:fehlermeldung` Block und gib ihn im JSON-Resultat zurück, statt das Plugin crashen zu lassen.

# -----------------------------------------------------------------------------
# 2. XMELD SPECIFIC RULES (Strict Compliance)
# -----------------------------------------------------------------------------

## XML Construction Rules
- **Namespace-Präfixe:** JEDES Element muss ein Präfix haben.
  - `xmeld:` (http://www.osci.de/xmeld2511a) für Payload-Elemente.
  - `xink:` für Header-Elemente.
  - `xian:`, `xida:`, `xig:` für Datentypen, wo im Header definiert.
- **Message 1332 (Freie Suche) Besonderheiten:**
  1. **Reihenfolge:** `<wohnung>` Block MUSS zwischen `<name>` und `<geburtsdaten>` stehen.
  2. **Adresse:** Innerhalb von `<anschrift.inland>` MUSS `<hausnummerOderHausnummernbereich>` das ERSTE Element sein.
  3. **Wrapper:** Verwende immer den Wrapper `hausnummerOderHausnummernbereich` für die Hausnummer, nie `hausnummer` direkt.
- **Validation:** Validiere generiertes XML immer gegen das Schema (XSD) bevor es gesendet wird, wenn möglich.

# -----------------------------------------------------------------------------
# 3. GIT & VERSION CONTROL
# -----------------------------------------------------------------------------

## Commit Messages (Conventional Commits)
Format: `<type>(<scope>): <subject>` 
- `feat(connector)`: Neue Funktionalität (z.B. SOAP Client Logik).
- `fix(xml)`: Korrektur an der XML-Struktur (z.B. Code 16 Fix).
- `docs(setup)`: Änderungen an der Dokumentation oder Readme.
- `chore(deps)`: Updates von Dependencies.
- `test(unit)`: Hinzufügen von Testfällen.

## Branching
- Arbeite auf Feature-Branches: `feature/phase1-xml-tunnel`.
- `main` Branch muss immer kompilierbar sein.

# -----------------------------------------------------------------------------
# 4. WORKFLOW & EXECUTION STEPS (For AI Agent)
# -----------------------------------------------------------------------------

Wenn du Code schreibst oder änderst, folge diesem Schritt-für-Schritt-Prozess:

1. **Analyse:** Verstehe, ob wir uns in Phase I (XML-Tunnel) oder Phase II (Builder) befinden. (Aktuell: Phase I).
2. **Implementation:**
   - **Step 1:** Erstelle/Update die Datenklassen.
   - **Step 2:** Implementiere die Logik. Achte peinlich genau auf die XMeld-Struktur-Regeln oben.
   - **Step 3:** Implementiere Logging an den Schnittstellen (Input/Output).
3. **Validierung:**
   - Erstelle einen JUnit Test.
   - Nutze die "Golden Master" XML-Datei (`test-payload-1332.xml`) als Mock-Input.
   - Verifiziere, dass keine "Code 16" Strukturfehler generiert wurden.
4. **Refactoring:** Prüfe auf doppelte Code-Fragmente und extrahiere Konstanten (z.B. Namespaces).

# -----------------------------------------------------------------------------
# 5. PROJECT SPECIFIC CONTEXT
# -----------------------------------------------------------------------------

- **Zielsystem:** BayBIS Testumgebung der AKDB.
- **Authentifizierung:** Erfolgt über Zertifikate (OSCI) oder im Test über Sender/Empfänger-IDs im Header.
- **Input Phase I:** Eine valide XML-Datei als String.
- **Output Phase I:** Ein JSON-Objekt mit extrahierten Trefferdaten (AGS, Adresse).
