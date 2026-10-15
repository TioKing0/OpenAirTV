package br.com.shocktv;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Private on-device collection. Metadata and playback state are recorded only for
 * items the viewer interacts with. No demo rows, analytics or external accounts.
 */
public final class UserLibrary {
    private static final String INDEX="stored_entries_v1";
    private final SharedPreferences prefs;

    public UserLibrary(Context context) {
        prefs=context.getApplicationContext().getSharedPreferences("shock_tv_library_v1",Context.MODE_PRIVATE);
    }

    public static String key(String type,String id) {
        return digest(type+"|"+id);
    }
    public static String episodeKey(String seriesId,String episodeId) {
        return digest("series|"+seriesId+"|"+episodeId);
    }
    public static String directKey(String url) {
        return digest("direct|"+url);
    }

    private static String digest(String input) {
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb=new StringBuilder();
            for(int i=0;i<hash.length;i++)sb.append(String.format(java.util.Locale.US,"%02x",hash[i]&0xff));
            return sb.toString();
        }catch(Exception e) { return Integer.toHexString(input.hashCode()); }
    }
    public static final class Entry {
        public String key="",id="",type="",title="",poster="",description="",source="",episodeId="",streamUrl="";
        public long positionMs=0,durationMs=0,lastPlayed=0,likedAt=0,laterAt=0;
        public boolean liked=false,watchLater=false,completed=false;

        public boolean canContinue(){
            return !completed && lastPlayed>0 && positionMs>=5000
              && (durationMs<=0 || positionMs < (long)(durationMs*.95));
        }
        public String progressLabel(){
            if(completed)return "Concluído";
            if(positionMs>0)return "Pausado em "+time(positionMs)+(durationMs>0?" de "+time(durationMs):"");
            return lastPlayed>0?"Assistido recentemente":"Ainda não iniciado";
        }
        private static String time(long millis){
            long sec=millis/1000,h=sec/3600,m=(sec%3600)/60,s=sec%60;
            if(h>0)return String.format(java.util.Locale.US,"%d:%02d:%02d",h,m,s);
            return String.format(java.util.Locale.US,"%02d:%02d",m,s);
        }
    }

    private static Entry parse(String key,String data) {
        Entry e=new Entry();e.key=key;
        if(data==null)return e;
        try {
            JSONObject j=new JSONObject(data);
            e.id=j.optString("id","");e.type=j.optString("type","");
            e.title=j.optString("title","");e.poster=j.optString("poster","");
            e.description=j.optString("description","");e.source=j.optString("source","");
            e.episodeId=j.optString("episodeId","");
            e.streamUrl=j.optString("streamUrl","");
            e.positionMs=j.optLong("positionMs",0);e.durationMs=j.optLong("durationMs",0);
            e.lastPlayed=j.optLong("lastPlayed",0);e.likedAt=j.optLong("likedAt",0);e.laterAt=j.optLong("laterAt",0);
            e.liked=j.optBoolean("liked",false);e.watchLater=j.optBoolean("watchLater",false);
            e.completed=j.optBoolean("completed",false);
        }catch(Exception ignored){}
        return e;
    }
    public synchronized Entry get(String key) {
        if(key==null||key.length()==0)return new Entry();
        return parse(key,prefs.getString("entry_"+key,null));
    }

    private void put(Entry e) {
        if(e.key==null||e.key.isEmpty())return;
        try {
            JSONObject j=new JSONObject();
            j.put("id",e.id);j.put("type",e.type);j.put("title",e.title);j.put("poster",e.poster);
            j.put("description",e.description);j.put("source",e.source);j.put("episodeId",e.episodeId);
            j.put("streamUrl",e.streamUrl);
            j.put("positionMs",e.positionMs);j.put("durationMs",e.durationMs);j.put("lastPlayed",e.lastPlayed);
            j.put("liked",e.liked);j.put("watchLater",e.watchLater);
            j.put("likedAt",e.likedAt);j.put("laterAt",e.laterAt);j.put("completed",e.completed);
            Set<String> ids=new HashSet<>(prefs.getStringSet(INDEX,Collections.emptySet()));
            ids.add(e.key);
            prefs.edit().putString("entry_"+e.key,j.toString()).putStringSet(INDEX,ids).apply();
        }catch(Exception ignored){}
    }

    public synchronized Entry identify(String key,String id,String type,String title,String poster,String desc,String source,String episodeId) {
        Entry e=get(key);
        if(e.key.isEmpty())e.key=key;
        // Never erase useful saved metadata with a missing/empty response.
        if(id!=null&&!id.isEmpty())e.id=id;
        if(type!=null&&!type.isEmpty())e.type=type;
        if(title!=null&&!title.isEmpty())e.title=title;
        if(poster!=null&&!poster.isEmpty())e.poster=poster;
        if(desc!=null&&!desc.isEmpty())e.description=desc;
        if(source!=null&&!source.isEmpty())e.source=source;
        if(episodeId!=null&&!episodeId.isEmpty())e.episodeId=episodeId;
        put(e);return e;
    }
    public synchronized Entry liked(String key,boolean value){
        Entry e=get(key);e.liked=value;e.likedAt=value?System.currentTimeMillis():0;put(e);return e;
    }
    public synchronized Entry later(String key,boolean value){
        Entry e=get(key);e.watchLater=value;e.laterAt=value?System.currentTimeMillis():0;put(e);return e;
    }
    public synchronized Entry start(String key,String url) {
        Entry e=get(key);e.streamUrl=url==null?"":url;
        e.lastPlayed=System.currentTimeMillis();e.completed=false;
        put(e);return e;
    }
    public synchronized void progress(String key,long position,long duration,boolean ended){
        Entry e=get(key);
        if(e.key.isEmpty()||e.lastPlayed==0)return;
        if(duration>0)e.durationMs=duration;
        if(ended){e.positionMs=0;e.completed=true;}
        else{
            e.positionMs=Math.max(0,position);
            // Preserve progress on pause, mark completed at the very end.
            if(e.durationMs>60000 && e.positionMs>=e.durationMs*0.98){
                e.positionMs=0;e.completed=true;
            }
        }
        put(e);
    }
    public synchronized ArrayList<Entry> list(String which) {
        ArrayList<Entry> entries=new ArrayList<>();
        Set<String> ids=new HashSet<>(prefs.getStringSet(INDEX,Collections.emptySet()));
        for(String id:ids){
            Entry e=get(id);
            boolean include;
            switch(which){
                case "continue":include=e.canContinue();break;
                case "later":include=e.watchLater;break;
                case "liked":include=e.liked;break;
                case "recent":include=e.lastPlayed>0;break;
                default:include=false;
            }
            if(include)entries.add(e);
        }
        Collections.sort(entries,(a,b)->Long.compare(sortTime(b,which),sortTime(a,which)));
        return entries;
    }
    private static long sortTime(Entry e,String which){
        if("liked".equals(which))return e.likedAt;
        if("later".equals(which))return e.laterAt;
        return e.lastPlayed;
    }
    public synchronized void removeHistory(String key){
        Entry e=get(key);e.lastPlayed=0;e.positionMs=0;e.durationMs=0;e.completed=false;e.streamUrl="";
        put(e);
    }
    public synchronized void clearHistory(){
        for(Entry e:list("recent"))removeHistory(e.key);
    }
    public synchronized void erase(String key){
        Set<String> ids=new HashSet<>(prefs.getStringSet(INDEX,Collections.emptySet()));
        ids.remove(key);
        prefs.edit().remove("entry_"+key).putStringSet(INDEX,ids).apply();
    }
}
