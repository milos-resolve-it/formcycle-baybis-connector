#!/usr/bin/env python3
"""
Simple test server for BayBIS web interface.
This proxies requests to the Java backend.
"""

from http.server import HTTPServer, SimpleHTTPRequestHandler
import json
import subprocess
import os
import sys

class BayBISHandler(SimpleHTTPRequestHandler):
    def do_POST(self):
        if self.path == '/search':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                # Parse request
                data = json.loads(post_data.decode('utf-8'))
                
                # Call Java backend
                result = self.call_java_backend(data)
                
                # Send response
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                self.wfile.write(result.encode('utf-8'))
                
            except Exception as e:
                self.send_error(500, str(e))
        else:
            self.send_error(404)
    
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()
    
    def call_java_backend(self, data):
        """Call the Java ManualBayBisTrigger with generated XML"""
        
        # Build XML file
        xml_content = self.build_xml(data)
        
        # Write to temp file
        temp_file = 'temp_search.xml'
        with open(temp_file, 'w', encoding='utf-8') as f:
            f.write(xml_content)
        
        try:
            # Call Java
            java_home = os.path.join('..', 'tools', 'jdk-11.0.2')
            java_exe = os.path.join(java_home, 'bin', 'java.exe')
            
            classpath = [
                os.path.join('..', 'target', 'classes'),
                os.path.join('..', 'target', 'test-classes'),
                os.path.join(os.path.expanduser('~'), '.m2', 'repository', 'org', 'json', 'json', '20231013', 'json-20231013.jar'),
                os.path.join(os.path.expanduser('~'), '.m2', 'repository', 'org', 'slf4j', 'slf4j-api', '2.0.9', 'slf4j-api-2.0.9.jar'),
                os.path.join(os.path.expanduser('~'), '.m2', 'repository', 'org', 'slf4j', 'slf4j-simple', '2.0.9', 'slf4j-simple-2.0.9.jar'),
            ]
            
            cmd = [
                java_exe,
                '-cp',
                ';'.join(classpath),
                'de.formcycle.baybis.ManualBayBisTrigger',
                temp_file
            ]
            
            result = subprocess.run(cmd, capture_output=True, text=True, cwd='..')
            
            # Extract JSON from output
            output = result.stdout
            json_start = output.find('{')
            if json_start != -1:
                json_str = output[json_start:output.rfind('}')+1]
                return json_str
            else:
                return json.dumps({"status": "ERROR", "message": "No JSON in response"})
                
        finally:
            if os.path.exists(temp_file):
                os.remove(temp_file)
    
    def build_xml(self, data):
        """Build XMeld 1332 XML from form data"""
        import uuid
        from datetime import datetime
        
        msg_uuid = str(uuid.uuid4())
        timestamp = datetime.now().isoformat()
        
        has_address = any([data.get('strasse'), data.get('hausnummer'), 
                          data.get('plz'), data.get('ort')])
        
        xml = f'''<?xml version="1.0" encoding="UTF-8"?>
<xmeld:datenabruf.freieSuche.suchanfrage.1332
    xmlns:xmeld="http://www.osci.de/xmeld2511a"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    version="25.11a"
    standard="XMeld">
    <nachrichtenkopf.g2g>
        <identifikation.nachricht>
            <nachrichtenUUID>{msg_uuid}</nachrichtenUUID>
            <nachrichtentyp><code>1332</code></nachrichtentyp>
            <erstellungszeitpunkt>{timestamp}</erstellungszeitpunkt>
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
                        <xmeld:vornamen><name>{data['vorname']}</name></xmeld:vornamen>
                        <xmeld:nachname><name>{data['nachname']}</name></xmeld:nachname>
                    </xmeld:nachnameUndVornamen>
                </xmeld:name>
            </xmeld:name>'''
        
        if has_address:
            xml += '''
            <xmeld:wohnung>
                <xmeld:anschrift>
                    <xmeld:anschrift.inland>'''
            if data.get('plz'):
                xml += f'\n                        <postleitzahl>{data["plz"]}</postleitzahl>'
            if data.get('strasse'):
                xml += f'\n                        <strasse>{data["strasse"]}</strasse>'
            if data.get('ort'):
                xml += f'\n                        <wohnort>{data["ort"]}</wohnort>'
            if data.get('hausnummer'):
                xml += f'''
                        <hausnummerOderHausnummernbereich>
                            <hausnummer>{data["hausnummer"]}</hausnummer>
                        </hausnummerOderHausnummernbereich>'''
            xml += '''
                    </xmeld:anschrift.inland>
                </xmeld:anschrift>
            </xmeld:wohnung>'''
        
        xml += f'''
            <xmeld:geburtsdaten>
                <xmeld:geburtstag>
                    <xmeld:geburtsdatum>
                        <xmeld:geburtsdatum>
                            <teilbekanntesDatum>
                                <jahrMonatTag>{data['geburtsdatum']}</jahrMonatTag>
                            </teilbekanntesDatum>
                        </xmeld:geburtsdatum>
                    </xmeld:geburtsdatum>
                </xmeld:geburtstag>
            </xmeld:geburtsdaten>
        </xmeld:auswahldaten>
    </xmeld:suchprofil>
    <xmeld:steuerungsinformationen>'''
        
        for i in range(1, 11):
            xml += f'\n        <xmeld:anforderungselement><code>{i}</code></xmeld:anforderungselement>'
        
        xml += '''
        <xmeld:anforderungselement><code>29</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>33</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>34</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>35</code></xmeld:anforderungselement>
        <xmeld:anforderungselement><code>37</code></xmeld:anforderungselement>
        <xmeld:verzichtAufMitteilung>true</xmeld:verzichtAufMitteilung>
    </xmeld:steuerungsinformationen>
</xmeld:datenabruf.freieSuche.suchanfrage.1332>'''
        
        return xml

def run(port=8000):
    server_address = ('', port)
    httpd = HTTPServer(server_address, BayBISHandler)
    print(f'🚀 BayBIS Test Server running on http://localhost:{port}')
    print(f'📂 Serving files from: {os.getcwd()}')
    print(f'🔍 Open http://localhost:{port}/index.html in your browser')
    print(f'\nPress Ctrl+C to stop...\n')
    httpd.serve_forever()

if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    run(port)
