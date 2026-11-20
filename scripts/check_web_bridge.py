"""Exercise the actual loopback web service with an isolated upstream (no DB/card needed)."""
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
import subprocess, os, threading, time, json

root = Path(__file__).resolve().parents[1]
java = Path(os.environ.get('JAVA_HOME', '')) / 'bin/java.exe'
if not java.exists(): java = Path(r'C:\Program Files\Java\jdk-21.0.11\bin\java.exe')

class Upstream(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/api/v1/status':
            self.send_response(200); self.end_headers(); self.wfile.write(b'{"status":"UP"}')
        elif self.path == '/api/v1/me':
            if self.headers.get('Authorization') != 'Bearer test-only':
                self.send_response(401); self.end_headers(); return
            self.send_response(200); self.end_headers(); self.wfile.write(b'{"data":{"username":"test"}}')
        elif self.path == '/api/v1/redirect':
            self.send_response(302); self.send_header('Location', 'http://127.0.0.1:1/'); self.end_headers()
        else:
            self.send_response(404); self.end_headers()
    def do_POST(self):
        value = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        self.send_response(200); self.end_headers(); self.wfile.write(value)
    def log_message(self, *args): pass

server = ThreadingHTTPServer(('127.0.0.1', 0), Upstream)
threading.Thread(target=server.serve_forever, daemon=True).start()
env = dict(os.environ, IDP_API_BASE_URL=f'http://127.0.0.1:{server.server_port}')
process = subprocess.Popen([str(java), '-Djava.awt.headless=true', '-cp', str(root/'target/classes')+';'+str(root/'target/dependency/*'), 'vn.kma.desktop.WebApplication'], cwd=root, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, creationflags=subprocess.CREATE_NO_WINDOW)
cases = []

def request(path, method='GET', payload=None, headers=None):
    req = Request('http://127.0.0.1:8787'+path, data=None if payload is None else json.dumps(payload).encode(), method=method, headers=headers or {})
    try:
        with urlopen(req, timeout=3) as response: return response.status, dict(response.headers), response.read()
    except HTTPError as e: return e.code, dict(e.headers), e.read()

def check(label, condition):
    cases.append({'case': label, 'passed': bool(condition)})
    if not condition: raise AssertionError(label)

try:
    for _ in range(50):
        if process.poll() is not None: raise RuntimeError('Bridge failed to start: '+process.stderr.read().decode(errors='replace'))
        try:
            if request('/bridge/status')[0] == 200: break
        except (URLError, OSError): time.sleep(.1)
    else: raise RuntimeError('Bridge startup timed out')
    status, headers, body = request('/api/v1/status')
    check('Fixed upstream proxy returns backend status', status==200 and json.loads(body)['status']=='UP')
    check('API response is not cached', headers.get('Cache-control')=='no-store')
    status, _, body = request('/api/v1/me', headers={'Authorization':'Bearer test-only'})
    check('Bearer header is forwarded to the fixed upstream', status==200 and json.loads(body)['data']['username']=='test')
    check('External origins cannot access local bridge', request('/bridge/status', headers={'Origin':'https://untrusted.example'})[0]==403)
    check('Unknown Host is rejected against DNS rebinding', request('/bridge/status', headers={'Host':'untrusted.example'})[0]==403)
    check('Mutation without Origin is rejected', request('/api/v1/example','POST',{}, {'Content-Type':'application/json'})[0]==403)
    check('Mutation using form content type is rejected', request('/api/v1/example','POST',{}, {'Origin':'http://127.0.0.1:8787','Content-Type':'text/plain'})[0]==403)
    status, _, body = request('/api/v1/example','POST',{'example':True}, {'Origin':'http://127.0.0.1:8787','Content-Type':'application/json'})
    check('Same-origin JSON mutation reaches the upstream', status==200 and json.loads(body)=={'example':True})
    check('Unknown card route is rejected', request('/bridge/unknown')[0]==404)
    check('Card read requires POST', request('/bridge/card/read')[0]==405)
    check('Upstream redirects are not followed', request('/api/v1/redirect')[0]==302)
    status, _, body = request('/bridge/card/read','POST',None, {'Origin':'http://127.0.0.1:8787','Content-Type':'application/json'})
    check('Missing card payload is rejected before opening a device', status==400)
    status, _, body = request('/bridge/card/read','POST',{'transport':'REMOTE'}, {'Origin':'http://127.0.0.1:8787','Content-Type':'application/json'})
    check('Unknown card transport is rejected', status==400)
    check('Oversized writes are rejected', request('/api/v1/example','POST',{'value':'x'*262145}, {'Origin':'http://127.0.0.1:8787','Content-Type':'application/json'})[0]==400)
    print(f'WEB BRIDGE HTTP CHECKS PASSED: {len(cases)}')
finally:
    process.terminate()
    process.communicate(timeout=10)
    server.shutdown(); server.server_close()
    report={'status':'PASS' if len(cases)==14 and all(c['passed'] for c in cases) else 'INCOMPLETE','cases':cases,'passed':sum(c['passed'] for c in cases),'total':len(cases),'scope':'Actual loopback bridge and isolated HTTP upstream. No MySQL or native card.'}
    (root/'target/web-bridge-http-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
