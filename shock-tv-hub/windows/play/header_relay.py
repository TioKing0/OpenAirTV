"""Private, bounded HLS transport for addon-declared public request headers."""
import base64,http.server,re,secrets,socket,threading,urllib.parse,urllib.request

ALLOWED={'user-agent':'User-Agent','referer':'Referer','origin':'Origin','accept':'Accept'}
class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,url):
        if not HeaderRelay.valid(url) or (urllib.parse.urlsplit(req.full_url).scheme=='https' and urllib.parse.urlsplit(url).scheme!='https'):
            raise urllib.error.HTTPError(req.full_url,502,'Unsafe media redirect',headers,fp)
        return super().redirect_request(req,fp,code,msg,headers,url)
def public_headers(value):
    if not isinstance(value,dict):return {}
    return {ALLOWED[k.lower()]:v for k,v in (value or {}).items()
            if isinstance(k,str) and k.lower() in ALLOWED and isinstance(v,str)
            and 0<len(v)<=2048 and '\r' not in v and '\n' not in v}

class HeaderRelay:
    def __init__(self,headers):
        self.headers=public_headers(headers);self.token=secrets.token_urlsafe(24)
        self.closed=False;self.slots=threading.BoundedSemaphore(4);self.clients=set();self.lock=threading.Lock()
        relay=self
        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self,*args):pass
            def do_GET(self):self.serve(False)
            def do_HEAD(self):self.serve(True)
            def serve(self,head):
                if self.headers.get('Host')!='127.0.0.1:'+str(relay.server.server_port):self.send_error(404);return
                prefix='/'+relay.token+'/'
                if not self.path.startswith(prefix) or len(self.path)>16000:self.send_error(404);return
                try:
                    encoded=self.path[len(prefix):].split('/',1)[0]
                    url=base64.urlsafe_b64decode(encoded+'='*(-len(encoded)%4)).decode()
                    if not relay.valid(url):raise ValueError()
                except Exception:self.send_error(404);return
                if not relay.slots.acquire(False):self.send_error(503);return
                with relay.lock:relay.clients.add(self.connection)
                try:
                    headers=dict(relay.headers);headers['Accept-Encoding']='identity'
                    value=self.headers.get('Range')
                    if value and not urllib.parse.urlsplit(url).path.lower().endswith('.m3u8') and re.fullmatch(r'bytes=\d*-\d*',value):headers['Range']=value
                    req=urllib.request.Request(url,headers=headers,method='HEAD' if head else 'GET')
                    with urllib.request.build_opener(SafeRedirect()).open(req,timeout=12) as upstream:
                        mime=upstream.headers.get('Content-Type','application/octet-stream')
                        playlist=('mpegurl' in mime.lower() or urllib.parse.urlsplit(upstream.url).path.lower().endswith('.m3u8'))
                        body=None
                        if playlist and not head:
                            data=upstream.read(2_000_001)
                            if len(data)>2_000_000:raise ValueError('Playlist too large')
                            body=relay.rewrite(data.decode('utf-8-sig'),upstream.url).encode()
                        self.send_response(upstream.status);self.send_header('Content-Type',mime)
                        self.send_header('Connection','close')
                        if body is not None:self.send_header('Content-Length',str(len(body)))
                        else:
                            for name in ('Content-Length','Content-Range','Accept-Ranges'):
                                if upstream.headers.get(name):self.send_header(name,upstream.headers[name])
                        self.end_headers()
                        if head:return
                        if body is not None:self.wfile.write(body)
                        else:
                            while not relay.closed:
                                chunk=upstream.read(65536)
                                if not chunk:break
                                self.wfile.write(chunk)
                except urllib.error.HTTPError as error:
                    self.send_error(error.code)
                except (OSError,ValueError):self.close_connection=True
                finally:
                    with relay.lock:relay.clients.discard(self.connection)
                    relay.slots.release()
        self.server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);self.server.daemon_threads=True
        threading.Thread(target=lambda:self.server.serve_forever(poll_interval=.1),daemon=True).start()
    @staticmethod
    def valid(url):
        p=urllib.parse.urlsplit(url)
        return p.scheme in ('http','https') and bool(p.hostname) and not p.username and not p.password
    def url(self,url):
        if not self.valid(url):raise ValueError('Invalid media URL')
        encoded=base64.urlsafe_b64encode(url.encode()).decode().rstrip('=')
        suffix='/index.m3u8' if urllib.parse.urlsplit(url).path.lower().endswith('.m3u8') else '/media'
        return f'http://127.0.0.1:{self.server.server_port}/{self.token}/{encoded}{suffix}'
    def rewrite(self,text,base):
        if not text.lstrip().startswith('#EXTM3U'):raise ValueError('Invalid HLS')
        def route(value):
            absolute=urllib.parse.urljoin(base,value)
            return self.url(absolute) if self.valid(absolute) else value
        lines=[]
        for line in text.splitlines():
            if line and not line.startswith('#'):line=route(line.strip())
            elif line.startswith('#'):line=re.sub(r'URI="([^"\r\n]+)"',lambda m:'URI="'+route(m[1])+'"',line)
            lines.append(line)
        return '\n'.join(lines)+'\n'
    def close(self):
        if self.closed:return
        self.closed=True
        with self.lock:
            for client in self.clients:
                try:client.shutdown(socket.SHUT_RDWR)
                except OSError:pass
        self.server.shutdown();self.server.server_close()
