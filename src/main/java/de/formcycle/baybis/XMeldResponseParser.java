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
            
            // Extract Hits (trefferliste)
            NodeList trefferNodes = doc.getElementsByTagNameNS("http://www.osci.de/xmeld2511a", "treffer"); // Adjust Namespace if version differs
            // Fallback for namespace flexibility if exact URI match fails (common issue)
            if (trefferNodes.getLength() == 0) {
                 trefferNodes = doc.getElementsByTagNameNS("*", "treffer");
            }

            resultJson.put("trefferAnzahl", trefferNodes.getLength());
            JSONArray hitsArray = new JSONArray();

            for (int i = 0; i < trefferNodes.getLength(); i++) {
                Element treffer = (Element) trefferNodes.item(i);
                JSONObject hitObj = new JSONObject();

                // Extract Person Data
                // Note: Structure is usually xmeld:treffer -> xmeld:natuerlichePerson
                
                // Name
                hitObj.put("nachname", getNestedValue(treffer, "familienname", "name"));
                hitObj.put("vorname", getNestedValue(treffer, "vornamen", "name"));
                
                // Address (Wohnung -> Anschrift)
                // Note: Often addresses are in a specific block. We try to find them deeply.
                hitObj.put("strasse", getDeepValue(treffer, "strasse"));
                hitObj.put("hausnummer", getDeepValue(treffer, "hausnummer"));
                hitObj.put("plz", getDeepValue(treffer, "postleitzahl"));
                hitObj.put("ort", getDeepValue(treffer, "ort"));
                
                // Additional Fields (AGS)
                // AGS is usually under 'gemeindeschluessel' inside 'wohnort' or 'anschrift'
                hitObj.put("ags", getDeepValue(treffer, "gemeindeschluessel"));
                
                // Wohnung Status (F/N)
                hitObj.put("wohnungStatus", getDeepValue(treffer, "statusWohnung"));

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
        return getTextContent(parent, tagName);
    }
}
