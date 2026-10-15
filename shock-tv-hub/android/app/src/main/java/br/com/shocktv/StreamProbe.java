package br.com.shocktv;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight network verification for legitimate direct HTTP(S) media.
 * Does not decode a movie, bypass a paywall, inspect torrent hashes or verify all HLS/DASH segments.
 * Never send credentials on redirects to a different host or over plaintext.
 */
public final class StreamProbe {
    private StreamProbe(){}
    public static final class Option {
        public String url="",label="",quality="Auto",language="Indefinido",mime="";
        public String[] headers=new String[0],values=new String[0];
        public String originalHost="";
        public int qualityRank=0;
        public long latencyMs=0;
        public JSONObject toJson(){
            JSONObject json=new JSONObject();
            try{
                json.put("url",url);json.put("label",label);json.put("quality",quality);
                json.put("language",language);json.put("mime",mime);
                JSONArray names=new JSONArray(),vals=new JSONArray();
                for(String h:headers)names.put(h);
                for(String v:values)vals.put(v);
                json.put("headerNames",names);json.put("headerValues",vals);
                json.put("qualityRank",qualityRank);
            }catch(Exception ignored){}
            return json;
        }
        public static Option fromJson(JSONObject json){
            Option o=new Option();
            o.url=json.optString("url","");o.label=json.optString("label","");
            o.quality=json.optString("quality","Auto");
            o.language=json.optString("language","Indefinido");
            o.mime=json.optString("mime","");
            o.qualityRank=json.optInt("qualityRank",0);
            JSONArray names=json.optJSONArray("headerNames"),values=json.optJSONArray("headerValues");
            if(names!=null&&values!=null&&names.length()==values.length()&&names.length()<=12){
                o.headers=new String[names.length()];o.values=new String[names.length()];
                for(int i=0;i<names.length();i++){
                    o.headers[i]=names.optString(i,"");o.values[i]=values.optString(i,"");
                }
            }
            return o;
        }
        public String shortLabel(){return quality+" · "+language;}
    }
    public static Option candidate(String url,String label,String[] headers,String[] values){
        Option o=new Option();o.url=url;o.label=label;
        o.headers=headers==null?new String[0]:headers;
        o.values=values==null?new String[0]:values;
        String info=label.toLowerCase(Locale.ROOT);
        if(info.contains("2160")||info.contains("4k")||info.contains("uhd")){
            o.quality="4K";o.qualityRank=2160;
        }else if(info.contains("1080")||info.contains("full hd")||info.contains("fhd")){
            o.quality="1080p";o.qualityRank=1080;
        }else if(info.contains("720")||info.contains(" hd ")){
            o.quality="720p";o.qualityRank=720;
        }else if(info.contains("480")||info.contains(" sd ")){
            o.quality="480p";o.qualityRank=480;
        }
        if(info.contains("dublado")||info.contains("pt-br")||info.contains("português")||
           info.contains("portugues")||info.contains("🇧🇷")){
            o.language="Português";
        }else if(info.contains("original")||info.contains("english")||info.contains("inglês")){
            o.language="Original/inglês";
        }else if(info.contains("legendado")){
            o.language="Legendado";
        }
        return o;
    }
    public static final class Report {
        public final ArrayList<Option> available=new ArrayList<>();
        public int failed=0,checked=0,redirected=0;
        public String firstFailure="";
        public JSONArray toJson(){
            JSONArray list=new JSONArray();
            for(Option o:available)list.put(o.toJson());
            return list;
        }
    }
    /** Test at most 24 candidates, four concurrent requests; fail closed for HTTP errors and HTML pages. */
    public static Report verify(List<Option> all){
        Report report=new Report();
        int count=Math.min(24,all.size());
        if(count==0)return report;
        ExecutorService workers=Executors.newFixedThreadPool(4);
        CompletionService<Option> results=new ExecutorCompletionService<>(workers);
        try {
            for(int i=0;i<count;i++){
                final Option candidate=all.get(i);
                results.submit(()->probe(candidate));
            }
            for(int i=0;i<count;i++){
                try{
                    Option o=results.take().get();
                    report.checked++;
                    if(o!=null)report.available.add(o);
                    else report.failed++;
                }catch(Exception e){
                    report.checked++;report.failed++;
                    if(report.firstFailure.isEmpty())report.firstFailure="Falha de rede ou resposta inválida";
                }
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        finally{workers.shutdownNow();}
        report.available.sort((a,b)->Integer.compare(score(b),score(a)));
        return report;
    }
    public static int score(Option o){
        int language="Português".equals(o.language)?35:0;
        int quality=o.qualityRank==1080?36:o.qualityRank==720?28:
            o.qualityRank==2160?22:o.qualityRank==480?12:14;
        int latency=(int)Math.min(15,Math.max(0,o.latencyMs/250));
        return quality+language-latency;
    }
    private static Option probe(Option original)throws Exception{
        URI originalUri=new URI(original.url);
        if(!http(originalUri)||originalUri.getUserInfo()!=null)return null;
        URI current=originalUri;
        boolean redirected=false;
        long start=System.currentTimeMillis();
        for(int hop=0;hop<6;hop++){
            HttpURLConnection conn=null;
            try{
                conn=(HttpURLConnection)current.toURL().openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(4200);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Range","bytes=0-2047");
                conn.setRequestProperty("Accept-Encoding","identity");
                conn.setRequestProperty("User-Agent","ShockTV/0.3.4 (Android TV)");
                // Only use addon-provided headers for original same-origin requests.
                boolean sameOrigin=origin(current).equals(origin(originalUri));
                for(int i=0;i<original.headers.length&&i<original.values.length;i++){
                    String name=original.headers[i],v=original.values[i];
                    if(name==null||v==null||v.length()>2048||v.indexOf('\r')>=0||v.indexOf('\n')>=0)continue;
                    if(!sameOrigin)continue;
                    if(("Authorization".equalsIgnoreCase(name)||"Cookie".equalsIgnoreCase(name))
                        &&!"https".equalsIgnoreCase(current.getScheme()))continue;
                    if("Range".equalsIgnoreCase(name)||"Host".equalsIgnoreCase(name))continue;
                    conn.setRequestProperty(name,v);
                }
                int status=conn.getResponseCode();
                if(status==301||status==302||status==303||status==307||status==308){
                    String target=conn.getHeaderField("Location");
                    if(target==null||target.isEmpty())return null;
                    URI next=current.resolve(target).normalize();
                    if(!http(next)||next.getUserInfo()!=null)return null;
                    // Do not downgrade from TLS, even if the CDN recommends it.
                    if("https".equalsIgnoreCase(current.getScheme())&&!"https".equalsIgnoreCase(next.getScheme()))return null;
                    current=next;redirected=true;continue;
                }
                if(status!=200&&status!=206)return null;
                String contentType=conn.getContentType();
                String mime=contentType==null?"":contentType.toLowerCase(Locale.ROOT);
                if(mime.contains("text/html")||mime.contains("application/json")||
                   mime.contains("text/xml"))return null;
                byte[] sample;
                try(InputStream input=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[1024];int n;
                    while(out.size()<2048&&(n=input.read(buffer,0,Math.min(buffer.length,2048-out.size())))>0){
                        out.write(buffer,0,n);
                    }
                    sample=out.toByteArray();
                }
                if(sample.length==0)return null;
                String preview=new String(sample,0,Math.min(sample.length,90),StandardCharsets.UTF_8)
                   .trim().toLowerCase(Locale.ROOT);
                if(preview.startsWith("<!doctype")||preview.startsWith("<html")||
                   preview.startsWith("{\"error\"")||preview.startsWith("<error")||
                   preview.startsWith("<?xml")&&!mime.contains("dash"))return null;
                String p=current.getPath().toLowerCase(Locale.ROOT);
                boolean manifestHls=p.endsWith(".m3u8")||mime.contains("mpegurl")||
                                    preview.startsWith("#extm3u");
                boolean manifestDash=p.endsWith(".mpd")||mime.contains("dash+xml")||
                                     preview.startsWith("<mpd");
                boolean media=mime.startsWith("video/")||mime.startsWith("audio/")||
                              mime.contains("octet-stream")||mime.contains("matroska")||
                              p.endsWith(".mp4")||p.endsWith(".webm")||p.endsWith(".mkv")||
                              p.endsWith(".ts")||p.endsWith(".m4v");
                if(!manifestHls&&!manifestDash&&!media)return null;
                Option good=Option.fromJson(original.toJson());
                good.url=current.toString();
                good.latencyMs=System.currentTimeMillis()-start;
                good.mime=manifestHls?"application/x-mpegURL":manifestDash?"application/dash+xml":mime;
                // Strip request headers when switching hosts; never persist Authorization or Cookies.
                if(!origin(current).equals(origin(originalUri))){
                    good.headers=new String[0];good.values=new String[0];
                }
                return good;
            }finally{if(conn!=null)conn.disconnect();}
        }
        return null;
    }
    private static boolean http(URI url){
        if(url.getHost()==null||url.getHost().isEmpty())return false;
        String p=url.getScheme();
        return "http".equalsIgnoreCase(p)||"https".equalsIgnoreCase(p);
    }
    private static String origin(URI url){
        int port=url.getPort();
        if(port<0)port="https".equalsIgnoreCase(url.getScheme())?443:80;
        return url.getScheme().toLowerCase(Locale.ROOT)+"://"+url.getHost().toLowerCase(Locale.ROOT)+":"+port;
    }
}
