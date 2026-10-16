"""libVLC playback wrapper for embedded VLC runtime, including native Win32 video HWND."""
import os
import pathlib
import sys

def runtime():
    root=pathlib.Path(getattr(sys,"_MEIPASS",pathlib.Path(__file__).resolve().parent))
    home=root/"vlc"
    if not (home/"libvlc.dll").exists():
        for path in (os.environ.get("TELARES_VLC_HOME",""),r"C:\Program Files\VideoLAN\VLC",r"C:\Program Files (x86)\VideoLAN\VLC"):
            if path and pathlib.Path(path,"libvlc.dll").exists():home=pathlib.Path(path);break
    if not (home/"libvlc.dll").exists():raise RuntimeError("VLC não encontrado. Extraia o ZIP completo.")
    os.environ["PATH"]=str(home)+os.pathsep+os.environ.get("PATH","")
    os.environ["VLC_PLUGIN_PATH"]=str(home/"plugins")
    if hasattr(os,"add_dll_directory"):os.add_dll_directory(str(home))
    import vlc
    return vlc

class VideoEngine:
    def __init__(self,hwnd,callback):
        self.vlc=runtime()
        self.instance=self.vlc.Instance("--quiet","--no-video-title-show","--network-caching=1200")
        self.player=self.instance.media_player_new()
        self.player.set_hwnd(hwnd)
        self.hooks=[]
        events=self.player.event_manager()
        for attr,name in (("MediaPlayerEncounteredError","error"),("MediaPlayerPlaying","playing"),("MediaPlayerEndReached","ended")):
            def handler(evt,kind=name):callback(kind)
            self.hooks.append(handler)
            events.event_attach(getattr(self.vlc.EventType,attr),handler)
    def play(self,url):
        self.player.stop()
        self.player.set_media(self.instance.media_new(url))
        self.player.play()
    def stop(self):self.player.stop()
    def pause(self):self.player.pause()
    def seek(self,delta):
        t=self.player.get_time()
        if t>=0:self.player.set_time(max(0,t+int(delta*1000)))
    def position(self):return max(0,self.player.get_time()/1000)
    def duration(self):return max(0,self.player.get_length()/1000)
    def is_playing(self):return bool(self.player.is_playing())
    def resume(self,seconds):self.player.set_time(int(max(0,seconds)*1000))
    def close(self):
        try:self.player.stop();self.player.release();self.instance.release()
        except Exception:pass
