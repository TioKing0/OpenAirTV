"""Real libtorrent and bundled VLC smoke test, including range reads and decoded video."""
import hashlib,http.server,json,pathlib,sys,threading,time,urllib.request
from torrent_engine import TorrentEngine,byte_range

def run(directory):
    root=pathlib.Path(directory).resolve()
    class Handler(http.server.SimpleHTTPRequestHandler):
        def __init__(self,*args,**kwargs):super().__init__(*args,directory=str(root),**kwargs)
        def log_message(self,*args):pass
        def do_GET(self):
            if self.path.startswith('/protected/'):
                if any(self.headers.get(k)!=v for k,v in {"User-Agent":"Telares-Fixture","Referer":"https://fixture.invalid/","Origin":"https://fixture.invalid"}.items()):self.send_error(403);return
                self.path=self.path[len("/protected"):]
            if self.path=='/sample.torrent':
                url=f'http://127.0.0.1:{self.server.server_port}/'.encode()
                torrent=b'd4:info'+(root/'info.bin').read_bytes()+b'8:url-list'+str(len(url)).encode()+b':'+url+b'e'
                self.send_response(200);self.send_header('Content-Length',str(len(torrent)));self.end_headers();self.wfile.write(torrent)
            else:
                path=(root/urllib.parse.unquote(self.path.split('?',1)[0].lstrip('/'))).resolve()
                if not path.is_relative_to(root) or not path.is_file():self.send_error(404);return
                body=path.read_bytes()
                try:a,b,partial=byte_range(self.headers.get('Range'),len(body))
                except ValueError:self.send_error(416);return
                self.send_response(206 if partial else 200);self.send_header('Content-Length',str(b-a+1))
                self.send_header('Content-Type','application/vnd.apple.mpegurl' if path.suffix=='.m3u8' else 'application/dash+xml' if path.suffix=='.mpd' else 'video/mp4' if path.suffix in ('.mp4','.m4s') else 'video/mp2t' if path.suffix=='.ts' else 'application/octet-stream')
                if partial:self.send_header('Content-Range',f'bytes {a}-{b}/{len(body)}')
                self.end_headers();self.wfile.write(body[a:b+1])
    server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler);server.daemon_threads=True
    threading.Thread(target=server.serve_forever,daemon=True).start()
    engine=TorrentEngine(local_fixture=True);video=None
    try:
        url=engine.open(f'http://127.0.0.1:{server.server_port}/sample.torrent',timeout=20)
        original=(root/'sample.mp4').read_bytes()
        with urllib.request.urlopen(url,timeout=50) as response:received=response.read()
        assert hashlib.sha256(received).digest()==hashlib.sha256(original).digest(),'Torrent data mismatch'
        for value in ('bytes=0-511','bytes=500-999','bytes=-256','bytes=1000-'):
            a,b,_=byte_range(value,len(original))
            with urllib.request.urlopen(urllib.request.Request(url,headers={'Range':value}),timeout=15) as response:
                assert response.status==206 and response.read()==original[a:b+1],value
        from player import VideoEngine
        import os
        os.environ['TELARES_SELF_TEST']='1'
        video=VideoEngine(0,lambda event:None)
        results=[]
        for source in (url,f'http://127.0.0.1:{server.server_port}/sample.m3u8',f'http://127.0.0.1:{server.server_port}/sample.mpd',f'http://127.0.0.1:{server.server_port}/protected/sample.m3u8'):
            headers={"User-Agent":"Telares-Fixture","Referer":"https://fixture.invalid/","Origin":"https://fixture.invalid"} if "/protected/" in source else None
            video.play(source,headers);deadline=time.monotonic()+25;decoded=False
            while time.monotonic()<deadline:
                import vlc
                stats=vlc.MediaStats()
                valid=video.player.get_media().get_stats(stats)
                if valid and stats.decoded_video>0 and video.player.get_time()>1000:decoded=True;break
                time.sleep(.1)
            assert decoded,'VLC did not decode '+source+' state='+str(video.player.get_state())+' time='+str(video.player.get_time())+' decoded='+str(stats.decoded_video)
            video.player.set_pause(1);time.sleep(.2);video.resume(3);seek_deadline=time.monotonic()+5
            while time.monotonic()<seek_deadline and video.position()<2:time.sleep(.1)
            assert 2.5<=video.position()<=3.5,'Seek failed: '+('torrent' if source==url else source.rsplit('/',1)[-1])
            video.player.set_pause(0)
            results.append({'protocol':'hls-headers' if '/protected/' in source else 'torrent' if source==url else 'hls' if 'm3u8' in source else 'dash','decoded_video':True,'seek':True})
        print(json.dumps({'native_version':engine.lt.version,'sha256':hashlib.sha256(original).hexdigest(),'range_reads':'passed','playback':results}))
    finally:
        if video:video.close()
        cache=engine.root;engine.close();server.shutdown();server.server_close()
        assert not cache.exists(),'Torrent cache not removed'
    return 0

if __name__=='__main__':
    import faulthandler
    faulthandler.dump_traceback_later(60,repeat=True)
    sys.exit(run(sys.argv[1]))
