# Simple HTTP server for BayBIS test interface
# No Python required!

$port = 8000
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

            # Build XML
            $uuid = [guid]::NewGuid().ToString()
            $timestamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ss.fffzzz"
            
            $hasAddress = $data.strasse -or $data.hausnummer -or $data.plz -or $data.ort
            
            $xml = @"
<?xml version="1.0" encoding="UTF-8"?>
<xmeld:datenabruf.freieSuche.suchanfrage.1332
    xmlns:xmeld="http://www.osci.de/xmeld2511a"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    version="25.11a"
    standard="XMeld">
    <nachrichtenkopf.g2g>
        <identifikation.nachricht>
            <nachrichtenUUID>$uuid</nachrichtenUUID>
            <nachrichtentyp><code>1332</code></nachrichtentyp>
            <erstellungszeitpunkt>$timestamp</erstellungszeitpunkt>
        </identifikation.nachricht>
        <leser>
            <verzeichnisdienst listVersionID="3"><code>DVDV</code></verzeichnisdienst>
            <kennung>ags:09000009</kennung>
            <name>Test Municipality</name>
        </leser>
        <autor>
            <verzeichnisdienst listVersionID="3"><code>DVDV</code></verzeichnisdienst>
            <kennung>dbs:060030010000</kennung>
            <name>Test Authority</name>
        </autor>
    </nachrichtenkopf.g2g>
    <anschrift.leser><gebaeude><hausnummer>1</hausnummer><postleitzahl>80000</postleitzahl><strasse>Teststraße</strasse><wohnort>München</wohnort></gebaeude></anschrift.leser>
    <anschrift.autor><gebaeude><hausnummer>1</hausnummer><postleitzahl>80000</postleitzahl><strasse>Teststraße</strasse><wohnort>München</wohnort></gebaeude></anschrift.autor>
    <xmeld:datenAbrufendeStelle>
        <xmeld:sicherheitsbehoerde>false</xmeld:sicherheitsbehoerde>
        <xmeld:abrufberechtigteStelle>
            <xmeld:anschrift><gebaeude><hausnummer>1</hausnummer><postleitzahl>80000</postleitzahl><strasse>Teststraße</strasse><wohnort>München</wohnort></gebaeude></xmeld:anschrift>
            <xmeld:behoerdenname>Test Authority</xmeld:behoerdenname>
        </xmeld:abrufberechtigteStelle>
        <xmeld:aktenzeichen>WEB-TEST</xmeld:aktenzeichen>
        <xmeld:anlassDesAbrufs>Web Interface Test</xmeld:anlassDesAbrufs>
        <xmeld:kennung>web/test</xmeld:kennung>
    </xmeld:datenAbrufendeStelle>
    <xmeld:suchprofil>
        <xmeld:auswahldaten>
            <xmeld:name>
                <xmeld:name>
                    <xmeld:nachnameUndVornamen>
                        <xmeld:vornamen><name>$($data.vorname)</name></xmeld:vornamen>
                        <xmeld:nachname><name>$($data.nachname)</name></xmeld:nachname>
                    </xmeld:nachnameUndVornamen>
                </xmeld:name>
            </xmeld:name>
"@

            if ($hasAddress) {
                $xml += @"

            <xmeld:wohnung>
                <xmeld:anschrift>
                    <xmeld:anschrift.inland>
"@
                if ($data.plz) { $xml += "`n                        <postleitzahl>$($data.plz)</postleitzahl>" }
                if ($data.strasse) { $xml += "`n                        <strasse>$($data.strasse)</strasse>" }
                if ($data.ort) { $xml += "`n                        <wohnort>$($data.ort)</wohnort>" }
                if ($data.hausnummer) {
                    $xml += @"

                        <hausnummerOderHausnummernbereich>
                            <hausnummer>$($data.hausnummer)</hausnummer>
                        </hausnummerOderHausnummernbereich>
"@
                }
                $xml += @"

                    </xmeld:anschrift.inland>
                </xmeld:anschrift>
            </xmeld:wohnung>
"@
            }

            $xml += @"

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
        </xmeld:auswahldaten>
    </xmeld:suchprofil>
    <xmeld:steuerungsinformationen>
        <xmeld:anforderungselement><code>1</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>2</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>3</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>4</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>5</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>6</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>7</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>8</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>9</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>10</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>29</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>33</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>34</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>35</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>37</code></xmeld:anforderungselement>
        <xmeld:verzichtAufMitteilung>true</xmeld:verzichtAufMitteilung>
    </xmeld:steuerungsinformationen>
</xmeld:datenabruf.freieSuche.suchanfrage.1332>
"@

            # Save to temp file
            $tempFile = "temp_search.xml"
            $xml | Out-File -FilePath $tempFile -Encoding UTF8

            # Call Java
            $javaHome = "..\tools\jdk-11.0.2"
            $javaExe = "$javaHome\bin\java.exe"
            $m2Repo = "$env:USERPROFILE\.m2\repository"
            
            $classpath = @(
                "..\target\classes",
                "..\target\test-classes",
                "$m2Repo\org\json\json\20231013\json-20231013.jar",
                "$m2Repo\org\slf4j\slf4j-api\2.0.9\slf4j-api-2.0.9.jar",
                "$m2Repo\org\slf4j\slf4j-simple\2.0.9\slf4j-simple-2.0.9.jar"
            ) -join ";"

            $output = & $javaExe -cp $classpath de.formcycle.baybis.ManualBayBisTrigger $tempFile 2>&1 | Out-String

            # Extract JSON
            $jsonStart = $output.IndexOf('{')
            if ($jsonStart -ge 0) {
                $jsonEnd = $output.LastIndexOf('}')
                $json = $output.Substring($jsonStart, $jsonEnd - $jsonStart + 1)
            } else {
                $json = '{"status":"ERROR","message":"No JSON in response"}'
            }

            # Clean up
            if (Test-Path $tempFile) {
                Remove-Item $tempFile
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
