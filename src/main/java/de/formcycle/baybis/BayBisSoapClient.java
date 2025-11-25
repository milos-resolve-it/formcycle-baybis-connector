package de.formcycle.baybis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SOAP Client for communicating with the AKDB BayBIS endpoint.
 * Handles Base64 encoding of the request payload and decoding of the response.
 */
public class BayBisSoapClient {

    private static final Logger LOG = LoggerFactory.getLogger(BayBisSoapClient.class);
    
    // Placeholder endpoint - should be configurable
    private static final String DEFAULT_ENDPOINT = "https://test.baybis.akdb.de/agv/services/agv_v2"; // Example URL
    
    private final String endpointUrl;
    private final HttpClient httpClient;

    public BayBisSoapClient(String endpointUrl) {
        this.endpointUrl = Objects.requireNonNullElse(endpointUrl, DEFAULT_ENDPOINT);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Sends a raw XMeld XML string to BayBIS.
     * 
     * @param xmeldXml The raw XMeld 1332 XML content.
     * @return The raw XMeld 1333 response XML content.
     * @throws BayBisConnectorException if the request fails or SOAP Fault occurs.
     */
    public String sendRequest(String xmeldXml) {
        Objects.requireNonNull(xmeldXml, "xmeldXml must not be null");

        LOG.info("Preparing BayBIS request.");
        
        try {
            // 1. Base64 Encode
            String base64Payload = Base64.getEncoder().encodeToString(xmeldXml.getBytes(StandardCharsets.UTF_8));
            LOG.debug("Base64 encoded payload length: {}", base64Payload.length());

            // 2. Wrap in SOAP Envelope (callApplicationByte)
            String soapRequest = createSoapEnvelope(base64Payload);
            LOG.debug("Generated SOAP Envelope.");

            // 3. Send HTTP Request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", "\"\"") // Usually empty or specific action
                    .POST(HttpRequest.BodyPublishers.ofString(soapRequest, StandardCharsets.UTF_8))
                    .build();

            LOG.info("Sending SOAP request to: {}", endpointUrl);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            LOG.info("Received response with status code: {}", statusCode);

            if (statusCode != 200) {
                LOG.error("HTTP Error: {}", response.body());
                throw new BayBisConnectorException("HTTP Error " + statusCode, "HTTP_ERR_" + statusCode);
            }

            String responseBody = response.body();
            
            // 4. Check for SOAP Faults
            if (responseBody.contains(":Fault>") || responseBody.contains("<Fault>")) {
                LOG.error("SOAP Fault detected.");
                // In a real implementation, parse the fault details.
                throw new BayBisConnectorException("SOAP Fault received from BayBIS", "SOAP_FAULT");
            }

            // 5. Extract and Decode Response
            String base64Response = extractBase64Response(responseBody);
            byte[] decodedBytes = Base64.getDecoder().decode(base64Response);
            String decodedXml = new String(decodedBytes, StandardCharsets.UTF_8);
            
            LOG.debug("Successfully decoded response XML. Length: {}", decodedXml.length());
            return decodedXml;

        } catch (BayBisConnectorException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BayBisConnectorException("Request interrupted", "INTERRUPTED", e);
        } catch (Exception e) {
            LOG.error("Unexpected error during BayBIS request", e);
            throw new BayBisConnectorException("Internal Connector Error: " + e.getMessage(), "INTERNAL_ERR", e);
        }
    }

    private String createSoapEnvelope(String base64Payload) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
               "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:agv=\"http://agv.akdb.de/agv_v2\">" +
               "<soapenv:Header/>" +
               "<soapenv:Body>" +
               "<agv:callApplicationByte>" +
               "<arg0>" + base64Payload + "</arg0>" +
               "</agv:callApplicationByte>" +
               "</soapenv:Body>" +
               "</soapenv:Envelope>";
    }

    private String extractBase64Response(String soapResponse) {
        // Simple Regex extraction for the return tag inside callApplicationByteResponse
        // This assumes standard formatting. Ideally, use a proper XML parser, but regex is faster for this specific wrapping.
        // Expected: <return>BASE64...</return>
        Pattern pattern = Pattern.compile("<return>([^<]+)</return>");
        Matcher matcher = pattern.matcher(soapResponse);
        
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            // Fallback: sometimes namespaces or different tag names might be used depending on the WSDL.
            // We might need to adjust this based on the actual response trace.
            LOG.error("Could not find <return> block in SOAP response.");
            LOG.debug("Full Response: {}", soapResponse);
            throw new BayBisConnectorException("Invalid SOAP Response: Missing return content", "INVALID_RESP");
        }
    }
}
