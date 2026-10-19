package br.com.shocktv;

import java.net.URI;
import java.net.URLEncoder;
import java.util.Locale;
import org.json.JSONObject;

/** Protocol routing: a torrent is never passed to an HTTP decoder. */
public final class SmartPlayback {
    public static final String SUBTITLE_ADDON="https://opensubtitlesv3-pro.dexter21767.com/manifest.json";
    private SmartPlayback(){}
    public static boolean http(String value){
        try { URI u=new URI(value);return u.getHost()!=null&&u.getUserInfo()==null&&
            ("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()));
        } catch(Exception e){return false;}
    }
    public static String torrentHash(JSONObject stream){
        String hash=stream.optString("infoHash","").toLowerCase(Locale.ROOT);
        if(hash.matches("[a-f0-9]{40}"))return hash;
        String url=stream.optString("url","");
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)(?:[?&])xt=urn:btih:([a-f0-9]{40})(?:&|$)").matcher(url);
        return m.find()?m.group(1).toLowerCase(Locale.ROOT):"";
    }
    public static String torrentUrl(JSONObject stream,String server){
        String hash=torrentHash(stream);
        // Stremio-compatible streaming server resolves the selected torrent file.
        // No guessed file index: unknown/multi-file torrents go to a torrent application.
        int file=stream.optInt("fileIdx",-1);
        if(hash.isEmpty()||file<0||!http(server))return "";
        return server.replaceAll("/+$","")+"/"+hash+"/"+file;
    }
    public static String magnet(JSONObject stream){
        String original=stream.optString("url","");
        if(original.startsWith("magnet:?")&&original.length()<16384)return original;
        if(http(original)&&original.toLowerCase(Locale.ROOT).split("[?#]",2)[0].endsWith(".torrent"))return original;
        String hash=torrentHash(stream);if(hash.isEmpty())return "";
        String result="magnet:?xt=urn:btih:"+hash;
        int file=stream.optInt("fileIdx",-1);if(file>=0)result+="&so="+file;
        org.json.JSONArray sources=stream.optJSONArray("sources");
        if(sources!=null)for(int i=0;i<sources.length()&&i<12;i++){
            String tracker=sources.optString(i,"");
            if(tracker.startsWith("tracker:"))try{
                result+="&tr="+URLEncoder.encode(tracker.substring(8),"UTF-8");
            }catch(Exception ignored){}
        }
        return result;
    }
    public static int subtitleRank(String lang){
        String s=lang.toLowerCase(Locale.ROOT).replace('_','-');
        return s.equals("pob")||s.equals("pt-br")||s.equals("por-br")?0:
            s.equals("por")||s.equals("pt")||s.equals("pt-pt")?1:2;
    }
}
