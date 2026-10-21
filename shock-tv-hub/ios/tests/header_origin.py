import http.server,pathlib,sys
class Origin(http.server.BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def do_GET(self):
        expected={'User-Agent':'Telares-Test','Referer':'https://fixture.invalid/','Origin':'https://fixture.invalid'}
        if any(self.headers.get(k)!=v for k,v in expected.items()) or self.headers.get('Cookie'):
            self.send_error(403);return
        bodies={'/master.m3u8':b'#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=500000\nvideo/index.m3u8\n',
                '/video/index.m3u8':b'#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI="key.bin"\n#EXT-X-MAP:URI="init.mp4"\n#EXTINF:1,\nsegment.ts\n#EXT-X-ENDLIST\n',
                '/video/key.bin':b'0123456789abcdef','/video/init.mp4':b'init','/video/segment.ts':b'0123456789'}
        body=bodies.get(self.path)
        if body is None:self.send_error(404);return
        partial=self.headers.get('Range')=='bytes=2-5'
        if partial:body=body[2:6]
        self.send_response(206 if partial else 200);self.send_header('Content-Length',str(len(body)))
        self.send_header('Content-Type','application/vnd.apple.mpegurl' if self.path.endswith('.m3u8') else 'application/octet-stream')
        if partial:self.send_header('Content-Range','bytes 2-5/10')
        self.end_headers();self.wfile.write(body)
server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Origin)
pathlib.Path(sys.argv[1]).write_text(str(server.server_port))
server.serve_forever()
