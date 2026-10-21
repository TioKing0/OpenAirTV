import pathlib,sys,unittest,http.server,threading,urllib.request,urllib.error
from unittest.mock import patch
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
from header_relay import HeaderRelay,public_headers
import catalog

class HeaderTests(unittest.TestCase):
    def test_new_addons_registered(self):
        self.assertIn('https://v2.stravo.site/local/manifest.json',catalog.DEFAULT)
        self.assertIn('https://aiostreams.elfhosted.com/stremio/manifest.json',catalog.DEFAULT)
    def test_header_policy(self):
        self.assertEqual(public_headers({'origin':'https://fixture.invalid','Host':'bad','Cookie':'secret','Referer':'bad\r\ninjected: 1'}),{'Origin':'https://fixture.invalid'})
        self.assertEqual(public_headers([]),{})
    def test_catalog_retains_public_headers(self):
        with patch.object(catalog,'manifest',return_value={'name':'Fixture','resources':['stream'],'types':['tv']}),patch.object(catalog,'get_json',return_value={'streams':[{'url':'https://fixture.invalid/a.m3u8','behaviorHints':{'proxyHeaders':{'request':{'Origin':'https://fixture.invalid'}}}}]}):
            found=catalog.streams(['https://fixture.invalid/manifest.json'],{'type':'tv','id':'test'})
        self.assertEqual(found[0]['_headers'],{'Origin':'https://fixture.invalid'})
    def test_nested_playlists_keys_segments_and_ranges(self):
        headers={'User-Agent':'Telares-Test','Referer':'https://fixture.invalid/','Origin':'https://fixture.invalid'}
        requests=[]
        class Origin(http.server.BaseHTTPRequestHandler):
            def log_message(self,*args):pass
            def do_GET(self):
                if any(self.headers.get(k)!=v for k,v in headers.items()):self.send_error(403);return
                requests.append(self.path)
                bodies={'/master.m3u8':b'#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=500000\nvideo/index.m3u8\n',
                        '/video/index.m3u8':b'#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI="key.bin"\n#EXT-X-MAP:URI="init.mp4"\n#EXTINF:1,\nsegment.ts\n#EXT-X-ENDLIST\n',
                        '/video/key.bin':b'0123456789abcdef','/video/init.mp4':b'init','/video/segment.ts':b'0123456789'}
                body=bodies.get(self.path)
                if body is None:self.send_error(404);return
                partial=self.headers.get('Range')=='bytes=2-5'
                if partial:body=body[2:6]
                self.send_response(206 if partial else 200);self.send_header('Content-Length',str(len(body)))
                self.send_header('Content-Type','application/vnd.apple.mpegurl' if '.m3u8' in self.path else 'application/octet-stream')
                if partial:self.send_header('Content-Range','bytes 2-5/10')
                self.end_headers();self.wfile.write(body)
        origin=http.server.ThreadingHTTPServer(('127.0.0.1',0),Origin);origin.daemon_threads=True
        threading.Thread(target=origin.serve_forever,daemon=True).start();relay=HeaderRelay(headers)
        def fetch(url,range=None):
            with urllib.request.urlopen(urllib.request.Request(url,headers={'Range':range} if range else {}),timeout=5) as r:return r.status,r.read()
        try:
            base=f'http://127.0.0.1:{origin.server_port}'
            _,master=fetch(relay.url(base+'/master.m3u8'),'bytes=0-')
            child=master.decode().splitlines()[-1];_,media=fetch(child)
            import re
            urls=re.findall(r'URI="([^"]+)"',media.decode())
            self.assertEqual(fetch(urls[0])[1],b'0123456789abcdef');self.assertEqual(fetch(urls[1])[1],b'init')
            segment=next(line for line in media.decode().splitlines() if line.startswith('http'))
            self.assertEqual(fetch(segment,'bytes=2-5'),(206,b'2345'))
            with self.assertRaises(urllib.error.HTTPError):
                fetch(child.replace(relay.token,'wrong-token'))
            self.assertIn('/video/segment.ts',requests)
        finally:relay.close();origin.shutdown();origin.server_close()

if __name__=='__main__':unittest.main()
