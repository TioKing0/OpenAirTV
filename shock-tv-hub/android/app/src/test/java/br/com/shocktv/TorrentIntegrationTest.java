package br.com.shocktv;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;

public class TorrentIntegrationTest {
    @Test public void nativeWebSeedAndSeekRanges()throws Exception{
        Path fixtures=Paths.get(System.getProperty("telares.fixture"));
        if(!Files.exists(fixtures.resolve("info.bin")))throw new AssertionError("Playback fixtures missing");
        byte[] original=Files.readAllBytes(fixtures.resolve("sample.mp4"));
        ServerSocket origin=new ServerSocket(0,5,InetAddress.getByName("127.0.0.1"));
        String seed="http://127.0.0.1:"+origin.getLocalPort()+"/";
        ByteArrayOutputStream torrent=new ByteArrayOutputStream();torrent.write("d4:info".getBytes("UTF-8"));torrent.write(Files.readAllBytes(fixtures.resolve("info.bin")));
        torrent.write(("8:url-list"+seed.length()+":"+seed+"e").getBytes("UTF-8"));
        ExecutorService clients=Executors.newCachedThreadPool();
        Thread accept=new Thread(()->{while(!origin.isClosed())try{
            Socket socket=origin.accept();clients.execute(()->{try(Socket client=socket){
                client.setSoTimeout(5000);BufferedReader in=new BufferedReader(new InputStreamReader(client.getInputStream(),"US-ASCII"));
                String line=in.readLine();if(line==null)return;byte[] data=line.split(" ")[1].equals("/sample.torrent")?torrent.toByteArray():original;
                String range=null;while((line=in.readLine())!=null&&!line.isEmpty())if(line.toLowerCase().startsWith("range:"))range=line.substring(6).trim();
                long[] bounds=TorrentEngine.range(range,data.length);
                String headers="HTTP/1.1 "+(bounds[2]==1?"206 Partial Content":"200 OK")+"\r\nContent-Length: "+(bounds[1]-bounds[0]+1)+"\r\nConnection: close\r\n";
                if(bounds[2]==1)headers+="Content-Range: bytes "+bounds[0]+"-"+bounds[1]+"/"+data.length+"\r\n";
                OutputStream out=client.getOutputStream();out.write((headers+"\r\n").getBytes("US-ASCII"));out.write(data,(int)bounds[0],(int)(bounds[1]-bounds[0]+1));
            }catch(Exception ignored){}});
        }catch(IOException e){break;}},"Fixture HTTP");accept.setDaemon(true);accept.start();
        File cache=Files.createTempDirectory("telares-native-test").toFile();TorrentEngine engine=new TorrentEngine(cache,true);
        try{
            String local=engine.open(seed+"sample.torrent");
            try(InputStream in=new URL(local).openStream()){assertArrayEquals(original,in.readAllBytes());}
            HttpURLConnection request=(HttpURLConnection)new URL(local).openConnection();request.setRequestProperty("Range","bytes=500-999");
            assertEquals(206,request.getResponseCode());try(InputStream in=request.getInputStream()){assertArrayEquals(java.util.Arrays.copyOfRange(original,500,1000),in.readAllBytes());}request.disconnect();
        }finally{engine.close();origin.close();clients.shutdownNow();}
    }
}
