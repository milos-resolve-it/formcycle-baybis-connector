# BayBIS Connector - Logging Documentation

## Overview

Comprehensive logging has been implemented across all components using SLF4J.

## Logging Levels

### INFO Level
- Process start/end markers
- Key milestones (request sent, response received)
- Summary information (number of persons found)
- **No PII (Personally Identifiable Information)**

### DEBUG Level
- Detailed XML payloads (with PII masking)
- Base64 encoding/decoding steps
- Individual person parsing
- **PII is masked** for privacy

### ERROR Level
- Exceptions with full stack traces
- SOAP faults
- HTTP errors
- Parsing errors

## Components with Logging

### 1. BayBisSoapClient
**Location:** `src/main/java/de/formcycle/baybis/BayBisSoapClient.java`

**Logs:**
```
[INFO ] === BayBIS Request Start ===
[INFO ] Endpoint: https://apk-int.akdb.de/okkommbis/services/XoevService
[DEBUG] Input XML (masked): <xmeld:...><name>Ba***</name>...
[DEBUG] Base64 encoded payload length: 2048
[DEBUG] Generated SOAP Envelope.
[INFO ] Sending SOAP request to: https://...
[INFO ] Received response with status code: 200
[DEBUG] Extracted Base64 response, length: 15234
[INFO ] Successfully decoded response XML. Length: 8456 bytes
[DEBUG] Response XML (masked): <xmeld:...><name>Ba***</name>...
[INFO ] === BayBIS Request Complete ===
```

**PII Masking:**
- Names: `Barbara` → `Ba***`
- Birthdates: `1992-02-02` → `1992-**-**`
- Addresses: Full masking
- IDs: Full masking
- Serial numbers: Full masking

### 2. XMeldResponseParser
**Location:** `src/main/java/de/formcycle/baybis/XMeldResponseParser.java`

**Logs:**
```
[INFO ] === Parsing XMeld Response ===
[DEBUG] Response XML length: 8456 bytes
[INFO ] Response status: SUCCESS
[INFO ] Found 2 person(s) in response
[DEBUG] Parsing person 1/2
[DEBUG] Person ID: 20986359
[DEBUG] Parsing person 2/2
[DEBUG] Person ID: 29504894
[INFO ] === Parsing Complete ===
[INFO ] Result: 2 person(s) successfully parsed into JSON
```

### 3. ManualBayBisTrigger (Test Tool)
**Location:** `src/test/java/de/formcycle/baybis/ManualBayBisTrigger.java`

**Logs:**
```
[INFO ] === Manual Test Tool Started ===
[INFO ] Input file: spec/test/FachspezifischBehoerdenauskunft005a-1332.xml
[INFO ] Loaded XML payload: 4256 characters
[INFO ] Sending request to BayBIS...
[INFO ] Received response from BayBIS in 1234 ms
```

### 4. Web Test Interface
**Location:** `test-web/start-server.ps1`

**Console Output:**
```
08:30:15 - POST /search
Processing search request...
Calling Java backend...
Response received: 2 persons found
```

## Log Configuration

### SLF4J Simple (Default)
The project uses `slf4j-simple` for testing. Configuration in `simplelogger.properties`:

```properties
# Default log level
org.slf4j.simpleLogger.defaultLogLevel=info

# Show date/time
org.slf4j.simpleLogger.showDateTime=true
org.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss.SSS

# Show thread name
org.slf4j.simpleLogger.showThreadName=false

# Show logger name
org.slf4j.simpleLogger.showLogName=true

# For DEBUG level on specific classes
org.slf4j.simpleLogger.log.de.formcycle.baybis.BayBisSoapClient=debug
org.slf4j.simpleLogger.log.de.formcycle.baybis.XMeldResponseParser=debug
```

### Production (Formcycle)
In production, Formcycle will use its own logging framework (likely Logback or Log4j2).

## Example Log Output

### Successful Request
```
[INFO ] 09:15:23 de.formcycle.baybis.ManualBayBisTrigger - === Manual Test Tool Started ===
[INFO ] 09:15:23 de.formcycle.baybis.ManualBayBisTrigger - Input file: spec/test/FachspezifischBehoerdenauskunft005a-1332.xml
[INFO ] 09:15:23 de.formcycle.baybis.ManualBayBisTrigger - Loaded XML payload: 4256 characters
[INFO ] 09:15:23 de.formcycle.baybis.BayBisSoapClient - === BayBIS Request Start ===
[INFO ] 09:15:23 de.formcycle.baybis.BayBisSoapClient - Endpoint: https://apk-int.akdb.de/okkommbis/services/XoevService
[INFO ] 09:15:23 de.formcycle.baybis.BayBisSoapClient - Sending SOAP request to: https://apk-int.akdb.de/okkommbis/services/XoevService
[INFO ] 09:15:24 de.formcycle.baybis.BayBisSoapClient - Received response with status code: 200
[INFO ] 09:15:24 de.formcycle.baybis.BayBisSoapClient - Successfully decoded response XML. Length: 8456 bytes
[INFO ] 09:15:24 de.formcycle.baybis.BayBisSoapClient - === BayBIS Request Complete ===
[INFO ] 09:15:24 de.formcycle.baybis.XMeldResponseParser - === Parsing XMeld Response ===
[INFO ] 09:15:24 de.formcycle.baybis.XMeldResponseParser - Response status: SUCCESS
[INFO ] 09:15:24 de.formcycle.baybis.XMeldResponseParser - Found 2 person(s) in response
[INFO ] 09:15:24 de.formcycle.baybis.XMeldResponseParser - === Parsing Complete ===
[INFO ] 09:15:24 de.formcycle.baybis.XMeldResponseParser - Result: 2 person(s) successfully parsed into JSON
```

### Error Case
```
[INFO ] 09:20:15 de.formcycle.baybis.BayBisSoapClient - === BayBIS Request Start ===
[INFO ] 09:20:15 de.formcycle.baybis.BayBisSoapClient - Sending SOAP request to: https://apk-int.akdb.de/okkommbis/services/XoevService
[INFO ] 09:20:16 de.formcycle.baybis.BayBisSoapClient - Received response with status code: 500
[ERROR] 09:20:16 de.formcycle.baybis.BayBisSoapClient - HTTP Error: <?xml version='1.0'...><faultstring>Ein technischer Fehler...</faultstring>...
[ERROR] 09:20:16 de.formcycle.baybis.BayBisSoapClient - BayBIS Connector Exception: HTTP Error 500
```

## Privacy & GDPR Compliance

### PII Masking Strategy
All DEBUG-level logs containing XML payloads are automatically masked:

1. **Names:** Keep first 2 characters, mask rest
   - `Barbara Carina` → `Ba***`
   - `Fischer` → `Fi***`

2. **Birthdates:** Keep year only
   - `1992-02-02` → `1992-**-**`

3. **Addresses:** Full masking
   - Street, house number, postal code all masked

4. **IDs:** Full masking
   - Person IDs, serial numbers completely masked

### INFO Level = No PII
INFO level logs contain **no personal data**, only:
- Process flow information
- Counts and statistics
- Technical details (endpoints, response codes)
- Timing information

## Monitoring & Troubleshooting

### Key Metrics to Monitor
1. **Request Duration:** Time between "Request Start" and "Request Complete"
2. **Response Codes:** HTTP status codes
3. **Person Count:** Number of persons found
4. **Error Rate:** Frequency of ERROR logs

### Common Issues

**"HTTP Error 500"**
- Check BayBIS service status
- Verify credentials in XML
- Check error ID in SOAP fault

**"SOAP Fault"**
- Invalid XML structure
- Missing required fields
- Wrong namespace

**"Parsing Error"**
- Unexpected response format
- Missing elements in response

## Best Practices

1. **Production:** Use INFO level by default
2. **Debugging:** Enable DEBUG for specific classes only
3. **Never log raw XML at INFO level** (contains PII)
4. **Always check ERROR logs** for exceptions
5. **Monitor request duration** for performance issues

## Log Rotation

For production deployment, configure log rotation:
- Max file size: 10MB
- Keep last 30 days
- Compress old logs
- Separate error logs from info logs
