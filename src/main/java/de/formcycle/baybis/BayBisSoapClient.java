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
    
    // Default endpoint - configurable via constructor
    private static final String DEFAULT_ENDPOINT = "https://apk-int.akdb.de/okkommbis/services/XoevService";
    
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

        LOG.info("=== BayBIS Request Start ===");
        LOG.info("Endpoint: {}", endpointUrl);
        LOG.debug("Input XML (masked): {}", maskPII(xmeldXml));
        
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
            LOG.debug("Extracted Base64 response, length: {}", base64Response.length());
            
            byte[] decodedBytes = Base64.getDecoder().decode(base64Response);
            String decodedXml = new String(decodedBytes, StandardCharsets.UTF_8);
            
            LOG.info("Successfully decoded response XML. Length: {} bytes", decodedXml.length());
            LOG.debug("Response XML (masked): {}", maskPII(decodedXml));
            LOG.info("=== BayBIS Request Complete ===");
            
            return decodedXml;

        } catch (BayBisConnectorException e) {
            LOG.error("BayBIS Connector Exception: {}", e.getMessage());
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
        // XoevService WSDL specifies namespace urn:akdb:ok.komm:xmeld-service and element xmlParameter
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
               "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tns=\"urn:akdb:ok.komm:xmeld-service\">" +
               "<soapenv:Header/>" +
               "<soapenv:Body>" +
               "<tns:callApplicationByte>" +
               "<tns:xmlParameter>" + base64Payload + "</tns:xmlParameter>" +
               "</tns:callApplicationByte>" +
               "</soapenv:Body>" +
               "</soapenv:Envelope>";
    }

    private String extractBase64Response(String soapResponse) {
        // Extract callApplicationByteReturn element as per XoevService WSDL
        // Expected: <callApplicationByteReturn>BASE64...</callApplicationByteReturn>
        Pattern pattern = Pattern.compile("<callApplicationByteReturn>([^<]+)</callApplicationByteReturn>");
        Matcher matcher = pattern.matcher(soapResponse);
        
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            // Fallback: sometimes namespaces or different tag names might be used depending on the WSDL.
            // We might need to adjust this based on the actual response trace.
            LOG.error("Could not find <callApplicationByteReturn> block in SOAP response.");
            LOG.debug("Full Response: {}", soapResponse);
            throw new BayBisConnectorException("Invalid SOAP Response: Missing return content", "INVALID_RESP");
        }
    }
    
    /**
     * Masks PII (Personally Identifiable Information) in XML for logging.
     * Masks: names, birthdates, addresses, IDs
     */
    private String maskPII(String xml) {
        if (xml == null) return null;
        
        String masked = xml;
        
        // Mask names (keep first 2 chars)
        masked = masked.replaceAll("(<name>)([^<]{2})[^<]*(<\\/name>)", "$1$2***$3");
        masked = masked.replaceAll("(<nachname>)([^<]{2})[^<]*(<\\/nachname>)", "$1$2***$3");
        masked = masked.replaceAll("(<vornamen>)([^<]{2})[^<]*(<\\/vornamen>)", "$1$2***$3");
        
        // Mask birthdates (keep year)
        masked = masked.replaceAll("(<jahrMonatTag>)(\\d{4})-\\d{2}-\\d{2}(<\\/jahrMonatTag>)", "$1$2-**-**$3");
        
        // Mask addresses
        masked = masked.replaceAll("(<strasse>)[^<]+(<\\/strasse>)", "$1***$2");
        masked = masked.replaceAll("(<hausnummer>)[^<]+(<\\/hausnummer>)", "$1***$2");
        masked = masked.replaceAll("(<postleitzahl>)[^<]+(<\\/postleitzahl>)", "$1***$2");
        
        // Mask IDs
        masked = masked.replaceAll("(<identifikationsmerkmal>)\\d+(<\\/identifikationsmerkmal>)", "$1***$2");
        masked = masked.replaceAll("(<seriennummer>)[^<]+(<\\/seriennummer>)", "$1***$2");
        
        return masked;
    }
}
