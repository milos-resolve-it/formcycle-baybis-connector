# Simple HTTP server for BayBIS test interface
# No Python required!

$port = 8888
$url = "http://localhost:$port/"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   BayBIS Test Server" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Starting server on port $port..." -ForegroundColor Green
Write-Host "Open: $url`index.html" -ForegroundColor Yellow
Write-Host ""
Write-Host "Press Ctrl+C to stop the server" -ForegroundColor Gray
Write-Host ""

# Start browser
Start-Process "$url`index.html"

# Create HTTP listener
$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add($url)
$listener.Start()

Write-Host "Server running! Waiting for requests..." -ForegroundColor Green

try {
    while ($listener.IsListening) {
        $context = $listener.GetContext()
        $request = $context.Request
        $response = $context.Response

        Write-Host "$(Get-Date -Format 'HH:mm:ss') - $($request.HttpMethod) $($request.Url.LocalPath)" -ForegroundColor Cyan

        if ($request.Url.LocalPath -eq "/search" -and $request.HttpMethod -eq "POST") {
            # Handle search request
            $reader = New-Object System.IO.StreamReader($request.InputStream)
            $body = $reader.ReadToEnd()
            $reader.Close()

            $data = $body | ConvertFrom-Json

            # Build XML - use fixed values from test-template.xml
            $uuid = "a91e4951-3f23-596f-9a02-505b94fca5dd"
            $timestamp = "2025-03-25T08:35:00.562+01:00"
            
            # Use exact XML structure from working test file
            $xml = @"
<?xml version="1.0" encoding="UTF-8"?>
<xmeld:datenabruf.freieSuche.suchanfrage.1332
	xmlns:xmeld="http://www.osci.de/xmeld2511a"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	xmlns:xima="http://www.osci.de/xinneres/meldeanschrift/5"
	xmlns:xig="http://www.osci.de/xinneres/geschlecht/1"
	xmlns:xian="http://www.osci.de/xinneres/allgemeinername/4"
	xmlns:xida="http://www.osci.de/xinneres/datum/2"
	xmlns:xiaa="http://www.osci.de/xinneres/auslandsanschrift/5"
	xmlns:xibehoerde="http://www.osci.de/xinneres/behoerde/7"
	xmlns:xicgvz="http://www.osci.de/xinneres/codes/gemeindeverzeichnis/3"
	xsi:schemaLocation="http://www.osci.de/xmeld2511a http://www.osci.de/xmeld2511a/xmeld-nachrichten-datenabrufe.xsd"
	version="25.11a"
	produkt="BayBIS Web Test Interface"
	produkthersteller="Formcycle"
	produktversion="1.0"
	standard="XMeld">
	<nachrichtenkopf.g2g>
		<identifikation.nachricht>
			<nachrichtenUUID>$uuid</nachrichtenUUID>
			<nachrichtentyp>
				<code>1332</code>
			</nachrichtentyp>
			<erstellungszeitpunkt>$timestamp</erstellungszeitpunkt>
		</identifikation.nachricht>
		<leser>
			<verzeichnisdienst listVersionID="3">
				<code>DVDV</code>
			</verzeichnisdienst>
			<kennung>ags:09000009</kennung>
			<name>S Stadt, Bürgerbüro</name>
			<erreichbarkeit>
				<kanal listVersionID="3">
					<code>01</code>
				</kanal>
				<kennung>[E-Mail-Adresse einer Test-Partei]</kennung>
				<zusatz>Unter kennung kann für praktische Zwecke beim Test eine Mail-Adresse eingetragen werden.</zusatz>
			</erreichbarkeit>
			<erreichbarkeit>
				<kanal listVersionID="3">
					<code>02</code>
				</kanal>
				<kennung>[Behördentelefon, Sammelanschluss]</kennung>
				<zusatz>Hier kann für praktische Zwecke beim Test eine Telefonnummer eingetragen werden.</zusatz>
			</erreichbarkeit>
		</leser>
		<autor>
			<verzeichnisdienst listVersionID="3">
				<code>DVDV</code>
			</verzeichnisdienst>
			<kennung>dbs:060030010000</kennung>
			<name>Amtsgericht S Stadt</name>
			<erreichbarkeit>
				<kanal listVersionID="3">
					<code>01</code>
				</kanal>
				<kennung>[E-Mail-Adresse einer Test-Partei]</kennung>
				<zusatz>Unter kennung kann für praktische Zwecke beim Test eine Mail-Adresse eingetragen werden.</zusatz>
			</erreichbarkeit>
		</autor>
	</nachrichtenkopf.g2g>
	<anschrift.leser>
		<gebaeude>
			<hausnummer>153</hausnummer>
			<postleitzahl>60000</postleitzahl>
			<strasse>Rathausstraße</strasse>
			<wohnort>S Stadt</wohnort>
		</gebaeude>
	</anschrift.leser>
	<anschrift.autor>
		<gebaeude>
			<hausnummer>91</hausnummer>
			<postleitzahl>60000</postleitzahl>
			<strasse>Rathausstraße</strasse>
			<wohnort>S Stadt</wohnort>
		</gebaeude>
	</anschrift.autor>
	<xmeld:datenAbrufendeStelle>
		<xmeld:sicherheitsbehoerde>true</xmeld:sicherheitsbehoerde>
		<xmeld:abrufberechtigteStelle>
			<xmeld:erreichbarkeit>
				<kanal listVersionID="3">
					<code>01</code>
				</kanal>
				<kennung>[E-Mail-Adresse einer Test-Partei]</kennung>
				<zusatz>Unter kennung kann für praktische Zwecke beim Test eine Mail-Adresse eingetragen werden.</zusatz>
			</xmeld:erreichbarkeit>
			<xmeld:anschrift>
				<gebaeude>
					<hausnummer>91</hausnummer>
					<postleitzahl>60000</postleitzahl>
					<strasse>Rathausstraße</strasse>
					<wohnort>S Stadt</wohnort>
				</gebaeude>
			</xmeld:anschrift>
			<xmeld:behoerdenname>Amtsgericht S Stadt</xmeld:behoerdenname>
		</xmeld:abrufberechtigteStelle>
		<xmeld:aktenzeichen>34952939</xmeld:aktenzeichen>
		<xmeld:anlassDesAbrufs>Personenüberprüfung</xmeld:anlassDesAbrufs>
		<xmeld:kennung>zb/2111</xmeld:kennung>
	</xmeld:datenAbrufendeStelle>
	<xmeld:suchprofil>
		<xmeld:auswahldaten>
			<xmeld:name>
				<xmeld:name>
					<xmeld:nachnameUndVornamen>
						<xmeld:vornamen>
							<name>$($data.vorname)</name>
						</xmeld:vornamen>
						<xmeld:nachname>
							<name>$($data.nachname)</name>
						</xmeld:nachname>
					</xmeld:nachnameUndVornamen>
				</xmeld:name>
			</xmeld:name>
			<xmeld:geburtsdaten>
				<xmeld:geburtstag>
					<xmeld:geburtsdatum>
						<xmeld:geburtsdatum>
							<teilbekanntesDatum>
								<jahrMonatTag>$($data.geburtsdatum)</jahrMonatTag>
							</teilbekanntesDatum>
						</xmeld:geburtsdatum>
					</xmeld:geburtsdatum>
				</xmeld:geburtstag>
			</xmeld:geburtsdaten>
			<xmeld:geschlecht listVersionID="1">
				<code>w</code>
			</xmeld:geschlecht>
		</xmeld:auswahldaten>
	</xmeld:suchprofil>
	<xmeld:steuerungsinformationen>
		<xmeld:anforderungselement>
			<code>1</code>
		</xmeld:anforderungselement>
		<xmeld:anforderungselement>
			<code>2</code>
		</xmeld:anforderungselement>
		<xmeld:anforderungselement>
			<code>3</code>
		</xmeld:anforderungselement>
		<xmeld:anforderungselement>
			<code>4</code>
		</xmeld:anforderungselement>
		<xmeld:anforderungselement>
			<code>5</code>
		</xmeld:anforderungselement>
		<xmeld:anforderungselement>
			<code>6</code>
		</xmeld:anforderungselement>
		<xmeld:anforderungselement>
			<code>7</code>
		</xmeld:anforderungselement>
		<xmeld:anforderungselement>
			<code>8</code>
		</xmeld:anforderungselement>
		<xmeld:anforderungselement>
			<code>9</code>
		</xmeld:anforderungselement>
		<xmeld:anforderungselement>
			<code>10</code>
		</xmeld:anforderungselement>
		<xmeld:anforderungselement>
			<code>29</code>
		</xmeld:anforderungselement>
		<xmeld:anforderungselement>
			<code>33</code>
		</xmeld:anforderungselement>
		<xmeld:anforderungselement>
			<code>34</code>
		</xmeld:anforderungselement>
		<xmeld:anforderungselement>
			<code>35</code>
		</xmeld:anforderungselement>
		<xmeld:anforderungselement>
			<code>37</code>
		</xmeld:anforderungselement>
		<xmeld:suchbereich>
			<xmeld:bundesland
                listURI="urn:de:bund:destatis:bevoelkerungsstatistik:schluessel:bundesland"
                listVersionID="0">
				<code>09</code>
			</xmeld:bundesland>
		</xmeld:suchbereich>
		<xmeld:verzichtAufMitteilung>true</xmeld:verzichtAufMitteilung>
	</xmeld:steuerungsinformationen>
</xmeld:datenabruf.freieSuche.suchanfrage.1332>
"@

            # Save to temp file in project root
            $projectRoot = Split-Path $PWD -Parent
            $tempFile = Join-Path $projectRoot "test-web\temp_search.xml"
            
            try {
                $xml | Out-File -FilePath $tempFile -Encoding UTF8
                Write-Host "Created temp XML file: $tempFile" -ForegroundColor Gray
                Write-Host "Generated XML (first 500 chars):" -ForegroundColor Cyan
                Write-Host $xml.Substring(0, [Math]::Min(500, $xml.Length)) -ForegroundColor Gray

                # Call Java - use absolute paths
                $javaHome = Join-Path $projectRoot "tools\jdk-11.0.2"
                $javaExe = Join-Path $javaHome "bin\java.exe"
                $m2Repo = "$env:USERPROFILE\.m2\repository"
                
                if (-not (Test-Path $javaExe)) {
                    throw "Java not found at: $javaExe"
                }
                
                $classpath = @(
                    (Join-Path $projectRoot "target\classes"),
                    (Join-Path $projectRoot "target\test-classes"),
                    "$m2Repo\org\json\json\20231013\json-20231013.jar",
                    "$m2Repo\org\slf4j\slf4j-api\2.0.9\slf4j-api-2.0.9.jar",
                    "$m2Repo\org\slf4j\slf4j-simple\2.0.9\slf4j-simple-2.0.9.jar"
                ) -join ";"

                Write-Host "Calling Java backend..." -ForegroundColor Gray
                Write-Host "Working directory: $projectRoot" -ForegroundColor Gray
                
                # Change to project root and use relative path
                Push-Location $projectRoot
                try {
                    $relativeTempFile = "test-web\temp_search.xml"
                    $output = & $javaExe -cp $classpath de.formcycle.baybis.ManualBayBisTrigger $relativeTempFile 2>&1 | Out-String
                } finally {
                    Pop-Location
                }

                Write-Host "Java output length: $($output.Length) chars" -ForegroundColor Gray

                # Extract JSON - find the JSON block and parse it properly
                $jsonStart = $output.IndexOf('{')
                if ($jsonStart -ge 0) {
                    $jsonEnd = $output.LastIndexOf('}')
                    $jsonString = $output.Substring($jsonStart, $jsonEnd - $jsonStart + 1)
                    
                    # Parse and re-serialize to ensure valid JSON
                    try {
                        $jsonObj = $jsonString | ConvertFrom-Json
                        # Remove rawXml to avoid JSON encoding issues
                        $jsonObj.PSObject.Properties.Remove('rawXml')
                        $json = $jsonObj | ConvertTo-Json -Depth 10 -Compress
                        Write-Host "Extracted and cleaned JSON successfully" -ForegroundColor Green
                    } catch {
                        Write-Host "ERROR: Failed to parse JSON: $_" -ForegroundColor Red
                        $json = "{`"status`":`"ERROR`",`"message`":`"Invalid JSON from backend`"}"
                    }
                } else {
                    Write-Host "ERROR: No JSON found in output" -ForegroundColor Red
                    Write-Host "Output: $output" -ForegroundColor Red
                    
                    # Extract error message from Java output
                    $errorMsg = "No JSON in response"
                    if ($output -match "HTTP Error (\d+)") {
                        $errorMsg = "BayBIS returned HTTP Error $($matches[1])"
                    } elseif ($output -match "Ein technischer Fehler") {
                        $errorMsg = "BayBIS technical error - person may not exist in test database"
                    } elseif ($output -match "Error reading file") {
                        $errorMsg = "Failed to read XML file"
                    }
                    
                    $json = "{`"status`":`"ERROR`",`"message`":`"$errorMsg`",`"trefferAnzahl`":0,`"treffer`":[]}"
                }
            } catch {
                Write-Host "ERROR in Java execution: $_" -ForegroundColor Red
                $json = "{`"status`":`"ERROR`",`"message`":`"Server error: $($_.Exception.Message)`"}"
            } finally {
                # Clean up
                if (Test-Path $tempFile) {
                    Remove-Item $tempFile
                }
            }

            # Send response
            $response.Headers.Add("Access-Control-Allow-Origin", "*")
            $response.Headers.Add("Content-Type", "application/json")
            $buffer = [System.Text.Encoding]::UTF8.GetBytes($json)
            $response.ContentLength64 = $buffer.Length
            $response.OutputStream.Write($buffer, 0, $buffer.Length)
            $response.OutputStream.Close()

        } elseif ($request.HttpMethod -eq "OPTIONS") {
            # CORS preflight
            $response.Headers.Add("Access-Control-Allow-Origin", "*")
            $response.Headers.Add("Access-Control-Allow-Methods", "POST, OPTIONS")
            $response.Headers.Add("Access-Control-Allow-Headers", "Content-Type")
            $response.StatusCode = 200
            $response.Close()

        } else {
            # Serve static files
            $filePath = Join-Path (Get-Location) $request.Url.LocalPath.TrimStart('/')
            
            if ($request.Url.LocalPath -eq "/") {
                $filePath = Join-Path (Get-Location) "index.html"
            }

            if (Test-Path $filePath) {
                $content = [System.IO.File]::ReadAllBytes($filePath)
                $response.ContentLength64 = $content.Length
                
                # Set content type
                $ext = [System.IO.Path]::GetExtension($filePath)
                switch ($ext) {
                    ".html" { $response.ContentType = "text/html" }
                    ".css" { $response.ContentType = "text/css" }
                    ".js" { $response.ContentType = "application/javascript" }
                    default { $response.ContentType = "application/octet-stream" }
                }
                
                $response.OutputStream.Write($content, 0, $content.Length)
            } else {
                $response.StatusCode = 404
                $buffer = [System.Text.Encoding]::UTF8.GetBytes("404 - Not Found")
                $response.OutputStream.Write($buffer, 0, $buffer.Length)
            }
            
            $response.Close()
        }
    }
} finally {
    $listener.Stop()
    Write-Host "`nServer stopped." -ForegroundColor Yellow
}
