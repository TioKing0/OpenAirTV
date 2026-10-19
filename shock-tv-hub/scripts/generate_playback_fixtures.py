"""Generate our own H.264/AAC, HLS and DASH test media; no third-party content."""
import hashlib,pathlib,subprocess,sys

def bencode(value):
    if isinstance(value,bytes):return str(len(value)).encode()+b':'+value
    if isinstance(value,str):return bencode(value.encode())
    if isinstance(value,int):return b'i'+str(value).encode()+b'e'
    if isinstance(value,list):return b'l'+b''.join(map(bencode,value))+b'e'
    return b'd'+b''.join(bencode(k)+bencode(v) for k,v in sorted(value.items()))+b'e'

def generate(directory):
    root=pathlib.Path(directory);root.mkdir(parents=True,exist_ok=True)
    sample=root/'sample.mp4'
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','lavfi','-i','testsrc=size=160x90:rate=10','-f','lavfi','-i','sine=frequency=440:sample_rate=44100','-t','8','-c:v','libx264','-pix_fmt','yuv420p','-g','10','-c:a','aac','-movflags','+faststart',str(sample)],check=True)
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(sample),'-c','copy','-hls_time','1','-hls_list_size','0',str(root/'sample.m3u8')],check=True)
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(sample),'-c','copy','-f','dash','-seg_duration','1',str(root/'sample.mpd')],check=True)
    body=sample.read_bytes();piece=16384
    info={'length':len(body),'name':'sample.mp4','piece length':piece,'pieces':b''.join(hashlib.sha1(body[n:n+piece]).digest() for n in range(0,len(body),piece))}
    (root/'info.bin').write_bytes(bencode(info))
    print('Generated synthetic H264/AAC, HLS, DASH and torrent info at '+str(root))
    return root

if __name__=='__main__':generate(sys.argv[1] if len(sys.argv)>1 else 'shock-tv-hub/test-fixtures')
