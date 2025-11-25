package de.formcycle.baybis;

import de.xima.fc.plugin.interfaces.workflow.IFCPluginWorkflowProcessingContext;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class BayBisConnectorTest {

    // Sample XMeld 1333 Response (Success Case)
    private static final String SAMPLE_1333_XML = 
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<xmeld:meldeauskunft.antwort.1333 xmlns:xmeld=\"http://www.osci.de/xmeld2511a\" xmlns:xink=\"http://www.osci.de/xinneres/basisnachricht/5\">" +
        "  <xink:nachrichtenkopf>" +
        "    <xink:nachrichtencode>1333</xink:nachrichtencode>" +
        "  </xink:nachrichtenkopf>" +
        "  <xmeld:ergebnis>" +
        "    <xmeld:trefferliste>" +
        "      <xmeld:treffer>" +
        "        <xmeld:natuerlichePerson>" +
        "          <xmeld:familienname><xmeld:name>Mustermann</xmeld:name></xmeld:familienname>" +
        "          <xmeld:vornamen><xmeld:name>Max</xmeld:name></xmeld:vornamen>" +
        "          <xmeld:wohnung>" +
        "            <xmeld:anschrift>" +
        "              <xmeld:strasse>Musterstraße</xmeld:strasse>" +
        "              <xmeld:hausnummer>12</xmeld:hausnummer>" +
        "              <xmeld:postleitzahl>12345</xmeld:postleitzahl>" +
        "              <xmeld:ort>Musterstadt</xmeld:ort>" +
        "              <xmeld:gemeindeschluessel>09123456</xmeld:gemeindeschluessel>" +
        "              <xmeld:statusWohnung>F</xmeld:statusWohnung>" +
        "            </xmeld:anschrift>" +
        "          </xmeld:wohnung>" +
        "        </xmeld:natuerlichePerson>" +
        "      </xmeld:treffer>" +
        "    </xmeld:trefferliste>" +
        "  </xmeld:ergebnis>" +
        "</xmeld:meldeauskunft.antwort.1333>";

    @Test
    public void testXMeldResponseParser() {
        System.out.println("Testing XMeldResponseParser...");
        XMeldResponseParser parser = new XMeldResponseParser();
        JSONObject result = parser.parseResponse(SAMPLE_1333_XML);

        System.out.println("Parsed JSON: " + result.toString(2));

        Assert.assertEquals("SUCCESS", result.getString("status"));
        Assert.assertEquals(1, result.getInt("trefferAnzahl"));
        
        JSONObject hit = result.getJSONArray("treffer").getJSONObject(0);
        Assert.assertEquals("Mustermann", hit.getString("nachname"));
        Assert.assertEquals("Max", hit.getString("vorname"));
        Assert.assertEquals("Musterstraße", hit.getString("strasse"));
        Assert.assertEquals("12", hit.getString("hausnummer"));
        Assert.assertEquals("12345", hit.getString("plz"));
        Assert.assertEquals("Musterstadt", hit.getString("ort"));
        Assert.assertEquals("09123456", hit.getString("ags"));
        Assert.assertEquals("F", hit.getString("wohnungStatus"));
    }

    @Test
    public void testBayBisRawActionValidation() {
        System.out.println("Testing BayBisRawAction validation...");
        BayBisRawAction action = new BayBisRawAction();
        MockContext context = new MockContext();

        // Test missing configuration
        try {
            action.execute(context);
            Assert.fail("Should throw exception for missing config");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof BayBisConnectorException);
            Assert.assertEquals("CONFIG_ERR", ((BayBisConnectorException) e).getErrorCode());
        }
    }
    
    // Simple Mock Context for testing
    private static class MockContext implements IFCPluginWorkflowProcessingContext {
        private final Map<String, String> variables = new HashMap<>();
        private final Map<String, byte[]> files = new HashMap<>();

        @Override
        public String getVariable(String name) {
            return variables.get(name);
        }

        @Override
        public void setVariable(String name, String value) {
            variables.put(name, value);
        }

        @Override
        public byte[] getFileContent(String fileName) {
            return files.get(fileName);
        }
        
        public void addFile(String name, String content) {
            files.put(name, content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
