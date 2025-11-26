package de.formcycle.baybis;

import de.xima.fc.plugin.interfaces.workflow.IFCPluginWorkflowProcessingContext;
import de.xima.fc.plugin.interfaces.workflow.IFCWorkflowAction;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Workflow Action to execute a raw XMeld request against BayBIS.
 * 
 * <p><b>Parameters:</b></p>
 * <ul>
 *   <li>xmlInputSource: Name of the variable or file containing the input XML (Msg 1332).</li>
 *   <li>targetVariable: Name of the process variable to store the JSON result.</li>
 *   <li>isFileInput: (Optional) "true" if xmlInputSource refers to a file, "false" (default) for a variable.</li>
 * </ul>
 */
public class BayBisRawAction implements IFCWorkflowAction {

    private static final Logger LOG = LoggerFactory.getLogger(BayBisRawAction.class);

    // These would normally be injected by the plugin framework
    private String xmlInputSource;
    private String targetVariable;
    private boolean isFileInput; 

    // Default constructor
    public BayBisRawAction() {}
    
    // Setters for configuration (Simulating injection)
    public void setXmlInputSource(String xmlInputSource) {
        this.xmlInputSource = xmlInputSource;
    }

    public void setTargetVariable(String targetVariable) {
        this.targetVariable = targetVariable;
    }
    
    public void setIsFileInput(boolean isFileInput) {
        this.isFileInput = isFileInput;
    }

    @Override
    public boolean execute(IFCPluginWorkflowProcessingContext context) throws Exception {
        LOG.info("Starting BayBisRawAction.");
        
        // 1. Validate Configuration
        if (xmlInputSource == null || xmlInputSource.isBlank()) {
            throw new BayBisConnectorException("Parameter 'xmlInputSource' is missing.", "CONFIG_ERR");
        }
        if (targetVariable == null || targetVariable.isBlank()) {
            throw new BayBisConnectorException("Parameter 'targetVariable' is missing.", "CONFIG_ERR");
        }

        // 2. Retrieve Input XML
        String xmeldXml = retrieveXmlContent(context);
        if (xmeldXml == null || xmeldXml.isBlank()) {
            throw new BayBisConnectorException("Input XML content is empty.", "INPUT_ERR");
        }

        try {
            // 3. Execute BayBIS Request
            BayBisSoapClient client = new BayBisSoapClient(null); // Use default endpoint
            String rawResponse = client.sendRequest(xmeldXml);

            // 4. Parse Response
            XMeldResponseParser parser = new XMeldResponseParser();
            JSONObject resultJson = parser.parseResponse(rawResponse);

            // 5. Write Output
            context.setVariable(targetVariable, resultJson.toString());
            LOG.info("BayBIS Request successful. Result written to variable: {}", targetVariable);
            
            return true;

        } catch (BayBisConnectorException e) {
            LOG.error("BayBIS Connector Error: {} ({})", e.getMessage(), e.getErrorCode());
            // Optionally write error to variable instead of failing hard?
            // For now, we rethrow to stop workflow or let formcycle handle it.
            throw e;
        } catch (Exception e) {
            LOG.error("Unexpected error in BayBisRawAction", e);
            throw e;
        }
    }

    private String retrieveXmlContent(IFCPluginWorkflowProcessingContext context) {
        if (isFileInput) {
            LOG.debug("Reading XML from file: {}", xmlInputSource);
            byte[] content = context.getFileContent(xmlInputSource);
            if (content == null) {
                throw new BayBisConnectorException("File not found: " + xmlInputSource, "FILE_NOT_FOUND");
            }
            return new String(content, StandardCharsets.UTF_8);
        } else {
            LOG.debug("Reading XML from variable: {}", xmlInputSource);
            return context.getVariable(xmlInputSource);
        }
    }
}
