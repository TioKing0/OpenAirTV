package br.com.shocktv;

import org.libtorrent4j.*;
import org.libtorrent4j.alerts.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Embedded native torrent session. HTTP is private loopback transport to Media3. */
public final class TorrentEngine implements AutoCloseable {
    private final File root;
    private final SessionManager session=new SessionManager();
    private volatile boolean closed=false;
    private AddTorrentParams params;private TorrentHandle handle;private FileStorage files;private TorrentInfo info;
    private ServerSocket server;private File media;private long size,offset;private int pieceLength;
    private final ExecutorService requests=Executors.newFixedThreadPool(3);
    private final Object diskLock=new Object();private volatile CountDownLatch flushed;private volatile int requestedPiece=-1;private volatile boolean readOK;
    private final Set<Integer> onDisk=new HashSet<>();private final String token=UUID.randomUUID().toString();

    public TorrentEngine(File cache)throws IOException {this(cache,false);}
    TorrentEngine(File cache,boolean localFixture)throws IOException {
        root=new File(cache,"telares-torrent-"+UUID.randomUUID());
        if(!root.mkdirs())throw new IOException("Não foi possível criar o cache de vídeo");
        session.addListener(new AlertListener(){
            public int[] types(){return new int[]{AlertType.READ_PIECE.swig()};}
            public void alert(Alert<?> alert){ReadPieceAlert read=(ReadPieceAlert)alert;CountDownLatch latch=flushed;if(latch!=null&&read.piece()==requestedPiece){readOK=!read.error().isError();latch.countDown();}}
        });
        SettingsPack settings=new SettingsPack();settings.setEnableDht(true);settings.setEnableLsd(true);settings.connectionsLimit(60);settings.listenInterfaces("0.0.0.0:0");
        if(localFixture)settings.setBoolean(org.libtorrent4j.swig.settings_pack.bool_types.ssrf_mitigation.swigValue(),false);
        session.start(new SessionParams(settings));session.uploadRateLimit(128*1024);
    }
    private static boolean mediaPath(String name){return name.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|m4v|mkv|webm|mov|avi|ts|m2ts|mp3|aac|flac|ogg)$");}
    public static int chooseFile(String[] names,long[] sizes,int requested)throws IOException{
        if(requested>=0){if(requested>=names.length||!mediaPath(names[requested]))throw new IOException("Arquivo do addon não é uma mídia válida");return requested;}
        int chosen=-1;for(int i=0;i<names.length;i++)if(mediaPath(names[i])){
            if(chosen>=0)throw new IOException("Torrent com vários vídeos sem índice do episódio");chosen=i;
        }
        if(chosen<0)throw new IOException("Torrent não contém vídeo ou áudio suportado");return chosen;
    }
    public String open(String source)throws Exception{
        int requested=-1;
        if(source.startsWith("magnet:?")){
            for(String part:source.substring(8).split("&"))if(part.matches("so=\\d+"))requested=Integer.parseInt(part.substring(3));
            byte[] metadata=session.fetchMagnet(source,60,root);
            if(metadata==null)throw new IOException("Fonte BitTorrent sem metadados ou peers disponíveis");
            loadMetadata(metadata);
        }else{
            URI uri=new URI(source);String fragment=uri.getFragment();
            if(fragment!=null&&fragment.matches("telaresFile=\\d+"))requested=Integer.parseInt(fragment.substring(12));
            if(!SmartPlayback.http(source))throw new IOException("Endereço torrent inválido");
            HttpURLConnection conn=(HttpURLConnection)uri.toURL().openConnection();conn.setConnectTimeout(10000);conn.setReadTimeout(15000);
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>4000000)throw new IOException("Metadados torrent muito grandes");}
                loadMetadata(out.toByteArray());
            }finally{conn.disconnect();}
        }
        if(closed)throw new InterruptedException("Cancelado");
        files=info.files();String[] names=new String[files.numFiles()];long[] sizes=new long[names.length];
        String prefix=root.getCanonicalPath()+File.separator;
        for(int i=0;i<names.length;i++){
            names[i]=files.filePath(i);sizes[i]=files.fileSize(i);
            if(!new File(root,names[i]).getCanonicalPath().startsWith(prefix)||
                files.fileFlags(i).and_(FileStorage.FLAG_SYMLINK).non_zero())throw new IOException("Caminho inseguro no torrent");
        }
        int selected=chooseFile(names,sizes,requested);size=sizes[selected];offset=files.fileOffset(selected);pieceLength=info.pieceLength();
        if(size<=0||size>20L*1024*1024*1024)throw new IOException("Arquivo excede o limite de cache de 20 GiB");
        if(root.getUsableSpace()<size+128L*1024*1024)throw new IOException("Espaço insuficiente para o cache do vídeo");
        media=new File(root,names[selected]);
        Priority[] priorities=Priority.array(Priority.IGNORE,names.length);priorities[selected]=Priority.DEFAULT;
        params.setSavePath(root.getAbsolutePath());params.filePriorities(priorities);
        params.setFlags(params.getFlags().and_(TorrentFlags.PAUSED.inv()).and_(TorrentFlags.AUTO_MANAGED.inv()));
        new SessionHandle(session.swig()).asyncAddTorrent(params);
        long deadline=System.currentTimeMillis()+10000;
        while(!closed&&(handle=session.find(info.infoHash()))==null){if(System.currentTimeMillis()>deadline)throw new IOException("Motor BitTorrent não iniciou");Thread.sleep(100);}
        if(closed)throw new InterruptedException("Cancelado");
        handle.resume();
        server=new ServerSocket(0,3,InetAddress.getByName("127.0.0.1"));
        Thread accept=new Thread(()->{
            while(!closed)try{Socket socket=server.accept();socket.setSoTimeout(10000);
                if(((ThreadPoolExecutor)requests).getActiveCount()>=3){socket.close();continue;}
                requests.execute(()->serve(socket));
            }catch(IOException ignored){if(!closed)close();}
        },"Telares-Torrent-HTTP");accept.setDaemon(true);accept.start();
        return "http://127.0.0.1:"+server.getLocalPort()+"/"+token;
    }
    private void loadMetadata(byte[] bytes)throws IOException{
        File metadata=new File(root,".metadata");
        try(FileOutputStream out=new FileOutputStream(metadata)){out.write(bytes);}
        try{params=new AddTorrentParams(org.libtorrent4j.swig.libtorrent.load_torrent_file(metadata.getAbsolutePath()));info=params.getTorrentInfo();
            if(info==null||!info.isValid())throw new IOException("Metadados torrent inválidos");
        }finally{metadata.delete();}
    }
    public static long[] range(String header,long size)throws IOException{
        if(header==null)return new long[]{0,size-1,0};
        if(!header.matches("bytes=\\d*-\\d*"))throw new IOException("Range inválido");
        String[] pair=header.substring(6).split("-",-1);
        try{
            if(pair[0].isEmpty()&&pair[1].isEmpty())throw new IOException("Range vazio");
            long start=pair[0].isEmpty()?Math.max(0,size-Long.parseLong(pair[1])):Long.parseLong(pair[0]);
            long end=pair[0].isEmpty()||pair[1].isEmpty()?size-1:Math.min(size-1,Long.parseLong(pair[1]));
            if(start>end||start>=size)throw new IOException("Range fora do arquivo");return new long[]{start,end,1};
        }catch(NumberFormatException e){throw new IOException("Range inválido");}
    }
    private void ready(int piece)throws Exception{
        long end=System.currentTimeMillis()+45000;handle.setPieceDeadline(piece,0);
        for(int p=piece+1;p<Math.min(info.numPieces(),piece+5);p++)handle.setPieceDeadline(p,(p-piece)*500);
        while(!closed&&!handle.havePiece(piece)){if(System.currentTimeMillis()>end)throw new IOException("Buffer BitTorrent sem dados");Thread.sleep(100);}
        if(closed)throw new InterruptedException("Cancelado");
        // Wait for disk writes before reading; sparse unwritten bytes must never reach the decoder.
        if(!onDisk.contains(piece)){
            CountDownLatch latch=new CountDownLatch(1);flushed=latch;requestedPiece=piece;readOK=false;handle.readPiece(piece);
            if(!latch.await(10,TimeUnit.SECONDS)||!readOK)throw new IOException("Cache torrent não foi confirmado em disco");
            onDisk.add(piece);if(onDisk.size()>256)onDisk.clear();flushed=null;
        }
    }
    private void serve(Socket socket){
        try(Socket client=socket){
            BufferedReader reader=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.US_ASCII));
            String first=reader.readLine();if(first==null||first.length()>4096)return;
            String[] request=first.split(" ");String host="",range=null;int bytes=0;
            for(String line;(line=reader.readLine())!=null&&!line.isEmpty();){
                bytes+=line.length();if(bytes>8192)return;int n=line.indexOf(':');if(n<0)continue;
                String name=line.substring(0,n).toLowerCase(Locale.ROOT),value=line.substring(n+1).trim();if(name.equals("range"))range=value;if(name.equals("host"))host=value;
            }
            OutputStream out=client.getOutputStream();
            if(request.length<2||!request[1].equals("/"+token)||!host.equals("127.0.0.1:"+server.getLocalPort())||!(request[0].equals("GET")||request[0].equals("HEAD"))){out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));return;}
            long[] bounds;
            try{bounds=range(range,size);}catch(IOException e){out.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */"+size+"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));return;}
            String header="HTTP/1.1 "+(bounds[2]==1?"206 Partial Content":"200 OK")+"\r\nContent-Type: application/octet-stream\r\nAccept-Ranges: bytes\r\nContent-Length: "+(bounds[1]-bounds[0]+1)+"\r\nConnection: close\r\n";
            if(bounds[2]==1)header+="Content-Range: bytes "+bounds[0]+"-"+bounds[1]+"/"+size+"\r\n";
            out.write((header+"\r\n").getBytes(StandardCharsets.US_ASCII));out.flush();if(request[0].equals("HEAD"))return;
            byte[] buffer=new byte[65536];long position=bounds[0];
            while(position<=bounds[1]&&!closed){
                int piece=(int)((offset+position)/pieceLength);int count=(int)Math.min(buffer.length,Math.min(bounds[1]-position+1,pieceLength-(offset+position)%pieceLength));
                synchronized(diskLock){ready(piece);try(RandomAccessFile file=new RandomAccessFile(media,"r")){file.seek(position);file.readFully(buffer,0,count);}}
                out.write(buffer,0,count);position+=count;
            }
        }catch(Exception error){System.err.println("Torrent transport: "+error.getClass().getSimpleName()+": "+error.getMessage());} // A stalled source closes the stream and Media3 tries the next candidate.
    }
    @Override public void close(){
        if(closed)return;closed=true;CountDownLatch latch=flushed;if(latch!=null)latch.countDown();
        try{if(server!=null)server.close();}catch(IOException ignored){}requests.shutdownNow();
        Thread cleanup=new Thread(()->{session.stop();delete(root);},"Telares-Torrent-Cleanup");cleanup.setDaemon(true);cleanup.start();
    }
    public void pause(){if(!closed)session.pause();}
    public void resume(){if(!closed)session.resume();}
    private static void delete(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);file.delete();}
}

