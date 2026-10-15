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
import android.widget.FrameLayout;
import android.util.LruCache;
import android.os.Handler;
import android.os.Looper;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import android.widget.Toast;
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
    // Bundled manifest addresses. A manifest is not a bundled video or proof of playback.
    private static final String FENIXFLIX="https://fenixflix.fenixhub.online/manifest.json";
    private static final String TORRENTIO="https://torrentio.strem.fun/manifest.json";
    private static final String NETFLIX_CATALOG="https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/manifest.json";
    private static final String PIRATEBAY_PLUS="https://thepiratebay-plus.strem.fun/manifest.json";
    private static final String[] BUNDLED_ADDONS={
        CINEMETA,FENIXFLIX,TORRENTIO,NETFLIX_CATALOG,PIRATEBAY_PLUS
    };
    private static final String[] BUNDLED_NAMES={
        "Cinemeta • Metadados","FenixFlix • Addon comunitário",
        "Torrentio • Busca de streams","Netflix Catalog • Metadados",
        "ThePirateBay+ • Busca de streams"
    };
    private final ExecutorService pool=Executors.newFixedThreadPool(5);
    private final ArrayList<String> sources=new ArrayList<>();
    private final Set<String> disabledBundled=new HashSet<>();
    private LinearLayout content;
    private TextView subtitle;
    private UserLibrary library;
    private LinearLayout personal;
    private final ArrayList<Media> knownItems=new ArrayList<>();
    private final LruCache<String,Bitmap> artwork=new LruCache<String,Bitmap>(12*1024*1024){
        @Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}
    };
    private final Handler ui=new Handler(Looper.getMainLooper());
    private String activeSection="inicio";
    private LinearLayout catalogArea;
    private LinearLayout spotlightHolder;
    private View browseRoot;
    private ScrollView browseScroll;
    private boolean showingDetails=false;
    private int browseOffset=0;
    private final Runnable refreshTask=()->refreshCatalogRows();


    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        library=new UserLibrary(this);
        loadSavedSources();
        render();
        for (String source : new ArrayList<>(sources)) loadSource(source);
    }

    private static boolean isBundled(String url) {
        for(String builtin:BUNDLED_ADDONS)if(builtin.equals(url))return true;
        return false;
    }
    private void loadSavedSources() {
        sources.clear();
        disabledBundled.clear();
        disabledBundled.addAll(getPreferences(0).getStringSet("disabled_bundled_addons_v1",
            java.util.Collections.emptySet()));
        for(String url:BUNDLED_ADDONS)
            if(!disabledBundled.contains(url))sources.add(url);
        try {
            // Preserve add-ons installed by the viewer on earlier app versions.
            JSONArray saved=new JSONArray(getPreferences(0).getString("sources","[]"));
            for(int i=0;i<saved.length()&&i<40;i++) {
                String url=saved.optString(i,"");
                if(url.startsWith("https://")&&!sources.contains(url)&&!isBundled(url))
                    sources.add(url);
            }
        }catch(Exception ignored){}
    }
    private void saveSources() {
        JSONArray custom=new JSONArray();
        for(String url:sources)if(!isBundled(url))custom.put(url);
        getPreferences(0).edit()
            .putString("sources",custom.toString())
            .putStringSet("disabled_bundled_addons_v1",new HashSet<>(disabledBundled))
            .apply();
    }
    private void reloadCatalogs(){
        knownItems.clear();
        render();
        for(String source:new ArrayList<>(sources))loadSource(source);
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
    /** 10-foot user interface: calm left rail, content first, consistent D-pad focus. */
    private void render() {
        showingDetails=false;
        LinearLayout shell=new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setBackgroundColor(BG);
        shell.setFocusableInTouchMode(true);

        LinearLayout sidebar=new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(13),dp(26),dp(8),dp(15));
        sidebar.setBackgroundColor(0xff0b1926);
        shell.addView(sidebar,new LinearLayout.LayoutParams(dp(176),-1));
        TextView logo=text("SHOCK",25,Color.WHITE,true);
        logo.setLetterSpacing(.08f);
        sidebar.addView(logo);
        TextView logo2=text("TV",19,AQUA,true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(38);
        sidebar.addView(logo2,lp);
        navItem(sidebar,"⌂","Início","inicio");
        navItem(sidebar,"▣","Filmes","filmes");
        navItem(sidebar,"▤","Séries","series");
        navItem(sidebar,"♡","Minha lista","minha");
        navItem(sidebar,"⌕","Buscar","buscar");
        navItem(sidebar,"⊞","Fontes","fontes");
        View fill=new View(this);sidebar.addView(fill,new LinearLayout.LayoutParams(1,0,1f));
        TextView foot=text("SHOCK TV\nv"+BuildConfig.VERSION_NAME,11,0xff648397,false);
        sidebar.addView(foot);

        browseScroll=new ScrollView(this);
        browseScroll.setClipToPadding(false);
        browseScroll.setFillViewport(true);
        browseScroll.setVerticalScrollBarEnabled(false);
        shell.addView(browseScroll,new LinearLayout.LayoutParams(0,-1,1f));
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(29),dp(24),dp(27),dp(65));
        browseScroll.addView(content);

        LinearLayout toolbar=new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(text(sectionLabel(activeSection),22,Color.WHITE,true),
                new LinearLayout.LayoutParams(0,dp(44),1f));
        toolbar.addView(text("SEU ENTRETENIMENTO",11,0xff8ca8b9,true));
        content.addView(toolbar);

        spotlightHolder=new LinearLayout(this);
        spotlightHolder.setOrientation(LinearLayout.VERTICAL);
        content.addView(spotlightHolder,new LinearLayout.LayoutParams(-1,-2));
        personal=new LinearLayout(this);
        personal.setOrientation(LinearLayout.VERTICAL);
        content.addView(personal,new LinearLayout.LayoutParams(-1,-2));
        catalogArea=new LinearLayout(this);
        catalogArea.setOrientation(LinearLayout.VERTICAL);
        content.addView(catalogArea,new LinearLayout.LayoutParams(-1,-2));
        subtitle=text("Catálogos conectados: "+sources.size(),12,0xff7994a8,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(30);
        content.addView(subtitle,sp);

        if("inicio".equals(activeSection)||"minha".equals(activeSection))showPersonal();
        updateSpotlight();
        refreshCatalogRows();
        browseRoot=shell;
        setContentView(shell);
        browseScroll.post(()->browseScroll.scrollTo(0,0));
    }
    private String sectionLabel(String key){
        if("filmes".equals(key))return "Filmes";
        if("series".equals(key))return "Séries";
        if("minha".equals(key))return "Minha biblioteca";
        return "Início";
    }
    private void navItem(LinearLayout parent,String glyph,String label,String route){
        TextView item=text(glyph+"   "+label,16,route.equals(activeSection)?Color.WHITE:0xffa9c0cd,route.equals(activeSection));
        item.setFocusable(true);
        item.setClickable(true);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(14),0,dp(3),0);
        item.setBackground(shape(route.equals(activeSection)?0xff19465b:0xff0b1926,route.equals(activeSection)?AQUA:0));
        LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(-1,dp(53));
        navParams.bottomMargin=dp(7);
        parent.addView(item,navParams);
        item.setOnClickListener(v->{
            if("buscar".equals(route)){searchCatalog();return;}
            if("fontes".equals(route)){showSources();return;}
            if(route.equals(activeSection))return;
            activeSection=route;render();
        });
        item.setOnFocusChangeListener((v,hasFocus)->{
            item.setTextColor(hasFocus?Color.WHITE:(route.equals(activeSection)?Color.WHITE:0xffa9c0cd));
            item.setBackground(shape(hasFocus?0xff205870:(route.equals(activeSection)?0xff19465b:0xff0b1926),
                    hasFocus?AQUA:(route.equals(activeSection)?0xff386e83:0)));
            v.animate().scaleX(hasFocus?1.025f:1f).setDuration(120).start();
        });
    }
    private void updateSpotlight(){
        if(spotlightHolder==null)return;
        spotlightHolder.removeAllViews();
        if(!"inicio".equals(activeSection))return;
        if(knownItems.isEmpty()){
            LinearLayout empty=new LinearLayout(this);
            empty.setPadding(dp(20),dp(14),dp(20),dp(15));
            empty.setBackground(shape(0xff142536,0));
            empty.addView(text("Carregando seus catálogos…",15,0xffb9d7e2,false));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(15);p.bottomMargin=dp(15);
            spotlightHolder.addView(empty,p);
            return;
        }
        Media featured=knownItems.get(0);
        LinearLayout hero=new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setBackground(shape(0xff162b3a,0));
        LinearLayout.LayoutParams hl=new LinearLayout.LayoutParams(-1,dp(232));
        hl.topMargin=dp(16);hl.bottomMargin=dp(21);
        spotlightHolder.addView(hero,hl);
        LinearLayout copy=new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(25),dp(15),dp(12),dp(16));
        hero.addView(copy,new LinearLayout.LayoutParams(0,-1,1.4f));
        copy.addView(text("EM DESTAQUE  ·  "+("series".equals(featured.type)?"SÉRIE":"FILME"),11,AQUA,true));
        TextView title=text(featured.name,31,Color.WHITE,true);title.setMaxLines(2);
        LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,-2);tl.topMargin=dp(7);
        copy.addView(title,tl);
        TextView desc=text(featured.description==null?"":featured.description,13,0xffb6cbd8,false);
        desc.setMaxLines(2);
        LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,-2);dl.topMargin=dp(5);
        copy.addView(desc,dl);
        LinearLayout buttons=new LinearLayout(this);
        LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(-1,-2);bl.topMargin=dp(11);
        copy.addView(buttons,bl);
        buttons.addView(button("Ver detalhes",()->details(featured)));
        buttons.addView(button("＋ Minha lista",()->{
            UserLibrary.Entry e=identify(featured);
            library.later(e.key,!e.watchLater);
            showPersonal();
            Toast.makeText(this,e.watchLater?"Removido da lista":"Salvo para mais tarde",Toast.LENGTH_SHORT).show();
        }));
        ImageView poster=new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackgroundColor(0xff203849);
        hero.addView(poster,new LinearLayout.LayoutParams(0,-1,.85f));
        if(featured.poster!=null&&featured.poster.startsWith("https://"))loadPoster(featured.poster,poster);
    }
    private void scheduleRefresh(){
        ui.removeCallbacks(refreshTask);
        ui.postDelayed(refreshTask,650L);
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
        field.setHint("https://servidor/manifest.json");
        LinearLayout wrap=new LinearLayout(this);
        wrap.setPadding(dp(24),dp(12),dp(24),0);
        wrap.addView(field);
        new AlertDialog.Builder(this).setTitle("Adicionar addon Stremio")
            .setMessage("Informe o endereço do manifesto HTTPS. A presença de um addon não garante vídeos reproduzíveis.")
            .setView(wrap).setNegativeButton("Cancelar",null)
            .setPositiveButton("Verificar",(d,w)->{
                String url=field.getText().toString().trim();
                if(!url.startsWith("https://")||!url.endsWith("/manifest.json")) {
                    message("Informe um HTTPS terminado em /manifest.json.");return;
                }
                if(sources.contains(url)){message("Esta fonte já está ativa.");return;}
                if(isBundled(url)){
                    disabledBundled.remove(url);
                    sources.add(url);
                    saveSources();
                    reloadCatalogs();
                    return;
                }
                if(sources.size()>=40){message("Limite atual de 40 fontes.");return;}
                validateSource(url);
            }).show();
    }
    private void validateSource(String url){
        pool.execute(()->{
            try {
                JSONObject json=getJson(url);
                if(json.optString("id").isEmpty()||json.optString("name").isEmpty()
                    ||!json.has("resources"))throw new Exception("Manifest incompleto.");
                String label=json.optString("name");
                runOnUiThread(()->{
                    if(!sources.contains(url)){
                        sources.add(url);saveSources();
                        reloadCatalogs();
                    }
                    message("Fonte adicionada: "+label);
                });
            }catch(Exception e){
                runOnUiThread(()->message("Não foi possível validar o addon: "+e.getMessage()));
            }
        });
    }
    private void showSources() {
        ArrayList<String> all=new ArrayList<>();
        ArrayList<String> labels=new ArrayList<>();
        for(int i=0;i<BUNDLED_ADDONS.length;i++){
            String url=BUNDLED_ADDONS[i];
            boolean active=sources.contains(url);
            all.add(url);
            labels.add((active?"●  ":"○  ")+BUNDLED_NAMES[i]+(active?"  ·  Ativado":"  ·  Desativado"));
        }
        for(String url:sources){
            if(isBundled(url))continue;
            all.add(url);
            labels.add("●  "+url);
        }
        new AlertDialog.Builder(this)
          .setTitle("Fontes • "+sources.size()+" ativa(s)")
          .setMessage("Os addons predefinidos podem ser ativados ou desativados. "
                    +"Fontes de torrent exigem mecanismo próprio e não são reproduzidas diretamente pelo Media3.")
          .setItems(labels.toArray(new String[0]),(dialog,which)->{
              String url=all.get(which);
              if(isBundled(url)){
                  if(sources.contains(url)){
                      sources.remove(url);
                      disabledBundled.add(url);
                  }else{
                      disabledBundled.remove(url);
                      sources.add(url);
                  }
                  saveSources();reloadCatalogs();showSources();
              }else{
                  new AlertDialog.Builder(this).setTitle("Remover addon?")
                    .setMessage(url).setNegativeButton("Cancelar",null)
                    .setPositiveButton("Remover",(d,w)->{
                        sources.remove(url);saveSources();
                        reloadCatalogs();showSources();
                    }).show();
              }
          })
          .setPositiveButton("Adicionar fonte",(d,w)->addSourceDialog())
          .setNegativeButton("Fechar",null).show();
    }
    private void loadSource(String manifestUrl){
        pool.execute(()->{
            try{
                JSONObject manifest=getJson(manifestUrl);
                JSONArray catalogs=manifest.optJSONArray("catalogs");
                if(catalogs==null)return;
                String base=manifestUrl.substring(0,manifestUrl.length()-"manifest.json".length());
                int loaded=0;
                for(int i=0;i<catalogs.length()&&loaded<7;i++){
                    JSONObject catalog=catalogs.optJSONObject(i);
                    if(catalog==null)continue;
                    String type=catalog.optString("type"),catalogId=catalog.optString("id");
                    if(!("movie".equals(type)||"series".equals(type)||"tv".equals(type)||"channel".equals(type)))continue;
                    if(!catalogId.matches("[a-zA-Z0-9_.:-]{1,100}"))continue;
                    try{
                        JSONObject response=getJson(base+"catalog/"+type+"/"+catalogId+".json");
                        JSONArray metas=response.optJSONArray("metas");
                        if(metas==null||metas.length()==0)continue;
                        ArrayList<Media> incoming=new ArrayList<>();
                        for(int j=0;j<metas.length()&&j<35;j++){
                            JSONObject meta=metas.optJSONObject(j);if(meta==null)continue;
                            String id=meta.optString("id","");
                            if(id.isEmpty())continue;
                            Media m=new Media();
                            m.name=meta.optString("name","Sem título");m.id=id;
                            m.type=meta.optString("type",type);
                            m.poster=meta.optString("poster","");
                            m.description=meta.optString("description","");
                            m.source=base;m.origin=manifest.optString("name","Addon");
                            m.catalogId=catalogId;
                            JSONArray genres=meta.optJSONArray("genres");
                            if(genres!=null)for(int g=0;g<genres.length();g++){
                                if(g>0)m.genre+=",";
                                m.genre+=genres.optString(g,"");
                            }
                            incoming.add(m);
                        }
                        loaded++;
                        runOnUiThread(()->{
                            // Stable metadata identity across addons and category results.
                            for(Media candidate:incoming){
                                Media prior=null;
                                for(Media existing:knownItems){
                                    if(existing.id.equals(candidate.id)&&existing.type.equals(candidate.type)){
                                        prior=existing;break;
                                    }
                                }
                                if(prior==null)knownItems.add(candidate);
                                else{
                                    if(prior.poster.isEmpty()&&!candidate.poster.isEmpty())prior.poster=candidate.poster;
                                    if(prior.genre.isEmpty()&&!candidate.genre.isEmpty())prior.genre=candidate.genre;
                                    if(prior.description.isEmpty()&&!candidate.description.isEmpty())prior.description=candidate.description;
                                }
                            }
                            scheduleRefresh();
                        });
                    }catch(Exception ignored){}
                }
            }catch(Exception ex){
                runOnUiThread(()->Toast.makeText(this,"Algumas fontes não responderam",Toast.LENGTH_SHORT).show());
            }
        });
    }
    private String identity(Media item){return item.type+"|"+item.id;}
    private boolean isSeries(Media m){return "series".equals(m.type);}
    private boolean isFilm(Media m){return "movie".equals(m.type);}
    private boolean isChannel(Media m){return "tv".equals(m.type)||"channel".equals(m.type);}
    private boolean hasGenre(Media m,String part){
        return m.genre!=null&&m.genre.toLowerCase(java.util.Locale.ROOT)
              .contains(part.toLowerCase(java.util.Locale.ROOT));
    }
    private void addRowFrom(String title,ArrayList<Media> inputs,Set<String> displayed){
        if(inputs.isEmpty())return;
        ArrayList<Media> items=new ArrayList<>();
        for(Media m:inputs){
            if(items.size()>=24)break;
            if(displayed.add(identity(m)))items.add(m);
        }
        if(items.isEmpty())return;
        addCatalog(title,items);
    }
    private interface MediaFilter{boolean matches(Media media);}
    private ArrayList<Media> matching(MediaFilter condition){
        ArrayList<Media> list=new ArrayList<>();
        for(Media m:knownItems)if(condition.matches(m))list.add(m);
        return list;
    }
    private void refreshCatalogRows(){
        if(catalogArea==null||showingDetails)return;
        catalogArea.removeAllViews();
        updateSpotlight();
        if("minha".equals(activeSection))return;
        Set<String> shown=new HashSet<>();
        if("filmes".equals(activeSection)||"series".equals(activeSection)){
            boolean movie="filmes".equals(activeSection);
            MediaFilter type=m->movie?isFilm(m):isSeries(m);
            addRowFrom("Em alta",matching(m->type.matches(m)&&!m.catalogId.toLowerCase(java.util.Locale.ROOT).contains("new")),shown);
            String[] genres={"Ação","Comédia","Drama","Suspense","Terror","Animação","Ficção científica","Documentário","Romance"};
            String[] words={"action","comedy","drama","thriller","horror","animation","sci-fi","documentary","romance"};
            for(int i=0;i<genres.length;i++){
                String term=words[i];
                addRowFrom(genres[i],matching(m->type.matches(m)&&hasGenre(m,term)),shown);
            }
            addRowFrom("Outros títulos",matching(type),shown);
        }else{
            addRowFrom("Novidades",matching(m->m.catalogId.toLowerCase(java.util.Locale.ROOT).contains("new")),shown);
            addRowFrom("Filmes para descobrir",matching(this::isFilm),shown);
            addRowFrom("Séries para maratonar",matching(this::isSeries),shown);
            addRowFrom("Canais e TV",matching(this::isChannel),shown);
        }
        if(knownItems.isEmpty()){
            TextView empty=text("Nenhum catálogo carregado. Verifique sua internet ou configure uma fonte em Fontes.",15,0xffb1c8d5,false);
            empty.setPadding(dp(5),dp(25),dp(5),dp(25));catalogArea.addView(empty);
        }
    }
    private void addCatalog(String header,ArrayList<Media> media){
        LinearLayout block=new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(23);
        catalogArea.addView(block,lp);
        TextView heading=text(header,19,Color.WHITE,true);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.bottomMargin=dp(15);
        block.addView(heading,hp);
        HorizontalScrollView row=new HorizontalScrollView(this);
        row.setHorizontalScrollBarEnabled(false);row.setClipChildren(false);
        row.setClipToPadding(false);
        LinearLayout items=new LinearLayout(this);items.setClipChildren(false);items.setPadding(dp(7),0,dp(14),dp(6));
        for(Media m:media){
            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);card.setFocusable(true);
            card.setBackground(shape(0xff152537,0));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(156),dp(257));
            cp.rightMargin=dp(15);items.addView(card,cp);
            ImageView poster=new ImageView(this);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackgroundColor(0xff263647);
            card.addView(poster,new LinearLayout.LayoutParams(-1,dp(218)));
            TextView label=text(m.name,13,0xffe9f3fa,true);label.setMaxLines(2);
            label.setPadding(dp(7),dp(7),dp(7),0);
            card.addView(label,new LinearLayout.LayoutParams(-1,dp(37)));
            card.setOnClickListener(v->details(m));
            card.setOnFocusChangeListener((v,focused)->{
                v.setBackground(shape(focused?0xff25465c:0xff152537,focused?AQUA:0));
                v.setElevation(dp(focused?14:0));
                v.animate().scaleX(focused?1.06f:1f).scaleY(focused?1.06f:1f).setDuration(125).start();
                label.setTextColor(focused?AQUA:0xffe9f3fa);
            });
            if(m.poster!=null&&m.poster.startsWith("https://"))loadPoster(m.poster,poster);
        }
        row.addView(items);
        block.addView(row,new LinearLayout.LayoutParams(-1,dp(278)));
    }
    /** Sample poster bitmaps at TV-card resolution. Keep a small cache to avoid OOM on low-memory TVs. */
    private void loadPoster(String url,ImageView image){
        Bitmap cached=artwork.get(url);
        if(cached!=null){image.setImageBitmap(cached);return;}
        pool.execute(()->{
            HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)new URL(url).openConnection();
                connection.setConnectTimeout(5500);connection.setReadTimeout(6500);
                connection.setRequestProperty("Accept","image/webp,image/jpeg,image/png");
                if(connection.getContentLength()>2500000)throw new Exception("Poster too large");
                byte[] bytes;
                try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int n;
                    while((n=in.read(buffer))!=-1){
                        out.write(buffer,0,n);
                        if(out.size()>2500000)throw new Exception("Poster limit exceeded");
                    }
                    bytes=out.toByteArray();
                }
                BitmapFactory.Options bounds=new BitmapFactory.Options();
                bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                int step=1;
                while(bounds.outWidth/step>420||bounds.outHeight/step>620)step*=2;
                BitmapFactory.Options sample=new BitmapFactory.Options();
                sample.inSampleSize=step;sample.inPreferredConfig=Bitmap.Config.RGB_565;
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,sample);
                if(bitmap!=null){
                    artwork.put(url,bitmap);
                    runOnUiThread(()->{
                        if(image.isAttachedToWindow())image.setImageBitmap(bitmap);
                    });
                }
            }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}
        });
    }
    /** All daily actions are available using the D-pad, without separate screens or fake buttons. */
    private String keyFor(Media m){
        return (m.episodeId!=null&&!m.episodeId.isEmpty())?
                UserLibrary.episodeKey(m.id,m.episodeId):UserLibrary.key(m.type,m.id);
    }
    private UserLibrary.Entry identify(Media m){
        return library.identify(keyFor(m),m.id,m.type,m.name,m.poster,m.description,m.source,m.episodeId);
    }
    private Media fromSaved(UserLibrary.Entry e){
        Media m=new Media();
        m.id=e.id;m.type=e.type;m.name=e.title;m.poster=e.poster;
        m.description=e.description;m.source=e.source;m.episodeId=e.episodeId;
        return m;
    }
    /** Full-screen title details instead of system alert dialogs. */
    private void details(Media m){
        UserLibrary.Entry entry=identify(m);
        if(browseRoot==null)return;
        browseOffset=browseScroll==null?0:browseScroll.getScrollY();
        showingDetails=true;
        ScrollView page=new ScrollView(this);
        page.setBackgroundColor(BG);
        page.setFillViewport(true);
        LinearLayout outer=new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(40),dp(23),dp(40),dp(45));
        page.addView(outer);
        Button back=button("←  Voltar",()->returnToBrowse());
        outer.addView(back);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);
        rowParams.topMargin=dp(26);outer.addView(row,rowParams);

        ImageView artworkView=new ImageView(this);
        artworkView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artworkView.setBackground(shape(0xff213748,0));
        row.addView(artworkView,new LinearLayout.LayoutParams(dp(238),dp(351)));
        if(m.poster!=null&&m.poster.startsWith("https://"))loadPoster(m.poster,artworkView);

        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(36),0,dp(5),0);
        row.addView(right,new LinearLayout.LayoutParams(0,-2,1f));
        right.addView(text(("series".equals(m.type)?"SÉRIE":"movie".equals(m.type)?"FILME":"TV")+"  ·  SHOCK TV",12,AQUA,true));
        TextView title=text(m.name,36,Color.WHITE,true);title.setMaxLines(3);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=dp(9);right.addView(title,tp);
        if(m.genre!=null&&!m.genre.isEmpty()){
            TextView genres=text(m.genre.replace(",", "  •  "),13,0xff90bbca,false);
            genres.setMaxLines(2);right.addView(genres);
        }
        TextView desc=text(m.description==null||m.description.isEmpty()?
                "Sinopse indisponível para este título.":m.description,16,0xffd1dfe6,false);
        desc.setMaxLines(8);
        LinearLayout.LayoutParams dp2=new LinearLayout.LayoutParams(-1,-2);dp2.topMargin=dp(20);
        right.addView(desc,dp2);

        LinearLayout primary=new LinearLayout(this);
        primary.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actions=new LinearLayout.LayoutParams(-1,-2);actions.topMargin=dp(24);
        right.addView(primary,actions);
        primary.addView(button(entry.canContinue()&&entry.streamUrl!=null&&!entry.streamUrl.isEmpty()?
             "▶  Continuar":"▶  Ver opções de reprodução",()->{
                 UserLibrary.Entry current=library.get(entry.key);
                 if(current.canContinue() && !current.streamUrl.isEmpty())resumeEntry(current);
                 else resolveItem(m);
             }));
        if(entry.canContinue()&&!entry.streamUrl.isEmpty())
            primary.addView(button("⟲  Recomeçar",()->{
                UserLibrary.Entry current=library.get(entry.key);
                play(current.streamUrl,fromSaved(current),current.episodeId,true);
            }));

        LinearLayout secondary=new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actions2=new LinearLayout.LayoutParams(-1,-2);actions2.topMargin=dp(11);
        right.addView(secondary,actions2);
        Button later=button(entry.watchLater?"✓  Na minha lista":"＋  Assistir mais tarde",()->{
            UserLibrary.Entry current=library.get(entry.key);
            library.later(entry.key,!current.watchLater);
            laterButtonRefresh(entry.key);
            showPersonal();
        });
        secondary.addView(later);
        Button liked=button(entry.liked?"♥  Curtido":"♡  Curtir",()->{
            UserLibrary.Entry current=library.get(entry.key);
            library.liked(entry.key,!current.liked);
            laterButtonRefresh(entry.key);
            showPersonal();
        });
        secondary.addView(liked);
        // Save handles so the buttons reflect each change while staying on the detail page.
        detailLaterButton=later;detailLikeButton=liked;
        UserLibrary.Entry updated=library.get(entry.key);
        if(updated.lastPlayed>0){
            TextView checkpoint=text(updated.progressLabel(),13,0xffa4c3d0,false);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(16);
            right.addView(checkpoint,cp);
        }
        setContentView(page);
        back.requestFocus();
    }
    private Button detailLaterButton,detailLikeButton;
    private void laterButtonRefresh(String key){
        UserLibrary.Entry e=library.get(key);
        if(detailLaterButton!=null)detailLaterButton.setText(e.watchLater?"✓  Na minha lista":"＋  Assistir mais tarde");
        if(detailLikeButton!=null)detailLikeButton.setText(e.liked?"♥  Curtido":"♡  Curtir");
    }
    private void returnToBrowse(){
        if(!showingDetails)return;
        showingDetails=false;
        setContentView(browseRoot);
        if(browseScroll!=null)browseScroll.post(()->browseScroll.scrollTo(0,browseOffset));
        scheduleRefresh();
    }
    @Override public void onBackPressed(){
        if(showingDetails){returnToBrowse();return;}
        super.onBackPressed();
    }
    private void resumeEntry(UserLibrary.Entry e){
        if(e.streamUrl!=null && (e.streamUrl.startsWith("http://")||e.streamUrl.startsWith("https://"))){
            play(e.streamUrl,fromSaved(e),e.episodeId,false);
        }else{
            resolveItem(fromSaved(e));
        }
    }
    private void showPersonal(){
        if(personal==null||library==null)return;
        personal.removeAllViews();
        addPersonalRow("Continuar assistindo","continue","");
        addPersonalRow("Assistir mais tarde","later","");
        addPersonalRow("Seus favoritos","liked","");
        if("minha".equals(activeSection))addPersonalRow("Assistidos recentemente","recent","");
        if("minha".equals(activeSection)&&library.list("continue").isEmpty()
            &&library.list("later").isEmpty()&&library.list("liked").isEmpty()
            &&library.list("recent").isEmpty()){
            TextView intro=text("Sua biblioteca ainda está vazia. Explore os catálogos e marque títulos para guardar aqui.",16,0xffb0cad6,false);
            intro.setPadding(dp(3),dp(18),dp(10),dp(18));personal.addView(intro);
        }
    }
    private void addPersonalRow(String title,String filter,String empty){
        ArrayList<UserLibrary.Entry> entries=library.list(filter);
        if(entries.isEmpty())return;
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=text(title,16,AQUA,true);
        line.addView(heading,new LinearLayout.LayoutParams(0,dp(44),1f));
        if(!entries.isEmpty())line.addView(button("Ver todos",()->showAll(filter)));
        personal.addView(line);
        if(entries.isEmpty()){
            TextView hint=text(empty,13,0xff91aaba,false);
            hint.setPadding(dp(8),0,0,dp(11));personal.addView(hint);
            return;
        }
        HorizontalScrollView scroller=new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        LinearLayout cards=new LinearLayout(this);
        cards.setClipChildren(false);
        for(int i=0;i<entries.size()&&i<16;i++){
            UserLibrary.Entry e=entries.get(i);
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(shape(PANEL,0xff2a4a60));
            card.setPadding(dp(14),dp(14),dp(14),dp(12));
            card.setFocusable(true);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(234),dp(110));cp.rightMargin=dp(12);
            cards.addView(card,cp);
            TextView titleView=text(e.title,15,Color.WHITE,true);
            titleView.setMaxLines(2);card.addView(titleView);
            TextView sub=text("continue".equals(filter)?e.progressLabel():(e.completed?"Concluído":e.type),12,0xffb3cbda,false);
            sub.setMaxLines(2);LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(-1,-2);q.topMargin=dp(8);card.addView(sub,q);
            if("continue".equals(filter)&&e.durationMs>0){
                android.widget.ProgressBar bar=new android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
                bar.setMax(1000);bar.setProgress((int)Math.min(1000,(1000L*e.positionMs)/e.durationMs));
                LinearLayout.LayoutParams progress=new LinearLayout.LayoutParams(-1,dp(4));progress.topMargin=dp(7);card.addView(bar,progress);
            }
            card.setOnFocusChangeListener((v,focus)->{
                v.setBackground(shape(focus?PANEL2:PANEL,focus?AQUA:0xff2a4a60));
                v.animate().scaleX(focus?1.04f:1f).scaleY(focus?1.04f:1f).setDuration(120).start();
            });
            card.setOnClickListener(v->{
                if("continue".equals(filter))resumeEntry(library.get(e.key));
                else details(fromSaved(e));
            });
        }
        scroller.addView(cards);personal.addView(scroller);
    }
    private void showAll(String filter){
        ArrayList<UserLibrary.Entry> entries=library.list(filter);
        if(entries.isEmpty()){message("Nenhum título nesta seção.");return;}
        String[] titles=new String[Math.min(180,entries.size())];
        for(int i=0;i<titles.length;i++){
            UserLibrary.Entry e=entries.get(i);
            titles[i]=e.title+"   •   "+e.progressLabel();
        }
        AlertDialog.Builder dlg=new AlertDialog.Builder(this).setTitle(labelFor(filter))
           .setItems(titles,(d,i)->details(fromSaved(entries.get(i))))
           .setNegativeButton("Voltar",null);
        if("recent".equals(filter))dlg.setNeutralButton("Limpar histórico",(d,w)->
             new AlertDialog.Builder(this).setTitle("Limpar todo o histórico?")
             .setMessage("Os curtidos e a lista Assistir mais tarde serão preservados.")
             .setNegativeButton("Cancelar",null)
             .setPositiveButton("Limpar",(dd,ww)->{library.clearHistory();showPersonal();}).show());
        dlg.show();
    }
    private String labelFor(String filter){
        if("continue".equals(filter))return "Continuar assistindo";
        if("liked".equals(filter))return "Lista de curtidos";
        if("later".equals(filter))return "Assistir mais tarde";
        return "Assistidos recentemente";
    }
    /** Auto-play the viewer's liked videos that already have a resolved, accessible media URL. */
    private void playLikedPlaylist(){
        ArrayList<UserLibrary.Entry> favorites=library.list("liked");
        ArrayList<String> urls=new ArrayList<>(), keys=new ArrayList<>();
        for(UserLibrary.Entry e:favorites){
            if(e.streamUrl==null)continue;
            if(!e.streamUrl.startsWith("https://")&&!e.streamUrl.startsWith("http://"))continue;
            urls.add(e.streamUrl);keys.add(e.key);
        }
        if(urls.isEmpty()){
            message("A lista de curtidos ainda não contém vídeos com streams reproduzidos. "+
                "Escolha um título, selecione uma fonte disponível e inicie a reprodução uma vez para adicioná-lo à fila.");
            return;
        }
        UserLibrary.Entry first=library.get(keys.get(0));
        Intent intent=new Intent(this,PlayerActivity.class);
        intent.putExtra("url",urls.get(0));intent.putExtra("title",first.title);
        intent.putExtra("id",first.id);intent.putExtra("type",first.type);
        intent.putExtra("poster",first.poster);intent.putExtra("description",first.description);
        intent.putExtra("source",first.source);intent.putExtra("episodeId",first.episodeId);
        intent.putExtra("libraryKey",first.key);
        intent.putExtra("playlistKeys",keys.toArray(new String[0]));
        intent.putExtra("playlistUrls",urls.toArray(new String[0]));
        startActivity(intent);
    }
    private void showFullLibrary(){
        new AlertDialog.Builder(this).setTitle("Minha biblioteca")
           .setItems(new String[]{"Continuar assistindo","Assistir mais tarde","Curtidos","Histórico de reprodução"},
               (dialog,which)->showAll(new String[]{"continue","later","liked","recent"}[which]))
           .setNegativeButton("Fechar",null).show();
    }
    private void searchCatalog(){
        EditText field=new EditText(this);field.setSingleLine(true);
        field.setHint("Título, filme ou série");
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(24),0,dp(24),0);box.addView(field);
        new AlertDialog.Builder(this).setTitle("Buscar nos catálogos carregados")
          .setView(box).setNegativeButton("Cancelar",null)
          .setPositiveButton("Buscar",(d,w)->{
              String query=field.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
              if(query.isEmpty())return;
              ArrayList<Media> matched=new ArrayList<>();
              for(Media m:knownItems)if(m.name.toLowerCase(java.util.Locale.ROOT).contains(query))matched.add(m);
              if(matched.isEmpty()){message("Nenhum resultado nos catálogos já carregados.");return;}
              String[] names=new String[Math.min(100,matched.size())];
              for(int i=0;i<names.length;i++)names[i]=matched.get(i).name;
              new AlertDialog.Builder(this).setTitle("Busca • "+matched.size()+" resultado(s)")
                .setItems(names,(dd,i)->details(matched.get(i)))
                .setNegativeButton("Fechar",null).show();
          }).show();
    }

    private void resolveItem(Media m) {
        if ("series".equals(m.type) && (m.episodeId==null||m.episodeId.isEmpty())) {
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
                    ArrayList<Media> episodes=new ArrayList<>();
                    for(int i=0;i<videos.length()&&i<250;i++) {
                        JSONObject episode=videos.optJSONObject(i);if(episode==null)continue;
                        String id=episode.optString("id","");
                        if(id.isEmpty())continue;
                        Media item=new Media();
                        item.id=m.id;item.type=m.type;item.episodeId=id;
                        item.name=m.name+" • T"+episode.optInt("season",0)+" E"+episode.optInt("episode",0)
                          +" "+episode.optString("title","");
                        item.source=m.source;item.poster=m.poster;item.description=m.description;
                        episodes.add(item);
                        labels.add(item.name);
                    }
                    runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Episódios de "+m.name)
                       .setItems(labels.toArray(new String[0]),(dialog,index)->details(episodes.get(index)))
                       .setNegativeButton("Voltar",null).show());
                }catch(Exception ex){runOnUiThread(()->message("Não foi possível consultar os episódios: "+ex.getMessage()));}
            });
        }else{
            resolveStreams(m,(m.episodeId!=null&&!m.episodeId.isEmpty())?m.episodeId:m.id);
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
                   .setItems(titles.toArray(new String[0]),(dialog,index)->play(playable.get(index),media,videoId,false))
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
                Media direct=new Media();
                direct.id=url;direct.type="direct";direct.name="Reprodução direta";
                direct.episodeId="";direct.poster="";direct.description="Mídia adicionada diretamente";
                direct.source="";play(url,direct,"",false);
            }).show();
    }
    private void play(String url,Media m,String videoId,boolean restart){
        if(url==null||!(url.startsWith("http://")||url.startsWith("https://"))){
            message("Esta fonte não tem um endereço de mídia reproduzível.");return;
        }
        if(m.type==null||m.type.isEmpty())m.type="movie";
        String key="direct".equals(m.type)?UserLibrary.directKey(url):
             ((m.episodeId!=null&&!m.episodeId.isEmpty())?
                 UserLibrary.episodeKey(m.id,m.episodeId):UserLibrary.key(m.type,m.id));
        library.identify(key,m.id,m.type,m.name,m.poster,m.description,m.source,m.episodeId);
        Intent intent=new Intent(this,PlayerActivity.class);
        intent.putExtra("url",url);intent.putExtra("title",m.name);
        intent.putExtra("id",m.id);intent.putExtra("type",m.type);
        intent.putExtra("poster",m.poster);intent.putExtra("description",m.description);
        intent.putExtra("source",m.source);intent.putExtra("episodeId",m.episodeId);
        intent.putExtra("libraryKey",key);intent.putExtra("restart",restart);
        startActivity(intent);
    }
    private void message(String text){new AlertDialog.Builder(this).setTitle("Shock TV").setMessage(text).setPositiveButton("OK",null).show();}
    private static final class Media{String id="",type="",name="",poster="",description="",source="",episodeId="",origin="",catalogId="",genre="";}
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
    @Override protected void onResume(){super.onResume();if(personal!=null)showPersonal();}
    @Override protected void onDestroy(){pool.shutdownNow();super.onDestroy();}
}
