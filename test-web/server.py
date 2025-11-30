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
import shutil

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
                
            except ValueError as e:
                # XML validation error - return as JSON
                self.send_response(400)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                error_response = json.dumps({
                    'status': 'ERROR',
                    'message': str(e),
                    'trefferAnzahl': 0,
                    'treffer': []
                })
                self.wfile.write(error_response.encode('utf-8'))
            except Exception as e:
                # Other errors
                self.send_response(500)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                error_response = json.dumps({
                    'status': 'ERROR',
                    'message': f'Server error: {str(e)}',
                    'trefferAnzahl': 0,
                    'treffer': []
                })
                self.wfile.write(error_response.encode('utf-8'))
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
        msg_type = str(data.get('messageType', '1332') or '1332')

        # Build XML file according to message type
        xml_content = self.build_xml(data)
        
        # Write to temp file in project root (not test-web)
        project_root = os.path.abspath('..')
        temp_file = os.path.join(project_root, 'test-web', f'temp_search_{msg_type}.xml')
        with open(temp_file, 'w', encoding='utf-8') as f:
            f.write(xml_content)
        
        try:
            # Call Java - use absolute paths
            project_root = os.path.abspath('..')
            # Try known embedded JDK first, then JAVA_HOME, then PATH
            java_home_embedded = os.path.join(project_root, 'tools', 'jdk-11.0.2')
            java_exe_candidates = []
            java_exe_candidates.append(os.path.join(java_home_embedded, 'bin', 'java.exe'))
            java_exe_candidates.append(os.path.join(java_home_embedded, 'bin', 'java'))

            env_java_home = os.environ.get('JAVA_HOME')
            if env_java_home:
                java_exe_candidates.append(os.path.join(env_java_home, 'bin', 'java.exe'))
                java_exe_candidates.append(os.path.join(env_java_home, 'bin', 'java'))

            java_in_path = shutil.which('java')
            if java_in_path:
                java_exe_candidates.append(java_in_path)

            java_exe = next((p for p in java_exe_candidates if p and os.path.exists(p)), None)
            if not java_exe:
                raise FileNotFoundError('Java runtime not found. Install Java 11+, set JAVA_HOME, or place JDK in tools/jdk-11.0.2.')
            
            m2_repo = os.path.join(os.path.expanduser('~'), '.m2', 'repository')
            classpath = [
                os.path.join(project_root, 'target', 'classes'),
                os.path.join(project_root, 'target', 'test-classes'),
                os.path.join(m2_repo, 'org', 'json', 'json', '20231013', 'json-20231013.jar'),
                os.path.join(m2_repo, 'org', 'slf4j', 'slf4j-api', '2.0.9', 'slf4j-api-2.0.9.jar'),
                os.path.join(m2_repo, 'org', 'slf4j', 'slf4j-simple', '2.0.9', 'slf4j-simple-2.0.9.jar'),
            ]
            
            cmd = [
                java_exe,
                '-cp',
                ';'.join(classpath),
                'de.formcycle.baybis.ManualBayBisTrigger',
                temp_file
            ]
            
            print(f"=== DEBUG INFO ===")
            print(f"Temp file path: {temp_file}")
            print(f"Temp file exists: {os.path.exists(temp_file)}")
            print(f"Java exe: {java_exe}")
            print(f"Java exe exists: {os.path.exists(java_exe)}")
            print(f"Full command: {cmd}")
            print(f"===================")
            
            result = subprocess.run(cmd, capture_output=True, text=True)
            
            # Extract JSON from output
            output = result.stdout
            stderr = result.stderr
            
            # Print debug info
            print(f"Java exit code: {result.returncode}")
            print(f"Stdout length: {len(output)}")
            print(f"FULL STDOUT:\n{output}")
            print(f"Stderr length: {len(stderr)}")
            print(f"Last 1000 chars of stderr: {stderr[-1000:] if stderr else 'None'}")
            
            # Try to find JSON in stdout first, then stderr
            json_start = output.find('{')
            if json_start != -1:
                json_str = output[json_start:output.rfind('}')+1]
                print(f"Found JSON in stdout")
                return json_str
            
            # If not in stdout, try stderr (sometimes logs go there)
            json_start = stderr.find('{') if stderr else -1
            if json_start != -1:
                json_str = stderr[json_start:stderr.rfind('}')+1]
                print(f"Found JSON in stderr")
                return json_str
            
            error_msg = f"No JSON in response. Exit code: {result.returncode}"
            if stderr:
                error_msg += f". Error: {stderr[:200]}"
            return json.dumps({"status": "ERROR", "message": error_msg})
                
        finally:
            # Keep temp file for debugging
            print(f"Temp file kept at: {temp_file}")
            pass
            # if os.path.exists(temp_file):
            #     os.remove(temp_file)
    
    def build_xml(self, data):
        """Dispatch XML building based on requested message type."""
        msg_type = str(data.get('messageType', '1332') or '1332')
        if msg_type == '1332':
            return self.build_xml_1332(data)
        if msg_type == '1330':
            return self.build_xml_1330(data)
        raise ValueError(f'Unsupported messageType "{msg_type}". Allowed: 1332, 1330.')

    def build_xml_1332(self, data):
        """Build XMeld 1332 XML (freie Suche) from form data."""
        # Use fixed values from test-template.xml instead of generating new ones
        msg_uuid = 'a91e4951-3f23-596f-9a02-505b94fca5dd'
        timestamp = '2025-03-25T08:35:00.562+01:00'
        
        has_custom_xml = data.get('customXml', '').strip()
        address = {
            'strasse': data.get('strasse', '').strip(),
            'hausnummer': data.get('hausnummer', '').strip(),
            'hausnummerZusatz': data.get('hausnummerZusatz', '').strip(),
            'hausnummerBuchstabe': data.get('hausnummerBuchstabe', '').strip(),
            'postleitzahl': data.get('postleitzahl', '').strip(),
            'wohnort': data.get('wohnort', '').strip(),
        }
        address_attempt = any(address.values())
        missing = []

        # Enforce minimum fields if address is supplied
        if address_attempt:
            if not address['postleitzahl']:
                missing.append('postleitzahl')
            if not address['strasse']:
                missing.append('strasse')
            if not address['wohnort']:
                missing.append('wohnort')
            if missing:
                raise ValueError(f"Adresse unvollstaendig: Felder fehlen ({', '.join(missing)}).")
        has_address = address_attempt and not missing

        # Validate custom XML if provided - only check for basic structural validity
        if has_custom_xml:
            try:
                from xml.dom import minidom
                # Try to parse as XML - minidom is more lenient with namespaces
                # Wrap in a dummy root with common namespaces
                wrapped_xml = f'''<ssxml version="1.0" encoding="UTF-8"ss>
                <root 
                    xmlns:xmeld="http://www.osci.de/xmeld2511a"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xmlns:xs="http://www.w3.org/2001/XMLSchema"
                    xmlns:xima="http://www.osci.de/xinneres/meldeanschrift/5"
                    xmlns:xig="http://www.osci.de/xinneres/geschlecht/1"
                    xmlns:xian="http://www.osci.de/xinneres/allgemeinername/4"
                    xmlns:xida="http://www.osci.de/xinneres/datum/2"
                    xmlns:xiaa="http://www.osci.de/xinneres/auslandsanschrift/5"
                    xmlns:xibehoerde="http://www.osci.de/xinneres/behoerde/7"
                    xmlns:xicgvz="http://www.osci.de/xinneres/codes/gemeindeverzeichnis/3">
                    {has_custom_xml}
                </root>'''
                minidom.parseString(wrapped_xml)
                # Basic check: ensure no loose text at the beginning
                if has_custom_xml[0] not in ['<', ' ', '\n', '\t']:
                    raise ValueError(f'Invalid XML: content must start with a tag, not text')
            except Exception as e:
                if 'ValueError' in str(type(e)):
                    raise
                raise ValueError(f'Invalid XML syntax: {str(e)}')
        
        xml = f'''<?xml version="1.0" encoding="UTF-8"?>
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
            <nachrichtenUUID>{msg_uuid}</nachrichtenUUID>
            <nachrichtentyp><code>1332</code></nachrichtentyp>
            <erstellungszeitpunkt>{timestamp}</erstellungszeitpunkt>
        </identifikation.nachricht>
                                                <leser>
            <verzeichnisdienst listVersionID="3"><code>DVDV</code></verzeichnisdienst>
            <kennung>ags:09000009</kennung>
            <name>S Stadt, Buergerbuero</name>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>01</code></kanal>
                <kennung>[E-Mail-Adresse einer Test-Partei]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test eine Mail-Adresse eingetragen werden.</zusatz>
            </erreichbarkeit>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>02</code></kanal>
                <kennung>[Behoerdentelefon, Sammelanschluss]</kennung>
                <zusatz>Hier kann fuer praktische Zwecke beim Test eine Telefonnummer eingetragen werden.</zusatz>
            </erreichbarkeit>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>02</code></kanal>
                <kennung>[Behoerdentelefon, Durchwahl]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test die Telefonnummer eines Ansprechpartners fuer den Test eingetragen werden.</zusatz>
            </erreichbarkeit>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>04</code></kanal>
                <kennung>[Behoerdenfax]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test eine Faxnummer eingetragen werden.</zusatz>
            </erreichbarkeit>
        </leser>
        <autor>
            <verzeichnisdienst listVersionID="3"><code>DVDV</code></verzeichnisdienst>
            <kennung>dbs:060030010000</kennung>
            <name>Amtsgericht S Stadt</name>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>01</code></kanal>
                <kennung>[E-Mail-Adresse einer Test-Partei]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test eine Mail-Adresse eingetragen werden. Hier der Name des Ansprechpartners.</zusatz>
            </erreichbarkeit>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>02</code></kanal>
                <kennung>[Behoerdentelefon, Sammelanschluss]</kennung>
                <zusatz>Hier kann fuer praktische Zwecke beim Test eine Telefonnummer eingetragen werden.</zusatz>
            </erreichbarkeit>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>02</code></kanal>
                <kennung>[Behoerdentelefon, Durchwahl]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test die Telefonnummer eines Ansprechpartners fuer den Test eingetragen werden. Hier der Name des Ansprechpartners.</zusatz>
            </erreichbarkeit>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>04</code></kanal>
                <kennung>[Behoerdenfax]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test eine Faxnummer eingetragen werden.</zusatz>
            </erreichbarkeit>
        </autor>
    </nachrichtenkopf.g2g>
    <anschrift.leser><gebaeude><hausnummer>153</hausnummer><postleitzahl>60000</postleitzahl><strasse>Rathausstrasse</strasse><wohnort>S Stadt</wohnort></gebaeude></anschrift.leser>
    <anschrift.autor><gebaeude><hausnummer>91</hausnummer><postleitzahl>60000</postleitzahl><strasse>Rathausstrasse</strasse><wohnort>S Stadt</wohnort></gebaeude></anschrift.autor>
                <xmeld:datenAbrufendeStelle>
        <xmeld:sicherheitsbehoerde>true</xmeld:sicherheitsbehoerde>
        <xmeld:abrufberechtigteStelle>
            <xmeld:erreichbarkeit>
                <kanal listVersionID="3"><code>01</code></kanal>
                <kennung>[E-Mail-Adresse einer Test-Partei]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test eine Mail-Adresse eingetragen werden. Hier der Name des Ansprechpartners.</zusatz>
            </xmeld:erreichbarkeit>
            <xmeld:erreichbarkeit>
                <kanal listVersionID="3"><code>02</code></kanal>
                <kennung>[Behoerdentelefon, Sammelanschluss]</kennung>
                <zusatz>Hier kann fuer praktische Zwecke beim Test eine Telefonnummer eingetragen werden.</zusatz>
            </xmeld:erreichbarkeit>
            <xmeld:erreichbarkeit>
                <kanal listVersionID="3"><code>02</code></kanal>
                <kennung>[Behoerdentelefon, Durchwahl]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test die Telefonnummer eines Ansprechpartners fuer den Test eingetragen werden. Hier der Name des Ansprechpartners.</zusatz>
            </xmeld:erreichbarkeit>
            <xmeld:erreichbarkeit>
                <kanal listVersionID="3"><code>04</code></kanal>
                <kennung>[Behoerdenfax]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test eine Faxnummer eingetragen werden.</zusatz>
            </xmeld:erreichbarkeit>
            <xmeld:anschrift><gebaeude><hausnummer>91</hausnummer><postleitzahl>60000</postleitzahl><strasse>Rathausstrasse</strasse><wohnort>S Stadt</wohnort></gebaeude></xmeld:anschrift>
            <xmeld:behoerdenname>Amtsgericht S Stadt</xmeld:behoerdenname>
        </xmeld:abrufberechtigteStelle>
        <xmeld:aktenzeichen>34952939</xmeld:aktenzeichen>
        <xmeld:anlassDesAbrufs>Personenueberpruefung</xmeld:anlassDesAbrufs>
        <xmeld:kennung>zb/2111</xmeld:kennung>
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
            hausnummer_block = ''
            if address['hausnummer']:
                hausnummer_block = f'''
                        <xmeld:hausnummerOderHausnummernbereich>
                            <xmeld:hausnummer>
                                <hausnummer>{address['hausnummer']}</hausnummer>'''
                if address['hausnummerBuchstabe']:
                    hausnummer_block += f'''
                                <hausnummerBuchstabeZusatzziffer>{address['hausnummerBuchstabe']}</hausnummerBuchstabeZusatzziffer>'''
                if address['hausnummerZusatz']:
                    hausnummer_block += f'''
                                <teilnummerDerHausnummer>{address['hausnummerZusatz']}</teilnummerDerHausnummer>'''
                hausnummer_block += '''
                            </xmeld:hausnummer>
                        </xmeld:hausnummerOderHausnummernbereich>'''

            xml += f'''
            <xmeld:wohnung>
                <xmeld:anschrift>
                    <xmeld:anschrift.inland>'''
            if hausnummer_block:
                xml += hausnummer_block
            xml += f'''
                        <postleitzahl>{address['postleitzahl']}</postleitzahl>
                        <strasse>{address['strasse']}</strasse>
                        <wohnort>{address['wohnort']}</wohnort>
                    </xmeld:anschrift.inland>
                </xmeld:anschrift>
            </xmeld:wohnung>'''

        # Insert custom XML if provided (between name and geburtsdaten)
        if has_custom_xml:
            # Clean up the custom XML: remove excessive whitespace and normalize indentation
            import re
            # Remove leading/trailing whitespace from each line
            lines = [line.strip() for line in has_custom_xml.split('\n') if line.strip()]
            # Join with proper indentation
            cleaned_xml = '\n            '.join(lines)
            xml += '\n            ' + cleaned_xml
        
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
            <xmeld:geschlecht listVersionID="1">
                <code>{data.get('geschlecht', 'w')}</code>
            </xmeld:geschlecht>
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
        <xmeld:suchbereich>
            <xmeld:bundesland listURI="urn:de:bund:destatis:bevoelkerungsstatistik:schluessel:bundesland" listVersionID="0">
                <code>09</code>
            </xmeld:bundesland>
        </xmeld:suchbereich>
        <xmeld:verzichtAufMitteilung>true</xmeld:verzichtAufMitteilung>
    </xmeld:steuerungsinformationen>
</xmeld:datenabruf.freieSuche.suchanfrage.1332>'''
        
        return xml

    def build_xml_1330(self, data):
        """Build XMeld 1330 XML (personensuche) from form data."""
        msg_uuid = 'b47016cf-bec4-5980-9a23-38b421c2c9d8'
        timestamp = '2025-03-25T08:35:02.562+01:00'

        plz = (data.get('postleitzahl') or '60000').strip()
        geschlecht = data.get('geschlecht') or 'm'
        gemeindeschluessel = (data.get('gemeindeschluessel') or '09000009').strip()

        has_custom_xml = data.get('customXml', '').strip()
        if has_custom_xml:
            try:
                from xml.dom import minidom
                wrapped_xml = f'''<ssxml version="1.0" encoding="UTF-8"ss>
                <root 
                    xmlns:xmeld="http://www.osci.de/xmeld2511a"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xmlns:xs="http://www.w3.org/2001/XMLSchema"
                    xmlns:xima="http://www.osci.de/xinneres/meldeanschrift/5"
                    xmlns:xig="http://www.osci.de/xinneres/geschlecht/1"
                    xmlns:xian="http://www.osci.de/xinneres/allgemeinername/4"
                    xmlns:xida="http://www.osci.de/xinneres/datum/2"
                    xmlns:xiaa="http://www.osci.de/xinneres/auslandsanschrift/5"
                    xmlns:xibehoerde="http://www.osci.de/xinneres/behoerde/7"
                    xmlns:xicgvz="http://www.osci.de/xinneres/codes/gemeindeverzeichnis/3">
                    {has_custom_xml}
                </root>'''
                minidom.parseString(wrapped_xml)
                if has_custom_xml[0] not in ['<', ' ', '\n', '\t']:
                    raise ValueError('Invalid XML: content must start with a tag, not text')
            except Exception as e:
                if isinstance(e, ValueError):
                    raise
                raise ValueError(f'Invalid XML syntax: {str(e)}')

        xml = f'''<?xml version="1.0" encoding="UTF-8"?>
<xmeld:datenabruf.personensuche.suchanfrage.1330 xmlns:xmeld="http://www.osci.de/xmeld2511a"
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
            <nachrichtenUUID>{msg_uuid}</nachrichtenUUID>
            <nachrichtentyp><code>1330</code></nachrichtentyp>
            <erstellungszeitpunkt>{timestamp}</erstellungszeitpunkt>
        </identifikation.nachricht>
        <leser>
            <verzeichnisdienst listVersionID="3"><code>DVDV</code></verzeichnisdienst>
            <kennung>ags:09000009</kennung>
            <name>S Stadt, Buergerbuero</name>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>01</code></kanal>
                <kennung>[E-Mail-Adresse einer Test-Partei]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test eine Mail-Adresse eingetragen werden.</zusatz>
            </erreichbarkeit>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>02</code></kanal>
                <kennung>[Behoerdentelefon, Sammelanschluss]</kennung>
                <zusatz>Hier kann fuer praktische Zwecke beim Test eine Telefonnummer eingetragen werden.</zusatz>
            </erreichbarkeit>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>02</code></kanal>
                <kennung>[Behoerdentelefon, Durchwahl]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test die Telefonnummer eines Ansprechpartners fuer den Test eingetragen werden.</zusatz>
            </erreichbarkeit>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>04</code></kanal>
                <kennung>[Behoerdenfax]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test eine Faxnummer eingetragen werden.</zusatz>
            </erreichbarkeit>
        </leser>
        <autor>
            <verzeichnisdienst listVersionID="3"><code>DVDV</code></verzeichnisdienst>
            <kennung>dbs:060030020000</kennung>
            <name>Polizei S Stadt</name>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>01</code></kanal>
                <kennung>[E-Mail-Adresse einer Test-Partei]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test eine Mail-Adresse eingetragen werden. Hier der Name des Ansprechpartners.</zusatz>
            </erreichbarkeit>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>02</code></kanal>
                <kennung>[Behoerdentelefon, Sammelanschluss]</kennung>
                <zusatz>Hier kann fuer praktische Zwecke beim Test eine Telefonnummer eingetragen werden.</zusatz>
            </erreichbarkeit>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>02</code></kanal>
                <kennung>[Behoerdentelefon, Durchwahl]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test die Telefonnummer eines Ansprechpartners fuer den Test eingetragen werden. Hier der Name des Ansprechpartners.</zusatz>
            </erreichbarkeit>
            <erreichbarkeit>
                <kanal listVersionID="3"><code>04</code></kanal>
                <kennung>[Behoerdenfax]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test eine Faxnummer eingetragen werden.</zusatz>
            </erreichbarkeit>
        </autor>
    </nachrichtenkopf.g2g>
    <anschrift.leser><gebaeude><hausnummer>153</hausnummer><postleitzahl>60000</postleitzahl><strasse>RathausstraAYe</strasse><wohnort>S Stadt</wohnort></gebaeude></anschrift.leser>
    <anschrift.autor><gebaeude><hausnummer>91</hausnummer><postleitzahl>60000</postleitzahl><strasse>Rathausweg</strasse><wohnort>S Stadt</wohnort></gebaeude></anschrift.autor>
        <xmeld:datenAbrufendeStelle>
        <xmeld:sicherheitsbehoerde>true</xmeld:sicherheitsbehoerde>
        <xmeld:abrufberechtigteStelle>
            <xmeld:erreichbarkeit>
                <kanal listVersionID="3"><code>01</code></kanal>
                <kennung>[E-Mail-Adresse einer Test-Partei]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test eine Mail-Adresse eingetragen werden. Hier der Name des Ansprechpartners.</zusatz>
            </xmeld:erreichbarkeit>
            <xmeld:erreichbarkeit>
                <kanal listVersionID="3"><code>02</code></kanal>
                <kennung>[Behoerdentelefon, Sammelanschluss]</kennung>
                <zusatz>Hier kann fuer praktische Zwecke beim Test eine Telefonnummer eingetragen werden.</zusatz>
            </xmeld:erreichbarkeit>
            <xmeld:erreichbarkeit>
                <kanal listVersionID="3"><code>02</code></kanal>
                <kennung>[Behoerdentelefon, Durchwahl]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test die Telefonnummer eines Ansprechpartners fuer den Test eingetragen werden. Hier der Name des Ansprechpartners.</zusatz>
            </xmeld:erreichbarkeit>
            <xmeld:erreichbarkeit>
                <kanal listVersionID="3"><code>04</code></kanal>
                <kennung>[Behoerdenfax]</kennung>
                <zusatz>Unter kennung kann fuer praktische Zwecke beim Test eine Faxnummer eingetragen werden.</zusatz>
            </xmeld:erreichbarkeit>
            <xmeld:anschrift><gebaeude><hausnummer>91</hausnummer><postleitzahl>60000</postleitzahl><strasse>Rathausweg</strasse><wohnort>S Stadt</wohnort></gebaeude></xmeld:anschrift>
            <xmeld:behoerdenname>Polizei S Stadt</xmeld:behoerdenname>
        </xmeld:abrufberechtigteStelle>
        <xmeld:aktenzeichen>34952939-zb/2101</xmeld:aktenzeichen>
        <xmeld:anlassDesAbrufs>Personenueberpruefung</xmeld:anlassDesAbrufs>
        <xmeld:kennung>zb/2102</xmeld:kennung>
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
            </xmeld:name>
            <xmeld:anschrift>
                <gemeindeschluessel listVersionID="2025-01-31"><code>{gemeindeschluessel}</code></gemeindeschluessel>
                <postleitzahl>{plz}</postleitzahl>
            </xmeld:anschrift>'''

        if has_custom_xml:
            import re
            lines = [line.strip() for line in has_custom_xml.split('\n') if line.strip()]
            cleaned_xml = '\n            '.join(lines)
            xml += '\n            ' + cleaned_xml

        xml += f'''
            <xmeld:geburtsdatum>
                <xmeld:geburtsdatum>
                    <teilbekanntesDatum>
                        <jahrMonatTag>{data['geburtsdatum']}</jahrMonatTag>
                    </teilbekanntesDatum>
                </xmeld:geburtsdatum>
            </xmeld:geburtsdatum>
            <xmeld:geschlecht listVersionID="1">
                <code>{geschlecht}</code>
            </xmeld:geschlecht>
        </xmeld:auswahldaten>
    </xmeld:suchprofil>
    <xmeld:steuerungsinformationen>'''

        for i in range(1, 186):
            xml += f'\n        <xmeld:anforderungselement><code>{i}</code></xmeld:anforderungselement>'

        xml += '''
        <xmeld:verzichtAufMitteilung>true</xmeld:verzichtAufMitteilung>
    </xmeld:steuerungsinformationen>
</xmeld:datenabruf.personensuche.suchanfrage.1330>'''

        return xml

def run(port=8000):
    server_address = ('', port)
    httpd = HTTPServer(server_address, BayBISHandler)
    print(f'BayBIS Test Server running on http://localhost:{port}')
    print(f'Serving files from: {os.getcwd()}')
    print(f'Open http://localhost:{port}/index.html in your browser')
    print(f'\nPress Ctrl+C to stop...\n')
    httpd.serve_forever()

if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    run(port)
