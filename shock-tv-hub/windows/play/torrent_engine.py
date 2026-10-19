"""Embedded libtorrent, verified-piece streaming over a tokenized loopback HTTP server.

No separate daemon, browser, account, public web server or user configuration.
Only one active torrent. Cache and session are destroyed when playback closes.
"""
import collections
import http.server
import pathlib
import re
import secrets
import shutil
import socket
import tempfile
import threading
import time
import urllib.parse
import urllib.request

MEDIA={'.mp4','.m4v','.mkv','.webm','.mov','.avi','.ts','.m2ts','.mp3','.aac','.flac','.ogg'}

def byte_range(value,size):
    if not value:return 0,size-1,False
    match=re.fullmatch(r'bytes=(\d*)-(\d*)',value.strip())
    if not match or size<=0:raise ValueError('Invalid range')
    a,b=match.groups()
    if not a and not b:raise ValueError('Empty range')
    start=max(0,size-int(b)) if not a else int(a)
    end=size-1 if not a or not b else min(int(b),size-1)
    if start>=size or start>end:raise ValueError('Unsatisfiable range')
    return start,end,True

def select_file(files,index=None):
    count=files.num_files()
    if isinstance(index,int) and not isinstance(index,bool) and 0<=index<count and pathlib.PurePosixPath(files.file_path(index)).suffix.lower() in MEDIA:return index
    if index is not None:raise ValueError('Arquivo anunciado pelo addon não é uma mídia válida')
    choices=[i for i in range(count) if pathlib.PurePosixPath(files.file_path(i)).suffix.lower() in MEDIA]
    if len(choices)!=1:raise ValueError('Torrent com vários vídeos: o addon deve indicar o arquivo do episódio')
    return choices[0]

class TorrentEngine:
    def __init__(self,cache_parent=None):
        import libtorrent as lt
        self.lt=lt
        self.root=pathlib.Path(tempfile.mkdtemp(prefix='telares-torrent-',dir=cache_parent))
        self.closed=False;self.handle=None;self.server=None
        self.condition=threading.Condition();self.pieces=collections.OrderedDict();self.pending=set()
        self.session=lt.session({'enable_dht':True,'enable_lsd':True,'enable_upnp':False,'enable_natpmp':False,
            'listen_interfaces':'0.0.0.0:0','connections_limit':60,'upload_rate_limit':128*1024,
            'alert_mask':int(lt.alert.category_t.all_categories)})
        self.alert_thread=threading.Thread(target=self._alerts,daemon=True);self.alert_thread.start()

    def _alerts(self):
        while not self.closed:
            for alert in self.session.pop_alerts():
                if isinstance(alert,self.lt.read_piece_alert):
                    with self.condition:
                        self.pending.discard(alert.piece)
                        if alert.error.value()==0:
                            self.pieces[alert.piece]=bytes(alert.buffer)
                            while len(self.pieces)>8:self.pieces.popitem(last=False)
                        self.condition.notify_all()
            time.sleep(.03)

    def open(self,source,index=None,timeout=90):
        lt=self.lt
        if source.startswith('magnet:?'):
            params=lt.parse_magnet_uri(source)
            selected=urllib.parse.parse_qs(urllib.parse.urlsplit(source).query).get('so',[])
            if index is None and selected and re.fullmatch(r'\d+',selected[0]):index=int(selected[0])
            params.save_path=str(self.root)
        else:
            parsed=urllib.parse.urlsplit(source)
            if parsed.scheme not in ('http','https') or not parsed.hostname or parsed.username:raise ValueError('Torrent URL inválida')
            with urllib.request.urlopen(urllib.request.Request(source,headers={'User-Agent':'Telares/RC14'}),timeout=15) as response:data=response.read(4_000_001)
            if len(data)>4_000_000:raise ValueError('Metadados torrent muito grandes')
            params=lt.add_torrent_params();params.ti=lt.torrent_info(lt.bdecode(data));params.save_path=str(self.root)
        # Until metadata is checked, never choose/download arbitrary files.
        params.flags|=lt.torrent_flags.upload_mode
        params.flags &= ~lt.torrent_flags.auto_managed
        params.flags &= ~lt.torrent_flags.paused
        self.handle=self.session.add_torrent(params)
        deadline=time.monotonic()+timeout
        while not self.closed and not self.handle.has_metadata():
            if time.monotonic()>deadline:raise TimeoutError('Fonte BitTorrent sem metadados/peers disponíveis')
            time.sleep(.1)
        if self.closed:raise InterruptedError('Reprodução cancelada')
        self.info=self.handle.torrent_file();self.files=self.info.files()
        for i in range(self.files.num_files()):
            path=self.root/self.files.file_path(i)
            if not path.resolve().is_relative_to(self.root.resolve()) or self.files.file_flags(i)&self.lt.file_storage.flag_symlink:raise ValueError('Caminho inseguro no torrent')
        self.index=select_file(self.files,index)
        self.size=self.files.file_size(self.index);self.offset=self.files.file_offset(self.index);self.piece_length=self.info.piece_length()
        if self.size<=0 or self.size>20*1024**3:raise ValueError('Arquivo torrent fora do limite de 20 GiB')
        if shutil.disk_usage(self.root).free<self.size+128*1024**2:raise OSError('Espaço insuficiente para o cache do vídeo')
        self.handle.prioritize_files([4 if i==self.index else 0 for i in range(self.files.num_files())])
        self.handle.unset_flags(lt.torrent_flags.upload_mode)
        token=secrets.token_urlsafe(24);engine=self
        class Handler(http.server.BaseHTTPRequestHandler):
            protocol_version='HTTP/1.1'
            def log_message(self,*args):pass
            def do_HEAD(self):self.serve(False)
            def do_GET(self):self.serve(True)
            def serve(self,body):
                if self.path!='/'+token or self.headers.get('Host') not in ('127.0.0.1:'+str(engine.server.server_port),):self.send_error(404);return
                try:start,end,partial=byte_range(self.headers.get('Range'),engine.size)
                except ValueError:
                    self.send_response(416);self.send_header('Content-Range','bytes */'+str(engine.size));self.send_header('Content-Length','0');self.end_headers();return
                self.send_response(206 if partial else 200);self.send_header('Content-Type','application/octet-stream')
                self.send_header('Accept-Ranges','bytes');self.send_header('Content-Length',str(end-start+1));self.send_header('Connection','close')
                if partial:self.send_header('Content-Range',f'bytes {start}-{end}/{engine.size}')
                self.end_headers()
                if not body:return
                try:
                    while start<=end and not engine.closed:
                        absolute=engine.offset+start;piece=absolute//engine.piece_length;within=absolute%engine.piece_length
                        data=engine.read_piece(piece);n=min(end-start+1,len(data)-within)
                        if n<=0:raise OSError('Bloco torrent inválido')
                        self.wfile.write(data[within:within+n]);start+=n
                except (OSError,TimeoutError,InterruptedError):self.close_connection=True
        self.server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler)
        self.server.daemon_threads=True
        threading.Thread(target=lambda:self.server.serve_forever(poll_interval=.1),daemon=True).start()
        return f'http://127.0.0.1:{self.server.server_port}/{token}'

    def read_piece(self,index,timeout=45):
        deadline=time.monotonic()+timeout
        self.handle.set_piece_deadline(index,0)
        for i in range(index+1,min(index+5,self.info.num_pieces())):self.handle.set_piece_deadline(i,(i-index)*500)
        with self.condition:
            while not self.closed:
                if index in self.pieces:return self.pieces[index]
                if self.handle.have_piece(index) and index not in self.pending:
                    self.pending.add(index);self.handle.read_piece(index)
                if time.monotonic()>deadline:raise TimeoutError('Buffer BitTorrent sem dados suficientes')
                self.condition.wait(.1)
        raise InterruptedError('Reprodução cancelada')

    def close(self):
        if self.closed:return
        self.closed=True
        with self.condition:self.condition.notify_all()
        if self.server:self.server.shutdown();self.server.server_close()
        try:
            self.session.pause()
            if self.handle:self.session.remove_torrent(self.handle)
        finally:
            self.alert_thread.join(timeout=2)
            self.handle=None;self.session=None
            shutil.rmtree(self.root,ignore_errors=True)
