package br.com.shocktv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG=0xff091420, PANEL=0xff14283a, PANEL2=0xff1b3447, AQUA=0xff68d8e9;
    private static final String CINEMETA="https://v3-cinemeta.strem.io/manifest.json";
    private final ExecutorService pool=Executors.newFixedThreadPool(5);
    private final ArrayList<String> sources=new ArrayList<>();
    private LinearLayout content;
    private TextView subtitle;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        loadSavedSources();
        render();
        for (String source : new ArrayList<>(sources)) loadSource(source);
    }

    private void loadSavedSources() {
        sources.add(CINEMETA);
        try {
            JSONArray array=new JSONArray(getPreferences(0).getString("sources","[]"));
            for(int i=0;i<array.length()&&i<25;i++) {
                String url=array.optString(i,"");
                if(url.startsWith("https://")&&!sources.contains(url)) sources.add(url);
            }
        } catch(Exception ignored) {}
    }
    private void saveSources() {
        JSONArray array=new JSONArray();
        for(String url:sources) if(!url.equals(CINEMETA))array.put(url);
        getPreferences(0).edit().putString("sources",array.toString()).apply();
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int stroke) {
        GradientDrawable g=new GradientDrawable();
        g.setColor(color);g.setCornerRadius(dp(17));
        if(stroke!=0)g.setStroke(dp(2),stroke);
        return g;
    }
    private TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private void render() {
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(42),dp(22),dp(42),dp(40));
        scroll.addView(content);

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=text("SHOCK  TV",26,AQUA,true);
        header.addView(logo,new LinearLayout.LayoutParams(0,dp(53),1f));
        Button b=button("＋ Adicionar addon",()->addSourceDialog());
        header.addView(b);content.addView(header);

        View divider=new View(this);divider.setBackgroundColor(0xff26445b);
        content.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));

        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(25),dp(27),dp(25),dp(26));
        LinearLayout.LayoutParams heroLp=new LinearLayout.LayoutParams(-1,-2);heroLp.topMargin=dp(20);
        hero.setBackground(shape(0xff11354d,0xff275d70));
        content.addView(hero,heroLp);
        hero.addView(text("SEU CINEMA. SUA TV.",12,AQUA,true));
        TextView headline=text("Tudo em uma tela.",34,Color.WHITE,true);
        LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(-1,-2);h.topMargin=dp(7);
        hero.addView(headline,h);
        TextView desc=text("Explore catálogos compatíveis com o protocolo Stremio. Adicione seus addons e reproduza mídias autorizadas.",15,0xffc3d7e2,false);
        desc.setMaxLines(2);hero.addView(desc);
        LinearLayout nav=new LinearLayout(this);nav.setPadding(0,dp(10),0,0);
        nav.addView(button("Reproduzir URL",()->playDirect()));
        nav.addView(button("Minhas fontes",()->showSources()));
        hero.addView(nav);

        subtitle=text("Carregando catálogos...",14,0xff85a7ba,false);
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,-2);slp.topMargin=dp(15);
        content.addView(subtitle,slp);
        content.addView(text("Catálogos reais carregados dos seus addons. Sem itens de demonstração.",12,0xff819baa,false));
        setContentView(scroll);
    }

    private Button button(String label,Runnable task){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);
        b.setTextColor(Color.WHITE);b.setTextSize(13);b.setBackground(shape(0xff21617a,0xff3a899e));
        b.setPadding(dp(20),0,dp(20),0);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(47));p.rightMargin=dp(12);
        b.setLayoutParams(p);
        b.setOnClickListener(v->task.run());b.setOnFocusChangeListener((v,f)->v.animate().scaleX(f?1.05f:1f).scaleY(f?1.05f:1f).setDuration(125).start());
        return b;
    }
    private void addSourceDialog() {
        EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        field.setHint("https://exemplo.org/manifest.json");
        LinearLayout wrap=new LinearLayout(this);wrap.setPadding(dp(24),dp(10),dp(24),0);wrap.addView(field);
        new AlertDialog.Builder(this).setTitle("Adicionar addon Stremio")
            .setMessage("Digite a URL HTTPS do manifest de um addon que você tem autorização para usar.")
            .setView(wrap).setNegativeButton("Cancelar",null)
            .setPositiveButton("Verificar",(d,w)->{
                String url=field.getText().toString().trim();
                if(!url.startsWith("https://")||!url.endsWith("/manifest.json")) {
                    message("Informe uma URL HTTPS terminada em /manifest.json.");return;
                }
                if(sources.contains(url)){message("Essa fonte já está cadastrada.");return;}
                if(sources.size()>=25){message("Limite atual de 25 fontes.");return;}
                validateSource(url);
            }).show();
    }

    private void validateSource(String url){
        subtitle.setText("Verificando addon...");
        pool.execute(()->{
            try {
                JSONObject json=getJson(url);
                if(json.optString("id").isEmpty()||json.optString("name").isEmpty()||!json.has("resources"))throw new Exception("Manifest incompleto.");
                String label=json.optString("name");
                runOnUiThread(()->{sources.add(url);saveSources();message("Fonte adicionada: "+label);loadSource(url);});
            } catch(Exception e){runOnUiThread(()->message("Não foi possível validar o addon: "+e.getMessage()));}
        });
    }
    private void showSources(){
        String[] names=new String[sources.size()];
        for(int i=0;i<sources.size();i++)names[i]=sources.get(i);
        new AlertDialog.Builder(this).setTitle("Fontes ativas ("+sources.size()+")")
            .setItems(names,(d,which)->{
                if(which==0){message("Cinemeta é a fonte de metadados padrão.");return;}
                new AlertDialog.Builder(this).setTitle("Remover esta fonte?")
                    .setMessage(names[which]).setNegativeButton("Cancelar",null)
                    .setPositiveButton("Remover",(dd,w)->{sources.remove(names[which]);saveSources();render();for(String s:sources)loadSource(s);}).show();
            }).setPositiveButton("Adicionar",(d,w)->addSourceDialog()).setNegativeButton("Fechar",null).show();
    }

    private void loadSource(String manifestUrl){
        pool.execute(()->{
            try {
                JSONObject manifest=getJson(manifestUrl);
                JSONArray catalogs=manifest.optJSONArray("catalogs");
                if(catalogs==null)return;
                int loaded=0;
                String base=manifestUrl.substring(0,manifestUrl.length()-"manifest.json".length());
                for(int i=0;i<catalogs.length()&&loaded<4;i++){
                    JSONObject cat=catalogs.optJSONObject(i);if(cat==null)continue;
                    String type=cat.optString("type"),id=cat.optString("id");
                    if(!("movie".equals(type)||"series".equals(type)||"tv".equals(type)||"channel".equals(type)))continue;
                    if(!id.matches("[a-zA-Z0-9_.:-]{1,100}"))continue;
                    try{
                        String endpoint=base+"catalog/"+type+"/"+id+".json";
                        JSONObject resp=getJson(endpoint);
                        JSONArray metas=resp.optJSONArray("metas");
                        if(metas==null||metas.length()==0)continue;
                        final String section=manifest.optString("name","Addon")+" • "+cat.optString("name",type);
                        ArrayList<Media> found=new ArrayList<>();
                        for(int j=0;j<metas.length()&&j<18;j++){
                            JSONObject m=metas.optJSONObject(j);if(m==null)continue;
                            Media media=new Media();
                            media.name=m.optString("name","Sem título");media.id=m.optString("id","");
                            media.type=m.optString("type",type);
                            media.poster=m.optString("poster","");
                            media.description=m.optString("description","Metadados do addon: "+manifest.optString("name"));
                            media.source=base;found.add(media);
                        }
                        loaded++;
                        runOnUiThread(()->addCatalog(section,found));
                    }catch(Exception ignored){}
                }
                runOnUiThread(()->subtitle.setText("CATÁLOGOS DISPONÍVEIS • "+sources.size()+" FONTE(S)"));
            } catch(Exception ex){runOnUiThread(()->subtitle.setText("Alguns addons não responderam. Verifique a rede ou as fontes."));}
        });
    }
    private void addCatalog(String header,ArrayList<Media> media){
        LinearLayout block=new LinearLayout(this);block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(18);
        content.addView(block,lp);
        TextView heading=text(header.toUpperCase(),16,AQUA,true);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=dp(13);
        block.addView(heading,hp);
        HorizontalScrollView row=new HorizontalScrollView(this);row.setHorizontalScrollBarEnabled(false);
        row.setClipChildren(false);
        LinearLayout items=new LinearLayout(this);items.setClipChildren(false);
        for(Media m:media){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setFocusable(true);card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setBackground(shape(PANEL,0xff284357));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(138),dp(228));
            cp.rightMargin=dp(17);items.addView(card,cp);
            ImageView poster=new ImageView(this);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackgroundColor(0xff224055);
            card.addView(poster,new LinearLayout.LayoutParams(-1,dp(170)));
            TextView label=text(m.name,12,Color.WHITE,true);label.setMaxLines(2);
            label.setPadding(dp(8),dp(7),dp(8),0);card.addView(label);
            card.setOnFocusChangeListener((v,f)->{
                v.setBackground(shape(f?PANEL2:PANEL,f?AQUA:0xff284357));
                v.animate().scaleX(f?1.05f:1f).scaleY(f?1.05f:1f).setDuration(120).start();
            });
            card.setOnClickListener(v->details(m));
            if(m.poster.startsWith("https://"))loadPoster(m.poster,poster);
        }
        row.addView(items);block.addView(row,new LinearLayout.LayoutParams(-1,dp(244)));
    }
    private void loadPoster(String url,ImageView image){
        pool.execute(()->{
            try {
                HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(7000);c.setReadTimeout(8000);
                try(InputStream in=c.getInputStream()){
                    Bitmap bmp=BitmapFactory.decodeStream(in);
                    if(bmp!=null)runOnUiThread(()->image.setImageBitmap(bmp));
                }finally{c.disconnect();}
            }catch(Exception ignored){}
        });
    }
    /** Catalog metadata is separate from playable streams. Never imply that an item is playable before checking. */
    private void details(Media m){
        new AlertDialog.Builder(this).setTitle(m.name)
          .setMessage(m.description+"\n\nConsultaremos as fontes conectadas para encontrar um stream reproduzível.")
          .setNegativeButton("Cancelar",null)
          .setPositiveButton("Encontrar reprodução",(d,w)->resolveItem(m))
          .show();
    }
    private void resolveItem(Media m) {
        if ("series".equals(m.type)) {
            subtitle.setText("Consultando episódios de "+m.name+"...");
            pool.execute(()->{
                try {
                    JSONObject meta=getJson(m.source+"meta/"+m.type+"/"+encodePart(m.id)+".json").optJSONObject("meta");
                    JSONArray videos=meta!=null?meta.optJSONArray("videos"):null;
                    if(videos==null||videos.length()==0) {
                        runOnUiThread(()->message("Este catálogo ainda não informou episódios reproduzíveis."));
                        return;
                    }
                    ArrayList<String> labels=new ArrayList<>();
                    ArrayList<String> ids=new ArrayList<>();
                    for(int i=0;i<videos.length()&&i<250;i++) {
                        JSONObject episode=videos.optJSONObject(i);if(episode==null)continue;
                        String id=episode.optString("id","");
                        if(id.isEmpty())continue;
                        ids.add(id);
                        labels.add("T"+episode.optInt("season",0)+" E"+episode.optInt("episode",0)+"  •  "+episode.optString("title",m.name));
                    }
                    runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Episódios de "+m.name)
                       .setItems(labels.toArray(new String[0]),(dialog,index)->resolveStreams(m,ids.get(index)))
                       .setNegativeButton("Voltar",null).show());
                }catch(Exception ex){runOnUiThread(()->message("Não foi possível consultar os episódios: "+ex.getMessage()));}
            });
        }else{
            resolveStreams(m,m.id);
        }
    }
    private String encodePart(String value) throws Exception {
        return URLEncoder.encode(value,"UTF-8").replace("+","%20");
    }
    /** Query stream resource only from addons which declare stream capability. */
    private void resolveStreams(Media media,String videoId) {
        subtitle.setText("Buscando streams para "+media.name+"...");
        pool.execute(()->{
            ArrayList<String> playable=new ArrayList<>();
            ArrayList<String> titles=new ArrayList<>();
            ArrayList<String> sourcesChecked=new ArrayList<>(sources);
            for(String manifestUrl:sourcesChecked) {
                try {
                    JSONObject manifest=getJson(manifestUrl);
                    if(!supportsStreams(manifest,media.type,videoId))continue;
                    String root=manifestUrl.substring(0,manifestUrl.length()-"manifest.json".length());
                    JSONObject response=getJson(root+"stream/"+encodePart(media.type)+"/"+encodePart(videoId)+".json");
                    JSONArray streams=response.optJSONArray("streams");
                    if(streams==null)continue;
                    for(int i=0;i<streams.length()&&i<30;i++) {
                        JSONObject stream=streams.optJSONObject(i);
                        if(stream==null)continue;
                        String url=stream.optString("url","");
                        if(!(url.startsWith("https://")||url.startsWith("http://")))continue;
                        // Native Media3 supports HTTP(S) streams. Torrent hashes and external pages are not media URLs.
                        if(stream.optJSONObject("behaviorHints")!=null &&
                             stream.optJSONObject("behaviorHints").optJSONObject("proxyHeaders")!=null)continue;
                        if(playable.contains(url))continue;
                        playable.add(url);
                        String label=stream.optString("name","Stream");
                        String desc=stream.optString("title",stream.optString("description",""));
                        titles.add(manifest.optString("name","Addon")+" • "+label+(desc.isEmpty()?"":" • "+desc));
                    }
                }catch(Exception ignored){}
            }
            runOnUiThread(()->{
                subtitle.setText("CATÁLOGOS DISPONÍVEIS • "+sources.size()+" FONTE(S)");
                if(playable.size()==0) {
                    message("Nenhuma fonte de reprodução HTTP(S) compatível foi encontrada para \""+media.name+
                      "\". Metadados não garantem reprodução. Adicione um addon que forneça streams acessíveis.");
                    return;
                }
                new AlertDialog.Builder(this).setTitle(media.name+" • "+playable.size()+" stream(s)")
                   .setItems(titles.toArray(new String[0]),(dialog,index)->play(playable.get(index),media.name))
                   .setNegativeButton("Voltar",null).show();
            });
        });
    }
    private boolean supportsStreams(JSONObject manifest,String type,String id) {
        JSONArray resources=manifest.optJSONArray("resources");
        if(resources==null)return false;
        boolean supports=false;
        for(int i=0;i<resources.length();i++) {
            Object resource=resources.opt(i);
            if("stream".equals(resource)){supports=true;break;}
            if(resource instanceof JSONObject) {
                JSONObject r=(JSONObject)resource;
                if(!"stream".equals(r.optString("name")))continue;
                JSONArray types=r.optJSONArray("types");
                if(types!=null && !contains(types,type))continue;
                JSONArray prefixes=r.optJSONArray("idPrefixes");
                if(prefixes!=null && !startsWithAny(prefixes,id))continue;
                supports=true;break;
            }
        }
        if(!supports)return false;
        JSONArray types=manifest.optJSONArray("types");
        if(types!=null&&!contains(types,type))return false;
        JSONArray prefixes=manifest.optJSONArray("idPrefixes");
        return prefixes==null||startsWithAny(prefixes,id);
    }
    private boolean contains(JSONArray arr,String value) {
        for(int i=0;i<arr.length();i++)if(value.equals(arr.optString(i)))return true;
        return false;
    }
    private boolean startsWithAny(JSONArray arr,String id) {
        for(int i=0;i<arr.length();i++)if(id.startsWith(arr.optString(i,"!NOT_A_PREFIX")))return true;
        return false;
    }
    private void playDirect(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("https://.../video.mp4 ou playlist.m3u8");
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(23),0,dp(23),0);box.addView(input);
        new AlertDialog.Builder(this).setTitle("Reproduzir mídia autorizada")
            .setMessage("Use um endereço direto de mídia MP4, HLS ou DASH.")
            .setView(box).setNegativeButton("Cancelar",null)
            .setPositiveButton("Assistir",(d,w)->{
                String url=input.getText().toString().trim();
                if(!(url.startsWith("https://")||url.startsWith("http://"))){message("Use um endereço HTTP(S).");return;}
                play(url,"Reprodução direta");
            }).show();
    }
    private void play(String url,String name){
        Intent intent=new Intent(this,PlayerActivity.class);
        intent.putExtra("url",url);intent.putExtra("title",name);startActivity(intent);
    }
    private void message(String text){new AlertDialog.Builder(this).setTitle("Shock TV").setMessage(text).setPositiveButton("OK",null).show();}
    private static final class Media{String id,type,name,poster,description,source;}
    private JSONObject getJson(String url) throws Exception{
        if(!url.startsWith("https://"))throw new Exception("Fonte HTTPS necessária");
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(8000);connection.setReadTimeout(12000);
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("User-Agent","ShockTV/0.1");
        connection.setInstanceFollowRedirects(true);
        try(InputStream in=connection.getInputStream()){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>3000000)throw new Exception("Resposta excede 3 MB");}
            return new JSONObject(out.toString("UTF-8"));
        }finally{connection.disconnect();}
    }
    @Override protected void onDestroy(){pool.shutdownNow();super.onDestroy();}
}
