package br.com.shocktv;
import android.content.Intent;
import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.media3.exoplayer.ExoPlayer;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real Media3 decoder, HTTP/HLS/DASH and embedded torrent on Android emulator. */
public class PlaybackSmokeTest {
 private static final class Origin implements AutoCloseable {
  final Context context=InstrumentationRegistry.getInstrumentation().getContext();
  final ServerSocket socket;volatile boolean closed;final ExecutorService clients=Executors.newCachedThreadPool();
  Origin()throws Exception{
   socket=new ServerSocket(0,5,InetAddress.getByName("127.0.0.1"));
   new Thread(()->{while(!closed)try{Socket s=socket.accept();clients.execute(()->serve(s));}catch(Exception e){break;}},"Fixture-origin").start();
  }
  String url(String path){return "http://127.0.0.1:"+socket.getLocalPort()+"/"+path;}
  byte[] asset(String name)throws Exception{try(InputStream in=context.getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
  void serve(Socket s){try(Socket client=s){
   client.setSoTimeout(5000);BufferedReader in=new BufferedReader(new InputStreamReader(client.getInputStream(),"US-ASCII"));String request=in.readLine();if(request==null)return;
   String name=request.split(" ")[1].substring(1);if(name.contains("..")||name.contains("/"))return;
   String range=null;for(String line;(line=in.readLine())!=null&&!line.isEmpty();)if(line.toLowerCase().startsWith("range:"))range=line.substring(6).trim();
   byte[] data;
   if(name.equals("sample.torrent")){
    String seed=url("");ByteArrayOutputStream out=new ByteArrayOutputStream();out.write("d4:info".getBytes("UTF-8"));out.write(asset("info.bin"));out.write(("8:url-list"+seed.length()+":"+seed+"e").getBytes("UTF-8"));data=out.toByteArray();
   }else data=asset(name);
   long[] r=TorrentEngine.range(range,data.length);String mime=name.endsWith("m3u8")?"application/vnd.apple.mpegurl":name.endsWith("mpd")?"application/dash+xml":"application/octet-stream";
   String header="HTTP/1.1 "+(r[2]==1?"206 Partial Content":"200 OK")+"\r\nContent-Type: "+mime+"\r\nContent-Length: "+(r[1]-r[0]+1)+"\r\nConnection: close\r\n";
   if(r[2]==1)header+="Content-Range: bytes "+r[0]+"-"+r[1]+"/"+data.length+"\r\n";
   OutputStream out=client.getOutputStream();out.write((header+"\r\n").getBytes(StandardCharsets.US_ASCII));out.write(data,(int)r[0],(int)(r[1]-r[0]+1));
  }catch(Exception ignored){}}
  public void close()throws Exception{closed=true;socket.close();clients.shutdownNow();}
 }
 @Test public void decodeAndSeekWithoutSetup()throws Exception{
  try(Origin origin=new Origin()){
   for(String path:new String[]{"sample.mp4","sample.m3u8","sample.mpd","sample.torrent"}){
    Context target=InstrumentationRegistry.getInstrumentation().getTargetContext();Intent intent=new Intent(target,PlayerActivity.class);
    intent.putExtra("localFixture",true);intent.putExtra("url",origin.url(path));intent.putExtra("libraryKey","test|"+path);intent.putExtra("type","direct");intent.putExtra("title","Synthetic playback test");
    try(ActivityScenario<PlayerActivity> scenario=ActivityScenario.launch(intent)){
     AtomicBoolean decoded=new AtomicBoolean();long deadline=System.currentTimeMillis()+60000;
     while(System.currentTimeMillis()<deadline&&!decoded.get()){
      scenario.onActivity(activity->{try{
       java.lang.reflect.Field field=PlayerActivity.class.getDeclaredField("player");field.setAccessible(true);ExoPlayer player=(ExoPlayer)field.get(activity);
       if(player!=null&&player.getVideoDecoderCounters()!=null){player.getVideoDecoderCounters().ensureUpdated();decoded.set(player.getVideoDecoderCounters().renderedOutputBufferCount>0&&player.getCurrentPosition()>500);}
      }catch(Exception e){throw new AssertionError(e);}});Thread.sleep(100);
     }
     assertTrue("Media3 did not render video: "+path,decoded.get());
     scenario.onActivity(activity->{try{java.lang.reflect.Field field=PlayerActivity.class.getDeclaredField("player");field.setAccessible(true);((ExoPlayer)field.get(activity)).seekTo(3000);}catch(Exception e){throw new AssertionError(e);}});
     Thread.sleep(600);AtomicBoolean seek=new AtomicBoolean();scenario.onActivity(activity->{try{java.lang.reflect.Field field=PlayerActivity.class.getDeclaredField("player");field.setAccessible(true);seek.set(((ExoPlayer)field.get(activity)).getCurrentPosition()>=2500);}catch(Exception e){throw new AssertionError(e);}});
     assertTrue("Media3 seek failed: "+path,seek.get());
    }
   }
  }
 }
}
