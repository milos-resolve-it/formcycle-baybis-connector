package de.formcycle.baybis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Manual trigger for testing the BayBIS connection from the command line.
 * Reads an XMeld XML file and sends it directly via {@link BayBisSoapClient}.
 */
public class ManualBayBisTrigger {

    private static final Logger LOG = LoggerFactory.getLogger(ManualBayBisTrigger.class);

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("   BayBIS Connector - Manual Test Tool    ");
        System.out.println("==========================================");

        String endpoint = null; // use default endpoint in BayBisSoapClient
        String filePath = args.length > 0 ? args[0] : "spec/test/our-test.xml";

        System.out.println("\nUsing endpoint: " + (endpoint == null ? "Default (https://apk-int.akdb.de/okkommbis/services/XoevService)" : endpoint));
        System.out.println("Using test file: " + filePath);
        System.out.println("\nTo use a different file, run: java ... ManualBayBisTrigger <path-to-xml>");

        LOG.info("=== Manual Test Tool Started ===");
        LOG.info("Input file: {}", filePath);

        String xmlPayload;
        try {
            xmlPayload = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
            System.out.println("\n[OK] XML file loaded successfully (" + xmlPayload.length() + " characters)");
            LOG.info("Loaded XML payload: {} characters", xmlPayload.length());
        } catch (IOException e) {
            System.err.println("\n[ERROR] Error reading file: " + e.getMessage());
            LOG.error("Failed to read input file", e);
            return;
        }

        System.out.println("\n==========================================");
        System.out.println("Sending request to: " + (endpoint == null ? "Default Endpoint" : endpoint));
        System.out.println("==========================================");

        BayBisSoapClient client = new BayBisSoapClient(endpoint);
        long startTime = System.currentTimeMillis();
        LOG.info("Sending request to BayBIS...");

        try {
            String responseXml = client.sendRequest(xmlPayload);
            long duration = System.currentTimeMillis() - startTime;

            System.out.println("\n[SUCCESS] Response received in " + duration + " ms");
            LOG.info("Received response from BayBIS in {} ms", duration);
            System.out.println("\n==========================================");
            System.out.println("Raw XMeld 1333 Response:");
            System.out.println("==========================================");
            System.out.println(responseXml);
            System.out.println("==========================================");

            XMeldResponseParser parser = new XMeldResponseParser();
            System.out.println("\n==========================================");
            System.out.println("Parsed JSON Result:");
            System.out.println("==========================================");
            System.out.println(parser.parseResponse(responseXml).toString(2));
            System.out.println("==========================================");

        } catch (BayBisConnectorException e) {
            System.err.println("\n[ERROR] Connector Exception");
            System.err.println("Message: " + e.getMessage());
            System.err.println("Error Code: " + e.getErrorCode());
            System.err.println("\nStack Trace:");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("\n[ERROR] Unexpected Exception");
            System.err.println("Message: " + e.getMessage());
            System.err.println("\nStack Trace:");
            e.printStackTrace();
        }
    }
}
