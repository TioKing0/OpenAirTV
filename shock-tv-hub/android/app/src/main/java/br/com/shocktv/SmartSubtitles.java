package br.com.shocktv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.media3.common.C;
import androidx.media3.exoplayer.ExoPlayer;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Addon subtitles remain optional: errors never prevent playback. */
public final class SmartSubtitles {
    private final Activity activity;private final TextView overlay;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> tracks=new ArrayList<>();
    private SubtitleTimeline timeline;private ExoPlayer player;private boolean closed=false;
    private long delay=0;private int generation=0;private String status="Consultando legendas…";
    public SmartSubtitles(Activity a,FrameLayout root){
        activity=a;overlay=new TextView(a);overlay.setTextColor(0xffffffff);overlay.setTextSize(22);
        overlay.setGravity(Gravity.CENTER);overlay.setShadowLayer(5,2,2,0xff000000);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        p.bottomMargin=(int)(85*a.getResources().getDisplayMetrics().density);root.addView(overlay,p);
        ui.post(tick);
    }
    public void bind(ExoPlayer value){
        player=value;
        if(timeline!=null)player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());
    }
    private final Runnable tick=new Runnable(){public void run(){
        if(closed)return;overlay.setText(timeline!=null&&player!=null?timeline.at(player.getCurrentPosition(),delay):"");
        ui.postDelayed(this,200);
    }};
    private static String read(String url)throws Exception{
        if(!SmartPlayback.http(url))throw new IllegalArgumentException("URL de legenda inválida");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(5000);c.setReadTimeout(7000);c.setRequestProperty("User-Agent","Telares/RC13");
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>2000000)throw new IllegalArgumentException("Legenda muito grande");}
            return out.toString("UTF-8");
        }finally{c.disconnect();}
    }
    public void load(String[] addons,String type,String id){
        worker.execute(()->{
            ArrayList<JSONObject> found=new ArrayList<>();boolean config=false;
            if(addons!=null)for(String addon:addons)try{
                JSONObject m=new JSONObject(read(addon));JSONArray resources=m.optJSONArray("resources");
                boolean supports=false;
                if(resources!=null)for(int i=0;i<resources.length();i++){
                    Object r=resources.opt(i);JSONObject obj=r instanceof JSONObject?(JSONObject)r:null;
                    if(!"subtitles".equals(obj==null?r:obj.optString("name")))continue;
                    JSONArray types=obj!=null&&obj.has("types")?obj.optJSONArray("types"):m.optJSONArray("types");
                    JSONArray prefixes=obj!=null&&obj.has("idPrefixes")?obj.optJSONArray("idPrefixes"):m.optJSONArray("idPrefixes");
                    boolean match=types==null; if(types!=null)for(int n=0;n<types.length();n++)if(type.equals(types.optString(n)))match=true;
                    boolean prefix=prefixes==null||prefixes.length()==0;if(prefixes!=null)for(int n=0;n<prefixes.length();n++)if(id.startsWith(prefixes.optString(n)))prefix=true;
                    supports=match&&prefix;if(supports)break;
                }
                if(!supports)continue;
                JSONObject hints=m.optJSONObject("behaviorHints");
                if(hints!=null&&hints.optBoolean("configurationRequired",false)){config=true;continue;}
                String base=addon.substring(0,addon.length()-"manifest.json".length());
                JSONObject answer=new JSONObject(read(base+"subtitles/"+URLEncoder.encode(type,"UTF-8")+"/"+URLEncoder.encode(id,"UTF-8")+".json"));
                JSONArray arr=answer.optJSONArray("subtitles");
                if(arr!=null)for(int i=0;i<arr.length()&&found.size()<100;i++){
                    JSONObject s=arr.optJSONObject(i);if(s!=null&&SmartPlayback.http(s.optString("url","")))found.add(s);
                }
            }catch(Exception ignored){}
            final boolean needsConfig=config;found.sort((a,b)->Integer.compare(SmartPlayback.subtitleRank(a.optString("lang","")),SmartPlayback.subtitleRank(b.optString("lang",""))));
            ui.post(()->{if(closed)return;tracks.addAll(found);status=needsConfig?
                "OpenSubtitles Pro exige configuração. Em Fontes, abra Configurar OpenSubtitles e adicione o manifesto configurado.":"Nenhuma legenda externa disponível para este título.";
                if(!tracks.isEmpty()&&SmartPlayback.subtitleRank(tracks.get(0).optString("lang",""))==0)select(0);
            });
        });
    }
    private void select(int index){
        int token=++generation;timeline=null;overlay.setText("");
        if(player!=null)player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());
        String url=tracks.get(index).optString("url");
        worker.execute(()->{try{
            SubtitleTimeline parsed=SubtitleTimeline.parse(read(url));
            ui.post(()->{if(!closed&&token==generation){timeline=parsed;if(parsed.cues.isEmpty())new AlertDialog.Builder(activity).setMessage("Formato externo não suportado. Use uma legenda SRT/WebVTT ou as faixas do vídeo.").setPositiveButton("OK",null).show();}});
        }catch(Exception e){ui.post(()->{if(!closed&&token==generation)new AlertDialog.Builder(activity).setMessage("Não foi possível carregar a legenda selecionada.").setPositiveButton("OK",null).show();});}});
    }
    public void show(){
        ArrayList<String> names=new ArrayList<>();names.add("Desativar legendas externas");names.add("Faixas internas do vídeo (preferir português)");names.add("Sincronização · "+delay+" ms");
        for(JSONObject s:tracks)names.add(s.optString("lang","und")+" · "+s.optString("label",s.optString("id","Legenda")));
        new AlertDialog.Builder(activity).setTitle("Legendas · PT-BR preferencial")
            .setItems(names.toArray(new String[0]),(d,n)->{
                if(n<2){generation++;timeline=null;overlay.setText("");
                    if(player!=null)player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,n==0).setPreferredTextLanguages("pt-BR","pt","por").build());
                }else if(n==2)new AlertDialog.Builder(activity).setTitle("Ajustar legenda externa")
                    .setItems(new String[]{"Antecipar 500 ms","Atrasar 500 ms","Zerar"},(v,i)->{delay=i==2?0:Math.max(-60000,Math.min(60000,delay+(i==0?-500:500)));show();}).show();
                else select(n-3);
            }).setNeutralButton("Estado",(d,n)->new AlertDialog.Builder(activity).setMessage(status).setPositiveButton("OK",null).show())
            .setNegativeButton("Fechar",null).show();
    }
    public void close(){closed=true;generation++;ui.removeCallbacks(tick);worker.shutdownNow();player=null;}
}
