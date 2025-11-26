package de.formcycle.baybis;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Optional;

/**
 * Parses XMeld 1333 (Result) XML messages and converts the relevant data into JSON.
 */
public class XMeldResponseParser {

    /**
     * Parses the XMeld response string into a simplified JSON object.
     * 
     * @param xmeldResponse The raw XMeld 1333 XML string.
     * @return JSONObject containing status, hit count, and list of results.
     */
    public JSONObject parseResponse(String xmeldResponse) {
        JSONObject resultJson = new JSONObject();
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); // Important for xmeld: prefixes
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xmeldResponse)));

            // 1. Check for Errors in Header (xink:fehlermeldung)
            NodeList errorNodes = doc.getElementsByTagNameNS("*", "fehlermeldung");
            if (errorNodes.getLength() > 0) {
                resultJson.put("status", "ERROR");
                
                // Extract error details if available
                Element errorElement = (Element) errorNodes.item(0);
                // Simplification: Just take the text content or sub-elements if needed
                // Real XMeld errors have code and text.
                String code = getTextContent(errorElement, "code"); 
                String text = getTextContent(errorElement, "text");
                
                JSONObject errorObj = new JSONObject();
                errorObj.put("code", code);
                errorObj.put("message", text);
                resultJson.put("error", errorObj);
                
                return resultJson;
            }

            // 2. Parse Success Case
            resultJson.put("status", "SUCCESS");
            
            // Extract Persons from xmeld:auskunft -> xmeld:person
            NodeList personNodes = doc.getElementsByTagNameNS("http://www.osci.de/xmeld2511a", "person");
            // Fallback for namespace flexibility
            if (personNodes.getLength() == 0) {
                personNodes = doc.getElementsByTagNameNS("*", "person");
            }

            resultJson.put("trefferAnzahl", personNodes.getLength());
            JSONArray hitsArray = new JSONArray();

            for (int i = 0; i < personNodes.getLength(); i++) {
                Element person = (Element) personNodes.item(i);
                JSONObject hitObj = new JSONObject();

                // Extract Person ID
                String personId = getDeepValue(person, "identifikationsmerkmal");
                hitObj.put("id", personId);

                // Extract Name
                String nachname = getDeepValue(person, "nachname");
                String vorname = getDeepValue(person, "vornamen");
                String doktorgrad = getDeepValue(person, "doktorgrad");
                
                hitObj.put("nachname", nachname);
                hitObj.put("vorname", vorname);
                if (!doktorgrad.isEmpty()) {
                    hitObj.put("doktorgrad", doktorgrad);
                }
                
                // Extract Birth Data
                String geburtsdatum = getDeepValue(person, "geburtsdatum");
                if (!geburtsdatum.isEmpty()) {
                    hitObj.put("geburtsdatum", geburtsdatum);
                }
                
                // Extract Gender
                String geschlecht = getDeepValue(person, "geschlecht");
                if (!geschlecht.isEmpty()) {
                    hitObj.put("geschlecht", geschlecht);
                }
                
                // Extract Address (Wohnung -> Anschrift)
                String strasse = getDeepValue(person, "strasse");
                String hausnummer = getDeepValue(person, "hausnummer");
                String plz = getDeepValue(person, "postleitzahl");
                String ort = getDeepValue(person, "ort");
                
                if (!strasse.isEmpty() || !hausnummer.isEmpty() || !plz.isEmpty() || !ort.isEmpty()) {
                    JSONObject adresse = new JSONObject();
                    adresse.put("strasse", strasse);
                    adresse.put("hausnummer", hausnummer);
                    adresse.put("plz", plz);
                    adresse.put("ort", ort);
                    hitObj.put("adresse", adresse);
                }
                
                // Extract Status Flags
                String verzogen = getDeepValue(person, "verzogen");
                String verringerterDatenumfang = getDeepValue(person, "verringerterDatenumfang");
                
                if ("true".equals(verzogen)) {
                    hitObj.put("verzogen", true);
                }
                if ("true".equals(verringerterDatenumfang)) {
                    hitObj.put("verringerterDatenumfang", true);
                }
                
                // Extract Passport/ID Document Info
                NodeList ausweisNodes = person.getElementsByTagNameNS("*", "ausweisdokument");
                if (ausweisNodes.getLength() > 0) {
                    JSONArray ausweise = new JSONArray();
                    for (int j = 0; j < ausweisNodes.getLength(); j++) {
                        Element ausweis = (Element) ausweisNodes.item(j);
                        JSONObject ausweisObj = new JSONObject();
                        
                        String passart = getDeepValue(ausweis, "code");
                        String seriennummer = getDeepValue(ausweis, "seriennummer");
                        String gueltigkeitsdauer = getDeepValue(ausweis, "gueltigkeitsdauer");
                        String behoerde = getDeepValue(ausweis, "behoerde");
                        String ausstellungsdatum = getDeepValue(ausweis, "ausstellungsdatum");
                        
                        if (!passart.isEmpty()) ausweisObj.put("passart", passart);
                        if (!seriennummer.isEmpty()) ausweisObj.put("seriennummer", seriennummer);
                        if (!gueltigkeitsdauer.isEmpty()) ausweisObj.put("gueltigkeitsdauer", gueltigkeitsdauer);
                        if (!behoerde.isEmpty()) ausweisObj.put("behoerde", behoerde);
                        if (!ausstellungsdatum.isEmpty()) ausweisObj.put("ausstellungsdatum", ausstellungsdatum);
                        
                        if (ausweisObj.length() > 0) {
                            ausweise.put(ausweisObj);
                        }
                    }
                    if (ausweise.length() > 0) {
                        hitObj.put("ausweisdokumente", ausweise);
                    }
                }

                hitsArray.put(hitObj);
            }
            
            resultJson.put("treffer", hitsArray);
            resultJson.put("rawXml", xmeldResponse); // Optional, for debugging

        } catch (Exception e) {
            throw new BayBisConnectorException("Error parsing XMeld response: " + e.getMessage(), "PARSE_ERR", e);
        }
        
        return resultJson;
    }

    // Helper to safely get text content of a child element
    private String getTextContent(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagNameNS("*", tagName);
        if (list.getLength() > 0) {
            return list.item(0).getTextContent();
        }
        return "";
    }
    
    // Helper to get value from structure like <parent><child>value</child></parent>
    private String getNestedValue(Element parent, String parentTag, String childTag) {
        NodeList parents = parent.getElementsByTagNameNS("*", parentTag);
        if (parents.getLength() > 0) {
            Element p = (Element) parents.item(0);
            return getTextContent(p, childTag);
        }
        return "";
    }

    // Helper to search for a tag anywhere in the subtree (useful if exact path varies)
    private String getDeepValue(Element parent, String tagName) {
        String value = getTextContent(parent, tagName);
        return value != null ? value.trim() : "";
    }
}
