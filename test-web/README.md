# BayBIS Connector - Web Test Interface

A simple web interface for testing the BayBIS connector without Formcycle.

## Features

- Search by name (Vorname, Nachname) and birthdate
- Optional address search (Straße, Hausnummer, PLZ, Ort)
- Beautiful, modern UI
- Real-time JSON response display
- Formatted person cards with all details

## Quick Start

### Option 1: Using Python (Simplest)

1. Open terminal in the `test-web` folder
2. Run: `python -m http.server 8000`
3. Open browser: `http://localhost:8000`

**Note:** The servlet backend needs to be running separately (see Option 2 for full setup)

### Option 2: Full Java Setup (with Backend)

You need to set up a simple servlet container. Here's how:

#### Using Jetty (Recommended)

1. Download Jetty: https://www.eclipse.org/jetty/download.html
2. Extract to `test-web/jetty`
3. Copy the compiled classes to `test-web/jetty/webapps/baybis-test/WEB-INF/classes/`
4. Copy `index.html` to `test-web/jetty/webapps/baybis-test/`
5. Start Jetty: `java -jar jetty/start.jar`
6. Open: `http://localhost:8080/baybis-test/`

#### Using Tomcat

1. Download Tomcat: https://tomcat.apache.org/download-90.cgi
2. Create WAR file with servlet and HTML
3. Deploy to Tomcat webapps
4. Access at: `http://localhost:8080/baybis-test/`

### Option 3: Standalone Mode (HTML Only)

For quick testing without backend:

1. Open `index.html` directly in browser
2. Modify the fetch URL to point to your running servlet
3. Or use the command-line tool instead

## Test Data

Try these test persons from BayBIS:

### Jasmin Sippl
- **Vorname:** Jasmin
- **Nachname:** Sippl
- **Geburtsdatum:** 1973-02-02
- **Expected:** 4 persons found

### Barbara Carina Fischer
- **Vorname:** Barbara Carina
- **Nachname:** Fischer
- **Geburtsdatum:** 1992-02-02
- **Expected:** 2 persons found

## Architecture

```
┌─────────────┐         ┌──────────────────┐         ┌─────────────┐
│   Browser   │────────▶│  BayBisTestServlet│────────▶│   BayBIS    │
│ (HTML/JS)   │◀────────│   (Java Backend)  │◀────────│  (SOAP API) │
└─────────────┘  JSON   └──────────────────┘   XML    └─────────────┘
```

## Files

- `index.html` - Web interface (standalone, no dependencies)
- `BayBisTestServlet.java` - Backend servlet
- `README.md` - This file

## Notes

- This is **NOT** part of the Formcycle plugin
- For testing purposes only
- Uses the same BayBIS endpoint as the main connector
- Credentials: `ags:09000009` / `dbs:060030010000`

## Troubleshooting

**CORS Error:**
- The servlet includes CORS headers
- If still having issues, run browser with `--disable-web-security` (Chrome) for testing

**Connection Refused:**
- Make sure the servlet is running on port 8080
- Check firewall settings

**No Results:**
- Verify the person exists in BayBIS test database
- Try without address first
- Check the JSON response for error messages
