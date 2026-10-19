package br.com.shocktv;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import com.sun.net.httpserver.HttpServer;

public class TorrentIntegrationTest {
    @Test public void nativeWebSeedAndSeekRanges()throws Exception{
        Path fixtures=Paths.get(System.getProperty("telares.fixture"));
        if(!Files.exists(fixtures.resolve("info.bin")))throw new AssertionError("Playback fixtures missing");
        byte[] original=Files.readAllBytes(fixtures.resolve("sample.mp4"));
        HttpServer origin=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        String seed="http://127.0.0.1:"+origin.getAddress().getPort()+"/";
        ByteArrayOutputStream torrent=new ByteArrayOutputStream();torrent.write("d4:info".getBytes("UTF-8"));torrent.write(Files.readAllBytes(fixtures.resolve("info.bin")));
        torrent.write(("8:url-list"+seed.length()+":"+seed+"e").getBytes("UTF-8"));
        origin.createContext("/",exchange->{
            byte[] data=exchange.getRequestURI().getPath().equals("/sample.torrent")?torrent.toByteArray():original;
            try{long[] bounds=TorrentEngine.range(exchange.getRequestHeaders().getFirst("Range"),data.length);
                if(bounds[2]==1)exchange.getResponseHeaders().add("Content-Range","bytes "+bounds[0]+"-"+bounds[1]+"/"+data.length);
                exchange.sendResponseHeaders(bounds[2]==1?206:200,bounds[1]-bounds[0]+1);
                exchange.getResponseBody().write(data,(int)bounds[0],(int)(bounds[1]-bounds[0]+1));
            }finally{exchange.close();}
        });origin.start();
        File cache=Files.createTempDirectory("telares-native-test").toFile();TorrentEngine engine=new TorrentEngine(cache);
        try{
            String local=engine.open(seed+"sample.torrent");
            try(InputStream in=new URL(local).openStream()){assertArrayEquals(original,in.readAllBytes());}
            HttpURLConnection request=(HttpURLConnection)new URL(local).openConnection();request.setRequestProperty("Range","bytes=500-999");
            assertEquals(206,request.getResponseCode());try(InputStream in=request.getInputStream()){assertArrayEquals(java.util.Arrays.copyOfRange(original,500,1000),in.readAllBytes());}request.disconnect();
        }finally{engine.close();origin.stop(0);}
    }
}
