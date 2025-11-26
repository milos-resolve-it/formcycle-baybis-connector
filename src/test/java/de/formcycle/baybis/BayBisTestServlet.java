package de.formcycle.baybis;

import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Simple test servlet for the BayBIS connector web interface.
 * This is NOT part of the plugin - only for testing purposes.
 */
@WebServlet("/search")
public class BayBisTestServlet extends HttpServlet {

    private static final String BAYBIS_ENDPOINT = "https://apk-int.akdb.de/okkommbis/services/XoevService";
    private static final String AGS_LESER = "ags:09000009";
    private static final String DBS_AUTOR = "dbs:060030010000";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Enable CORS for testing
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // Read request body
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = request.getReader()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            JSONObject requestData = new JSONObject(sb.toString());
            
            // Extract search parameters
            String vorname = requestData.getString("vorname");
            String nachname = requestData.getString("nachname");
            String geburtsdatum = requestData.getString("geburtsdatum");
            
            // Optional address
            String strasse = requestData.optString("strasse", "");
            String hausnummer = requestData.optString("hausnummer", "");
            String plz = requestData.optString("plz", "");
            String ort = requestData.optString("ort", "");
            
            boolean hasAddress = !strasse.isEmpty() || !hausnummer.isEmpty() || 
                                !plz.isEmpty() || !ort.isEmpty();

            // Build XMeld 1332 XML
            String xml = buildXMeldRequest(vorname, nachname, geburtsdatum, 
                                          hasAddress, strasse, hausnummer, plz, ort);

            // Send to BayBIS
            BayBisSoapClient client = new BayBisSoapClient(BAYBIS_ENDPOINT);
            String responseXml = client.sendRequest(xml);

            // Parse response
            XMeldResponseParser parser = new XMeldResponseParser();
            JSONObject result = parser.parseResponse(responseXml);

            // Return JSON
            PrintWriter out = response.getWriter();
            out.print(result.toString(2));
            out.flush();

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject error = new JSONObject();
            error.put("status", "ERROR");
            error.put("message", e.getMessage());
            
            PrintWriter out = response.getWriter();
            out.print(error.toString(2));
            out.flush();
        }
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private String buildXMeldRequest(String vorname, String nachname, String geburtsdatum,
                                     boolean hasAddress, String strasse, String hausnummer, 
                                     String plz, String ort) {
        
        String uuid = java.util.UUID.randomUUID().toString();
        String timestamp = java.time.ZonedDateTime.now().format(
            java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<xmeld:datenabruf.freieSuche.suchanfrage.1332\n");
        xml.append("    xmlns:xmeld=\"http://www.osci.de/xmeld2511a\"\n");
        xml.append("    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        xml.append("    version=\"25.11a\"\n");
        xml.append("    standard=\"XMeld\">\n");
        
        // Header
        xml.append("    <nachrichtenkopf.g2g>\n");
        xml.append("        <identifikation.nachricht>\n");
        xml.append("            <nachrichtenUUID>").append(uuid).append("</nachrichtenUUID>\n");
        xml.append("            <nachrichtentyp><code>1332</code></nachrichtentyp>\n");
        xml.append("            <erstellungszeitpunkt>").append(timestamp).append("</erstellungszeitpunkt>\n");
        xml.append("        </identifikation.nachricht>\n");
        xml.append("        <leser>\n");
        xml.append("            <verzeichnisdienst listVersionID=\"3\"><code>DVDV</code></verzeichnisdienst>\n");
        xml.append("            <kennung>").append(AGS_LESER).append("</kennung>\n");
        xml.append("            <name>Test Municipality</name>\n");
        xml.append("        </leser>\n");
        xml.append("        <autor>\n");
        xml.append("            <verzeichnisdienst listVersionID=\"3\"><code>DVDV</code></verzeichnisdienst>\n");
        xml.append("            <kennung>").append(DBS_AUTOR).append("</kennung>\n");
        xml.append("            <name>Test Authority</name>\n");
        xml.append("        </autor>\n");
        xml.append("    </nachrichtenkopf.g2g>\n");
        
        // Addresses
        xml.append("    <anschrift.leser><gebaeude><hausnummer>1</hausnummer><postleitzahl>80000</postleitzahl>");
        xml.append("<strasse>Teststraße</strasse><wohnort>München</wohnort></gebaeude></anschrift.leser>\n");
        xml.append("    <anschrift.autor><gebaeude><hausnummer>1</hausnummer><postleitzahl>80000</postleitzahl>");
        xml.append("<strasse>Teststraße</strasse><wohnort>München</wohnort></gebaeude></anschrift.autor>\n");
        
        // Data requesting authority
        xml.append("    <xmeld:datenAbrufendeStelle>\n");
        xml.append("        <xmeld:sicherheitsbehoerde>false</xmeld:sicherheitsbehoerde>\n");
        xml.append("        <xmeld:abrufberechtigteStelle>\n");
        xml.append("            <xmeld:anschrift><gebaeude><hausnummer>1</hausnummer><postleitzahl>80000</postleitzahl>");
        xml.append("<strasse>Teststraße</strasse><wohnort>München</wohnort></gebaeude></xmeld:anschrift>\n");
        xml.append("            <xmeld:behoerdenname>Test Authority</xmeld:behoerdenname>\n");
        xml.append("        </xmeld:abrufberechtigteStelle>\n");
        xml.append("        <xmeld:aktenzeichen>WEB-TEST</xmeld:aktenzeichen>\n");
        xml.append("        <xmeld:anlassDesAbrufs>Web Interface Test</xmeld:anlassDesAbrufs>\n");
        xml.append("        <xmeld:kennung>web/test</xmeld:kennung>\n");
        xml.append("    </xmeld:datenAbrufendeStelle>\n");
        
        // Search profile
        xml.append("    <xmeld:suchprofil>\n");
        xml.append("        <xmeld:auswahldaten>\n");
        xml.append("            <xmeld:name>\n");
        xml.append("                <xmeld:name>\n");
        xml.append("                    <xmeld:nachnameUndVornamen>\n");
        xml.append("                        <xmeld:vornamen><name>").append(escapeXml(vorname)).append("</name></xmeld:vornamen>\n");
        xml.append("                        <xmeld:nachname><name>").append(escapeXml(nachname)).append("</name></xmeld:nachname>\n");
        xml.append("                    </xmeld:nachnameUndVornamen>\n");
        xml.append("                </xmeld:name>\n");
        xml.append("            </xmeld:name>\n");
        
        // Optional address
        if (hasAddress) {
            xml.append("            <xmeld:wohnung>\n");
            xml.append("                <xmeld:anschrift>\n");
            xml.append("                    <xmeld:anschrift.inland>\n");
            if (!plz.isEmpty()) {
                xml.append("                        <postleitzahl>").append(escapeXml(plz)).append("</postleitzahl>\n");
            }
            if (!strasse.isEmpty()) {
                xml.append("                        <strasse>").append(escapeXml(strasse)).append("</strasse>\n");
            }
            if (!ort.isEmpty()) {
                xml.append("                        <wohnort>").append(escapeXml(ort)).append("</wohnort>\n");
            }
            if (!hausnummer.isEmpty()) {
                xml.append("                        <hausnummerOderHausnummernbereich>\n");
                xml.append("                            <hausnummer>").append(escapeXml(hausnummer)).append("</hausnummer>\n");
                xml.append("                        </hausnummerOderHausnummernbereich>\n");
            }
            xml.append("                    </xmeld:anschrift.inland>\n");
            xml.append("                </xmeld:anschrift>\n");
            xml.append("            </xmeld:wohnung>\n");
        }
        
        // Birth date
        xml.append("            <xmeld:geburtsdaten>\n");
        xml.append("                <xmeld:geburtstag>\n");
        xml.append("                    <xmeld:geburtsdatum>\n");
        xml.append("                        <xmeld:geburtsdatum>\n");
        xml.append("                            <teilbekanntesDatum>\n");
        xml.append("                                <jahrMonatTag>").append(geburtsdatum).append("</jahrMonatTag>\n");
        xml.append("                            </teilbekanntesDatum>\n");
        xml.append("                        </xmeld:geburtsdatum>\n");
        xml.append("                    </xmeld:geburtsdatum>\n");
        xml.append("                </xmeld:geburtstag>\n");
        xml.append("            </xmeld:geburtsdaten>\n");
        xml.append("        </xmeld:auswahldaten>\n");
        xml.append("    </xmeld:suchprofil>\n");
        
        // Control information
        xml.append("    <xmeld:steuerungsinformationen>\n");
        for (int i = 1; i <= 10; i++) {
            xml.append("        <xmeld:anforderungselement><code>").append(i).append("</code></xmeld:anforderungselement>\n");
        }
        xml.append("        <xmeld:anforderungselement><code>29</code></xmeld:anforderungselement>\n");
        xml.append("        <xmeld:anforderungselement><code>33</code></xmeld:anforderungselement>\n");
        xml.append("        <xmeld:anforderungselement><code>34</code></xmeld:anforderungselement>\n");
        xml.append("        <xmeld:anforderungselement><code>35</code></xmeld:anforderungselement>\n");
        xml.append("        <xmeld:anforderungselement><code>37</code></xmeld:anforderungselement>\n");
        xml.append("        <xmeld:verzichtAufMitteilung>true</xmeld:verzichtAufMitteilung>\n");
        xml.append("    </xmeld:steuerungsinformationen>\n");
        xml.append("</xmeld:datenabruf.freieSuche.suchanfrage.1332>");
        
        return xml.toString();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
