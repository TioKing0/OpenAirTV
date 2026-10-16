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
import android.widget.GridLayout;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
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
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Collections;
import java.util.Comparator;
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
    private static final String IPTV="https://catalogo-iptv-production.up.railway.app/manifest.json";
    private static final String PLAVIO="https://plaviohd.edon.workers.dev/manifest.json";
    private static final String TVVIO="https://c69e439124f3-tvvio.baby-beamup.club/manifest.json";
    private static final String DC_UNIVERSE="https://h-gate.site/dc/manifest.json";
    private static final String[] BUNDLED_ADDONS={
        CINEMETA,FENIXFLIX,TORRENTIO,NETFLIX_CATALOG,PIRATEBAY_PLUS,
        IPTV,PLAVIO,TVVIO,DC_UNIVERSE
    };
    private static final String[] BUNDLED_NAMES={
        "Cinemeta • Metadados","FenixFlix • Addon comunitário",
        "Torrentio • Busca de streams","Netflix Catalog • Metadados",
        "ThePirateBay+ • Busca de streams",
        "Catálogo IPTV • canais ao vivo","PlavioHD • Addon comunitário",
        "Tvvio • canais ao vivo","DC Universe • Catálogo temático"
    };
    private final ExecutorService pool=Executors.newFixedThreadPool(5);
    private final ExecutorService searchExecutor=Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> searchFuture;
    private String chosenCategory="";
    private int categoryGeneration=0;
    private String liveGroupFilter="Todos os canais";
    private Media liveSelectedChannel=null;
    private final ArrayList<Media> categoryResults=new ArrayList<>();
    private TextView categoryStatus;
    private final ArrayList<String> sources=new ArrayList<>();
    private final Set<String> disabledBundled=new HashSet<>();
    private final LinkedHashMap<String,JSONObject> manifestCache =
        new LinkedHashMap<String,JSONObject>(25,.75f,true){
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String,JSONObject> eldest){
                return size()>40;
            }
        };
    private final LinkedHashMap<String,JSONArray> episodeCache =
        new LinkedHashMap<String,JSONArray>(10,.75f,true){
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String,JSONArray> eldest){
                return size()>25;
            }
        };
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
    private EditText searchField;
    private LinearLayout searchPanel;
    private LinearLayout searchResultsArea;
    private TextView searchFeedback;
    private String searchText="";
    private String searchFilter="all";
    private int searchGeneration=0;
    private final LinkedHashMap<String,Media> searchMatches=new LinkedHashMap<>();
    private Runnable pendingSearch;
    private Runnable pendingSearchRender;
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
        synchronized(manifestCache){manifestCache.clear();}
        synchronized(episodeCache){episodeCache.clear();}
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
        shell.setOrientation(BuildConfig.MOBILE_UI?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        shell.setBackgroundColor(BG);
        shell.setFocusableInTouchMode(true);

        if(BuildConfig.MOBILE_UI){
            LinearLayout top=new LinearLayout(this);
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.setPadding(dp(17),dp(9),dp(17),dp(7));
            top.setBackgroundColor(0xff0b1926);
            TextView brand=text("TELARES",22,AQUA,true);
            brand.setLetterSpacing(.07f);
            top.addView(brand,new LinearLayout.LayoutParams(0,dp(40),1f));
            TextView hint=text("PLAY",11,0xffb9d6e4,true);
            top.addView(hint);
            shell.addView(top,new LinearLayout.LayoutParams(-1,dp(57)));
            if(mobileWidthDp()>=700)addMobileTabletTabs(shell);
        }else{
        LinearLayout sidebar=new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(13),dp(26),dp(8),dp(15));
        sidebar.setBackgroundColor(0xff0b1926);
        shell.addView(sidebar,new LinearLayout.LayoutParams(dp(176),-1));
        TextView logo=text("TELARES",25,Color.WHITE,true);
        logo.setLetterSpacing(.08f);
        sidebar.addView(logo);
        TextView logo2=text("TV",19,AQUA,true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(38);
        sidebar.addView(logo2,lp);
        navItem(sidebar,"⌂","Início","inicio");
        navItem(sidebar,"▣","Filmes","filmes");
        navItem(sidebar,"▤","Séries","series");
        navItem(sidebar,"◉","Canais ao vivo","aovivo");
        navItem(sidebar,"♡","Minha lista","minha");
        navItem(sidebar,"⌕","Buscar","buscar");
        navItem(sidebar,"▦","Categorias","categorias");
        navItem(sidebar,"⊞","Fontes","fontes");
        View fill=new View(this);sidebar.addView(fill,new LinearLayout.LayoutParams(1,0,1f));
        TextView foot=text("TELARES TV\nv"+BuildConfig.VERSION_NAME,11,0xff648397,false);
        sidebar.addView(foot);


        }
        browseScroll=new ScrollView(this);
        browseScroll.setClipToPadding(false);
        browseScroll.setFillViewport(true);
        browseScroll.setVerticalScrollBarEnabled(false);
        shell.addView(browseScroll,BuildConfig.MOBILE_UI?
            new LinearLayout.LayoutParams(-1,0,1f):new LinearLayout.LayoutParams(0,-1,1f));
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(BuildConfig.MOBILE_UI?14:29),dp(BuildConfig.MOBILE_UI?14:24),
            dp(BuildConfig.MOBILE_UI?14:27),dp(BuildConfig.MOBILE_UI?22:65));
        browseScroll.addView(content);

        LinearLayout toolbar=new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.addView(text(sectionLabel(activeSection),BuildConfig.MOBILE_UI?20:22,Color.WHITE,true),
                new LinearLayout.LayoutParams(0,dp(44),1f));
        if(!BuildConfig.MOBILE_UI||mobileWidthDp()>=700)
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

        if("buscar".equals(activeSection)){
            buildSearchPage();
        }else if("categorias".equals(activeSection)){
            buildCategoryPage();
        }else{
            if("inicio".equals(activeSection)||"minha".equals(activeSection))showPersonal();
            updateSpotlight();
            refreshCatalogRows();
        }
        if(BuildConfig.MOBILE_UI&&mobileWidthDp()<700)addMobileBottomNavigation(shell);
        browseRoot=shell;
        setContentView(shell);
        browseScroll.post(()->browseScroll.scrollTo(0,0));
    }

    private int mobileWidthDp(){return getResources().getConfiguration().screenWidthDp;}
    private void mobileNavigate(String route){
        if("mais".equals(route)){
            final String[] labels={"Buscar","Categorias","Minha lista","Fontes"};
            new AlertDialog.Builder(this).setTitle("Telares Play")
                .setItems(labels,(d,index)->{
                    String choice=labels[index];
                    if("Fontes".equals(choice)){showSources();return;}
                    mobileNavigate("Buscar".equals(choice)?"buscar":
                         "Categorias".equals(choice)?"categorias":"minha");
                }).show();
            return;
        }
        if("fontes".equals(route)){showSources();return;}
        if(!route.equals(activeSection)){activeSection=route;render();}
        else if("buscar".equals(route)&&searchField!=null)searchField.requestFocus();
    }
    private void addMobileBottomNavigation(LinearLayout shell){
        LinearLayout bar=new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xff0c1c2a);
        String[] routes={"inicio","filmes","series","aovivo","mais"};
        String[] labels={"Início","Filmes","Séries","Ao vivo","Mais"};
        for(int i=0;i<routes.length;i++){
            final String destination=routes[i];
            TextView item=text(labels[i],11,
                activeSection.equals(destination)?AQUA:0xffa9c0cd,activeSection.equals(destination));
            item.setGravity(Gravity.CENTER);
            item.setMinimumHeight(dp(55));
            item.setClickable(true);
            item.setFocusable(true);
            item.setOnClickListener(v->mobileNavigate(destination));
            item.setContentDescription(labels[i]);
            bar.addView(item,new LinearLayout.LayoutParams(0,dp(62),1f));
        }
        shell.addView(bar,new LinearLayout.LayoutParams(-1,dp(64)));
    }
    private void addMobileTabletTabs(LinearLayout shell){
        HorizontalScrollView scroller=new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs=new LinearLayout(this);
        tabs.setPadding(dp(9),dp(3),dp(9),dp(5));
        tabs.setBackgroundColor(0xff0b1926);
        String[] routes={"inicio","filmes","series","aovivo","minha","buscar","categorias","fontes"};
        String[] titles={"Início","Filmes","Séries","Ao vivo","Minha lista","Buscar","Categorias","Fontes"};
        for(int i=0;i<routes.length;i++){
            final String route=routes[i];
            Button b=button(titles[i],()->mobileNavigate(route));
            b.setTextSize(12);
            b.setMinHeight(dp(48));
            b.setMinimumHeight(dp(48));
            tabs.addView(b);
        }
        scroller.addView(tabs);
        shell.addView(scroller,new LinearLayout.LayoutParams(-1,dp(63)));
    }
    private String sectionLabel(String key){
        if("filmes".equals(key))return "Filmes";
        if("series".equals(key))return "Séries";
        if("aovivo".equals(key))return "Canais ao vivo";
        if("buscar".equals(key))return "Buscar";
        if("categorias".equals(key))return "Categorias";
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
            if("buscar".equals(route)){
                if(!"buscar".equals(activeSection)){activeSection="buscar";render();}
                else if(searchField!=null)searchField.requestFocus();
                return;
            }
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
        Media chosen=null;
        for(Media candidate:knownItems){if(!isChannel(candidate)){chosen=candidate;break;}}
        if(chosen==null)return;
        final Media featured=chosen;
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
          .setTitle("Fontes • "+sources.size()+" ativa(s) • Selecione para alternar")
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
          .setNeutralButton("Sobre", (d,w)->message("Os addons predefinidos podem ser ativados ou desativados. "+
              "Algumas fontes oferecem só metadados, outras oferecem torrents. O Media3 não reproduz torrents diretamente."))
          .setNegativeButton("Fechar",null).show();
    }
    private JSONObject getManifest(String url) throws Exception{
        synchronized(manifestCache){
            JSONObject found=manifestCache.get(url);
            if(found!=null)return found;
        }
        JSONObject loaded=getJson(url);
        synchronized(manifestCache){manifestCache.put(url,loaded);}
        return loaded;
    }
    /** Source/catalog identifies linear broadcast, even when Stremio type is "series". */
    private boolean isBroadcastCatalog(JSONObject manifest,JSONObject catalog,String source){
        String type=catalog.optString("type","");
        if("tv".equals(type)||"channel".equals(type))return true;
        String origin=normalizeCategory(manifest.optString("id","")+" "+
            manifest.optString("name","")+" "+source);
        if(origin.contains("catalogo iptv")||origin.contains("tvvio")||
            origin.contains("iptv"))return true;
        String label=normalizeCategory(catalog.optString("name","")+" "+catalog.optString("id",""));
        if(label.contains("canais ao vivo")||label.contains("live channels")||
            label.contains("tv channels"))return true;
        String desc=normalizeCategory(manifest.optString("description",""));
        return (desc.contains("canais ao vivo")||desc.contains("live tv"))&&
            (label.contains("canais")||label.contains("channel")||
             label.contains("ao vivo")||label.contains("live"));
    }
    private void loadSource(String manifestUrl){
        pool.execute(()->{
            try{
                JSONObject manifest=getManifest(manifestUrl);
                JSONArray catalogs=manifest.optJSONArray("catalogs");
                if(catalogs==null)return;
                String base=manifestUrl.substring(0,manifestUrl.length()-"manifest.json".length());
                int loaded=0;
                for(int i=0;i<catalogs.length()&&loaded<16;i++){
                    JSONObject catalog=catalogs.optJSONObject(i);
                    if(catalog==null)continue;
                    String type=catalog.optString("type"),catalogId=catalog.optString("id");
                    boolean liveCatalog=isBroadcastCatalog(manifest,catalog,manifestUrl);
                    boolean custom=!("movie".equals(type)||"series".equals(type)||"tv".equals(type)||"channel".equals(type));
                    String catalogueName=catalog.optString("name","").toLowerCase(Locale.ROOT);
                    String lowerId=catalogId.toLowerCase(Locale.ROOT);
                    if(custom&&!liveCatalog&&!(catalogueName.contains("movie")||catalogueName.contains("series")||
                                  lowerId.contains("movie")||lowerId.contains("series")))continue;
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
                            m.type=type; // Preserve the protocol type, including custom DC Universe type.
                            m.displayType=liveCatalog?"tv":custom?
                                (lowerId.contains("series")||catalogueName.contains("series")?"series":"movie"):type;
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
                                    if("tv".equals(candidate.displayType))prior.displayType="tv";
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
    private boolean isSeries(Media m){return !isChannel(m)&&"series".equals(m.displayType.isEmpty()?m.type:m.displayType);}
    private boolean isFilm(Media m){return !isChannel(m)&&"movie".equals(m.displayType.isEmpty()?m.type:m.displayType);}
    private boolean isChannel(Media m){
        if("tv".equals(m.displayType)||"tv".equals(m.type)||"channel".equals(m.type))return true;
        // Historical metadata can say series even though it came from a TV catalogue.
        // Route legacy entries to the channel guide without deleting the user's library.
        String source=normalizeCategory(m.source+" "+m.origin);
        return source.contains("catalogo iptv production")||
               source.contains("tvvio.baby beamup");
    }
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
        if("buscar".equals(activeSection)||"categorias".equals(activeSection))return; // Keep dedicated discovery pages.
        catalogArea.removeAllViews();
        updateSpotlight();
        if("minha".equals(activeSection))return;
        Set<String> shown=new HashSet<>();
        if("aovivo".equals(activeSection)){
            buildLiveChannelGuide();
        }else if("filmes".equals(activeSection)||"series".equals(activeSection)){
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
            addRowFrom("Novidades",matching(m->!isChannel(m)&&m.catalogId.toLowerCase(java.util.Locale.ROOT).contains("new")),shown);
            addRowFrom("Filmes para descobrir",matching(this::isFilm),shown);
            addRowFrom("Séries para maratonar",matching(this::isSeries),shown);
            // Live signals are listed in their independent channel guide, not VOD rows.
        }
        if(knownItems.isEmpty()){
            TextView empty=text("Nenhum catálogo carregado. Verifique sua internet ou configure uma fonte em Fontes.",15,0xffb1c8d5,false);
            empty.setPadding(dp(5),dp(25),dp(5),dp(25));catalogArea.addView(empty);
        }
    }
    /** TV guide: remote-friendly groups, channel list and program preview. */
    private String liveGroupOf(Media m){
        String title=normalizeCategory(m.name),origin=normalizeCategory(m.catalogId+" "+m.genre);
        if(title.contains("telecine"))return "Telecine";
        if(title.contains("hbo")||title.contains("cinemax"))return "HBO / Max";
        if(title.contains("sportv")||title.contains("espn")||title.contains("premiere")||
           title.contains("tnt sports")||origin.contains("esporte"))return "Esportes";
        if(title.contains("globo")||title.contains("record")||title.contains("sbt")||
           title.contains("rede tv")||title.contains("tv cultura")||origin.contains("aberto"))
            return "Canais abertos";
        if(title.contains("news")||title.contains("cnn")||origin.contains("noticia"))return "Notícias";
        if(title.contains("cartoon")||origin.contains("infantil"))return "Infantil";
        if(origin.contains("religioso"))return "Religiosos";
        if(origin.contains("entretenimento"))return "Entretenimento";
        if(origin.contains("filmes")||origin.contains("series"))return "Filmes e séries";
        return "Outros canais";
    }
    private void buildLiveChannelGuide(){
        ArrayList<Media> channels=matching(this::isChannel);
        if(channels.isEmpty()){
            catalogArea.addView(text("Aguardando catálogos de TV. Consulte os addons em Fontes.",17,0xffb1c8d5,false));
            return;
        }
        java.util.Collections.sort(channels,(a,b)->a.name.compareToIgnoreCase(b.name));
        ArrayList<String> groups=new ArrayList<>();
        groups.add("Todos os canais");
        for(Media m:channels){String group=liveGroupOf(m);if(!groups.contains(group))groups.add(group);}
        if(!groups.contains(liveGroupFilter))liveGroupFilter="Todos os canais";
        final ArrayList<Media> subset=new ArrayList<>();
        for(Media m:channels)
            if("Todos os canais".equals(liveGroupFilter)||liveGroupOf(m).equals(liveGroupFilter))
                subset.add(m);
        if(liveSelectedChannel==null||!subset.contains(liveSelectedChannel))
            liveSelectedChannel=subset.isEmpty()?null:subset.get(0);

        LinearLayout guide=new LinearLayout(this);
        guide.setOrientation(LinearLayout.HORIZONTAL);
        guide.setPadding(dp(5),dp(15),dp(5),dp(8));
        catalogArea.addView(guide,new LinearLayout.LayoutParams(-1,dp(540)));

        LinearLayout groupList=new LinearLayout(this);
        groupList.setOrientation(LinearLayout.VERTICAL);
        groupList.setPadding(dp(5),dp(5),dp(8),dp(12));
        groupList.addView(text("CATEGORIAS",16,AQUA,true));
        for(String group:groups){
            Button tab=button((group.equals(liveGroupFilter)?"● ":"○ ")+group,()->{
                liveGroupFilter=group;
                liveSelectedChannel=null;
                refreshCatalogRows();
            });
            groupList.addView(tab,new LinearLayout.LayoutParams(-1,dp(53)));
        }
        ScrollView groupsScroll=new ScrollView(this);
        groupsScroll.setFillViewport(true);
        groupsScroll.addView(groupList);
        guide.addView(groupsScroll,new LinearLayout.LayoutParams(dp(213),-1));

        LinearLayout channelList=new LinearLayout(this);
        channelList.setOrientation(LinearLayout.VERTICAL);
        channelList.setPadding(dp(8),dp(5),dp(10),dp(12));
        channelList.addView(text("CANAIS • "+subset.size(),16,AQUA,true));
        for(Media m:subset){
            Button channel=button((m==liveSelectedChannel?"▶ ":"  ")+m.name,()->{
                liveSelectedChannel=m;
                refreshCatalogRows();
            });
            channel.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            channelList.addView(channel,new LinearLayout.LayoutParams(-1,dp(58)));
        }
        ScrollView channelsScroll=new ScrollView(this);
        channelsScroll.setFillViewport(true);
        channelsScroll.addView(channelList);
        guide.addView(channelsScroll,new LinearLayout.LayoutParams(dp(282),-1));

        LinearLayout preview=new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(22),dp(6),dp(12),dp(15));
        preview.setBackground(shape(0xff14283a,0xff214b61));
        guide.addView(preview,new LinearLayout.LayoutParams(0,-1,1f));
        Media selected=liveSelectedChannel;
        if(selected!=null){
            preview.addView(text("AO VIVO  •  "+liveGroupOf(selected),14,AQUA,true));
            TextView title=text(selected.name,25,Color.WHITE,true);
            title.setMaxLines(2);preview.addView(title);
            ImageView poster=new ImageView(this);
            poster.setScaleType(ImageView.ScaleType.FIT_CENTER);
            poster.setBackgroundColor(0xff091420);
            LinearLayout.LayoutParams img=new LinearLayout.LayoutParams(-1,0,1f);
            img.topMargin=dp(12);img.bottomMargin=dp(15);
            preview.addView(poster,img);
            if(selected.poster.startsWith("https://"))loadPoster(selected.poster,poster);
            preview.addView(text("Escolha Reproduzir para abrir o sinal em tela cheia. A disponibilidade depende do addon.",13,0xffa9c9d7,false));
            Button play=button("▶  Reproduzir canal",()->details(selected));
            LinearLayout.LayoutParams start=new LinearLayout.LayoutParams(-1,dp(50));
            start.topMargin=dp(14);preview.addView(play,start);
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
        right.addView(text((isSeries(m)?"SÉRIE":isFilm(m)?"FILME":"AO VIVO")+"  ·  TELARES TV",12,AQUA,true));
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
        if("buscar".equals(activeSection)||"categorias".equals(activeSection)){
            activeSection="inicio";searchGeneration++;categoryGeneration++;render();return;
        }
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
    /** Platform categories follow addon catalogue provenance; never claim subscription rights. */
    private void buildCategoryPage(){
        TextView intro=text("Explore catálogos identificados pelos addons. Plataformas não confirmam disponibilidade oficial nem reprodução.",14,0xffa9c6d5,false);
        catalogArea.addView(intro);
        String[] tags={"Canais ao vivo","Anime","Netflix","Max / HBO","Disney+","Prime Video","Apple TV+","Paramount+","Crunchyroll","Documentários","Terror","Comédia","Ficção científica"};
        LinearLayout options=new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin=dp(18);catalogArea.addView(options,lp);
        for(String tag:tags){
            Button b=button((tag.equals(chosenCategory)?"●  ":"○  ")+tag,()->{
                if("Canais ao vivo".equals(tag)){activeSection="aovivo";render();return;}
                chosenCategory=tag;
                requestCategory(tag);
                // Keep focus stable after refreshing, including with the D-pad.
            });
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(51));
            p.bottomMargin=dp(8);options.addView(b,p);
        }
        categoryStatus=text(chosenCategory.isEmpty()?"Escolha uma categoria.":categoryResults.size()+" título(s) em "+chosenCategory,15,0xffb6d6e1,false);
        LinearLayout.LayoutParams cap=new LinearLayout.LayoutParams(-1,-2);cap.topMargin=dp(18);
        catalogArea.addView(categoryStatus,cap);
        if(!categoryResults.isEmpty()){
            addCatalog(chosenCategory,new ArrayList<>(categoryResults));
        }
    }
    /** Explicit addon category declarations; a platform label does not prove availability in Brazil. */
    private String normalizeCategory(String value){
        String form=java.text.Normalizer.normalize(value==null?"":value,java.text.Normalizer.Form.NFD);
        return form.replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replace('-',' ').replace('_',' ');
    }
    private String[] categoryAliases(String category){
        if("Anime".equals(category))return new String[]{"anime","japanese animation","animacao japonesa"};
        if("Documentários".equals(category))return new String[]{"documentario","documentary","documentaries","documental"};
        if("Terror".equals(category))return new String[]{"terror","horror"};
        if("Comédia".equals(category))return new String[]{"comedia","comedy"};
        if("Ficção científica".equals(category))
            return new String[]{"ficcao cientifica","science fiction","sci fi","scifi"};
        if("Netflix".equals(category))return new String[]{"netflix"};
        if("Max / HBO".equals(category))
            return new String[]{"hbo","hbo max","hbomax","max originals","max catalog","max tv"};
        if("Disney+".equals(category))return new String[]{"disney+","disney plus","disneyplus"};
        if("Prime Video".equals(category))return new String[]{"prime video","primevideo","amazon prime"};
        if("Apple TV+".equals(category))return new String[]{"apple tv+","apple tv plus","appletv+","apple originals"};
        if("Paramount+".equals(category))return new String[]{"paramount+","paramount plus","paramountplus"};
        if("Crunchyroll".equals(category))return new String[]{"crunchyroll"};
        return new String[0];
    }
    private boolean genreCategory(String name){
        return "Anime".equals(name)||"Documentários".equals(name)||"Terror".equals(name)
            ||"Comédia".equals(name)||"Ficção científica".equals(name);
    }
    private boolean categoryMatch(String category,String provider,String catalogName,String catalogId,String genres){
        String origin=normalizeCategory(provider+" "+catalogName+" "+catalogId);
        String genre=normalizeCategory(genres);
        for(String label:categoryAliases(category)){
            String term=normalizeCategory(label);
            if(origin.contains(term)||(genreCategory(category)&&genre.contains(term)))return true;
        }
        return false;
    }
    private String genreArgument(JSONArray extras,String category){
        if(extras==null||!genreCategory(category))return "";
        for(int i=0;i<extras.length();i++){
            JSONObject entry=extras.optJSONObject(i);
            if(entry==null||!"genre".equals(entry.optString("name")))continue;
            JSONArray options=entry.optJSONArray("options");
            if(options==null)continue;
            for(int j=0;j<options.length();j++){
                String option=options.optString(j,""),normal=normalizeCategory(option);
                for(String keyword:categoryAliases(category)){
                    if(normal.contains(normalizeCategory(keyword))){
                        try{return "/genre="+URLEncoder.encode(option,"UTF-8").replace("+","%20");}
                        catch(Exception ignored){return "";}
                    }
                }
            }
        }
        return "";
    }
    private void requestCategory(String category){
        categoryGeneration++;
        final int request=categoryGeneration;
        categoryResults.clear();
        render();
        categoryStatus.setText("Consultando "+category+" nos addons...");
        ArrayList<String> providers=new ArrayList<>(sources);
        pool.execute(()->{
            LinkedHashMap<String,Media> found=new LinkedHashMap<>();
            int declared=0;
            for(String source:providers){
                if(request!=categoryGeneration||Thread.currentThread().isInterrupted())return;
                try{
                    JSONObject manifest=getManifest(source);
                    JSONArray cats=manifest.optJSONArray("catalogs");
                    if(cats==null)continue;
                    String base=source.substring(0,source.length()-"manifest.json".length());
                    String provider=manifest.optString("name","");
                    int checked=0;
                    for(int i=0;i<cats.length()&&checked<24;i++){
                        if(request!=categoryGeneration)return;
                        JSONObject c=cats.optJSONObject(i);if(c==null)continue;
                        String type=c.optString("type",""),id=c.optString("id","");
                        if(!("movie".equals(type)||"series".equals(type))||!id.matches("[a-zA-Z0-9_.:-]{1,100}"))continue;
                        if(isBroadcastCatalog(manifest,c,source))continue;
                        String name=c.optString("name","");
                        boolean providerCatalog=categoryMatch(category,provider,name,id,"");
                        String argument=genreArgument(c.optJSONArray("extra"),category);
                        if(!providerCatalog&&argument.isEmpty()&&!genreCategory(category))continue;
                        String path=base+"catalog/"+type+"/"+id+argument+".json";
                        try{
                            JSONArray metas=getJson(path).optJSONArray("metas");
                            checked++;
                            if(metas==null)continue;
                            declared++;
                            for(int j=0;j<metas.length()&&j<40;j++){
                                JSONObject json=metas.optJSONObject(j);if(json==null)continue;
                                String mid=json.optString("id",""),title=json.optString("name","");
                                if(mid.isEmpty()||title.isEmpty())continue;
                                JSONArray genres=json.optJSONArray("genres");
                                StringBuilder genre=new StringBuilder();
                                if(genres!=null)for(int k=0;k<genres.length()&&k<15;k++){
                                    if(k>0)genre.append(",");
                                    genre.append(genres.optString(k,""));
                                }
                                if(!providerCatalog&&argument.isEmpty()&&
                                   !categoryMatch(category,provider,name,id,genre.toString()))continue;
                                Media media=new Media();media.id=mid;media.name=title;
                                media.type=json.optString("type",type);
                                media.poster=json.optString("poster","");
                                media.description=json.optString("description","");
                                media.origin=provider;media.catalogId=id;
                                media.source=base;media.genre=genre.toString();
                                found.putIfAbsent(identity(media),media);
                            }
                        }catch(Exception ignored){}
                    }
                }catch(Exception ignored){}
            }
            final int candidateCount=declared;
            runOnUiThread(()->{
                if(!"categorias".equals(activeSection)||request!=categoryGeneration)return;
                categoryResults.clear();categoryResults.addAll(found.values());
                render();
                categoryStatus.setText(categoryResults.isEmpty()?
                    "Nenhum catálogo conectado declarou títulos para "+category+". Adicione um addon compatível em Fontes.":
                    categoryResults.size()+" títulos • "+category+" • origem declarada pelos addons");
            });
        });
    }

    /** Search screen rendered for a TV remote, not a modal limited to first-page items. */
    private void searchCatalog(){
        activeSection="buscar";
        render();
    }
    private void buildSearchPage(){
        searchPanel=new LinearLayout(this);
        searchPanel.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams section=new LinearLayout.LayoutParams(-1,-2);
        section.topMargin=dp(13);
        catalogArea.addView(searchPanel,section);
        TextView hint=text("Encontre filmes e séries em todos os catálogos pesquisáveis dos seus addons.",15,0xffb4d0dd,false);
        searchPanel.addView(hint);
        LinearLayout searchBar=new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bar=new LinearLayout.LayoutParams(-1,-2);bar.topMargin=dp(15);
        searchPanel.addView(searchBar,bar);
        searchField=new EditText(this);
        searchField.setSingleLine(true);
        searchField.setTextColor(Color.WHITE);
        searchField.setHintTextColor(0xff86a6b5);
        searchField.setTextSize(18);
        searchField.setHint("Digite o nome de um filme ou série");
        searchField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchField.setBackground(shape(0xff1c3749,0xff457589));
        searchField.setPadding(dp(20),dp(14),dp(14),dp(14));
        searchBar.addView(searchField,new LinearLayout.LayoutParams(0,dp(58),1f));
        Button submit=button("⌕  Pesquisar",()->runSearch(searchField.getText().toString()));
        searchBar.addView(submit);
        searchField.setOnEditorActionListener((v,id,event)->{
            if(id==EditorInfo.IME_ACTION_SEARCH){
                runSearch(searchField.getText().toString());
                ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(searchField.getWindowToken(),0);
                return true;
            }
            return false;
        });
        LinearLayout filters=new LinearLayout(this);
        LinearLayout.LayoutParams filterLp=new LinearLayout.LayoutParams(-1,-2);filterLp.topMargin=dp(13);
        searchPanel.addView(filters,filterLp);
        String[] types={"all","movie","series"};
        String[] labels={"Tudo","Filmes","Séries"};
        final Button[] filterButtons=new Button[types.length];
        for(int i=0;i<types.length;i++){
            final String type=types[i];
            final int choice=i;
            Button filter=button((type.equals(searchFilter)?"●  ":"○  ")+labels[i],()->{
                if(searchFilter.equals(type))return;
                searchFilter=type;
                for(int k=0;k<filterButtons.length;k++)
                    filterButtons[k].setText((types[k].equals(searchFilter)?"●  ":"○  ")+labels[k]);
                buildSearchResults();
            });
            filterButtons[i]=filter;
            filters.addView(filter);
        }
        searchFeedback=text("Use o teclado da TV para pesquisar títulos reais.",14,0xff9bb9c7,false);
        LinearLayout.LayoutParams feedbackLp=new LinearLayout.LayoutParams(-1,-2);
        feedbackLp.topMargin=dp(10);
        searchPanel.addView(searchFeedback,feedbackLp);
        searchResultsArea=new LinearLayout(this);
        searchResultsArea.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams out=new LinearLayout.LayoutParams(-1,-2);out.topMargin=dp(17);
        searchPanel.addView(searchResultsArea,out);
        searchField.setText(searchText);
        searchField.setSelection(searchField.length());
        searchField.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence text,int from,int count,int after){}
            @Override public void onTextChanged(CharSequence text,int from,int before,int count){
                if(pendingSearch!=null)ui.removeCallbacks(pendingSearch);
                final String query=text.toString();
                pendingSearch=()->runSearch(query);
                ui.postDelayed(pendingSearch,650L);
            }
            @Override public void afterTextChanged(Editable text){}
        });
        if(!searchText.trim().isEmpty())runSearch(searchText);
        searchField.requestFocus();
    }
    private void runSearch(String query){
        final String normalized=query==null?"":query.trim();
        if(pendingSearch!=null)ui.removeCallbacks(pendingSearch);
        pendingSearch=null;
        searchText=normalized;
        searchGeneration++;
        if(searchFuture!=null)searchFuture.cancel(true);
        final int token=searchGeneration;
        searchMatches.clear();
        if(searchResultsArea==null||!"buscar".equals(activeSection))return;
        searchResultsArea.removeAllViews();
        if(normalized.length()<2){
            searchFeedback.setText("Digite pelo menos 2 caracteres para encontrar um título.");
            return;
        }
        if(normalized.length()>120){
            searchFeedback.setText("Limite de 120 caracteres por pesquisa.");
            return;
        }
        // Results already loaded on the main screen can be shown immediately.
        String needle=normalized.toLowerCase(Locale.ROOT);
        for(Media m:knownItems)if(!isChannel(m)&&m.name.toLowerCase(Locale.ROOT).contains(needle))
            searchMatches.put(identity(m),m);
        searchFeedback.setText("Procurando “"+normalized+"” nos addons…");
        buildSearchResults();
        final ArrayList<String> providers=new ArrayList<>(sources);
        // Search endpoints belong to catalog resources whose manifest declares the search extra.
        searchFuture=searchExecutor.submit(()->{
            int checked=0,failed=0,supported=0;
            for(String addon:providers){
                if(token!=searchGeneration||Thread.currentThread().isInterrupted())break;
                try{
                    JSONObject manifest=getManifest(addon);
                    // Search is authorized by each catalog's declared "search" extra,
                    // not by resource idPrefixes (search has no item ID yet).
                    JSONArray catalogs=manifest.optJSONArray("catalogs");
                    if(catalogs==null)continue;
                    String root=addon.substring(0,addon.length()-"manifest.json".length());
                    int addonChecked=0;
                    for(int c=0;c<catalogs.length()&&addonChecked<5;c++){
                        if(token!=searchGeneration||Thread.currentThread().isInterrupted())break;
                        JSONObject catalog=catalogs.optJSONObject(c);if(catalog==null)continue;
                        String type=catalog.optString("type","");
                        String id=catalog.optString("id","");
                        if(!("movie".equals(type)||"series".equals(type)||"tv".equals(type)))continue;
                        if(isBroadcastCatalog(manifest,catalog,addon))continue;
                        if(!id.matches("[a-zA-Z0-9_.:-]{1,100}"))continue;
                        JSONArray extras=catalog.optJSONArray("extra");
                        if(extras==null)continue;
                        boolean canSearch=false;
                        for(int e=0;e<extras.length();e++){
                            Object item=extras.opt(e);
                            if(item instanceof JSONObject&&"search".equals(((JSONObject)item).optString("name")))
                                canSearch=true;
                            else if("search".equals(item))canSearch=true;
                        }
                        if(!canSearch)continue;
                        addonChecked++;supported++;
                        try{
                            String encoded=URLEncoder.encode(normalized,"UTF-8").replace("+","%20");
                            JSONObject answer=getJson(root+"catalog/"+type+"/"+id+"/search="+encoded+".json");
                            JSONArray metas=answer.optJSONArray("metas");
                            checked++;
                            if(metas==null)continue;
                            ArrayList<Media> found=new ArrayList<>();
                            for(int j=0;j<metas.length()&&j<35;j++){
                                JSONObject json=metas.optJSONObject(j);if(json==null)continue;
                                String mediaId=json.optString("id","");
                                String mediaType=json.optString("type",type);
                                if(mediaId.isEmpty()||json.optString("name","").isEmpty())continue;
                                Media m=new Media();
                                m.id=mediaId;m.type=mediaType;
                                m.name=json.optString("name","");m.poster=json.optString("poster","");
                                m.description=json.optString("description","");
                                m.origin=manifest.optString("name","Addon");m.catalogId=id;
                                m.source=root;
                                JSONArray genres=json.optJSONArray("genres");
                                if(genres!=null)for(int k=0;k<genres.length()&&k<8;k++){
                                    if(!m.genre.isEmpty())m.genre+=",";
                                    m.genre+=genres.optString(k,"");
                                }
                                found.add(m);
                            }
                            if(!found.isEmpty())runOnUiThread(()->{
                                if(token!=searchGeneration||!"buscar".equals(activeSection))return;
                                for(Media m:found){
                                    String key=identity(m);
                                    Media current=searchMatches.get(key);
                                    if(current==null)searchMatches.put(key,m);
                                    else {
                                        if(current.poster.isEmpty())current.poster=m.poster;
                                        if(current.description.isEmpty())current.description=m.description;
                                    }
                                }
                                scheduleSearchResults();
                            });
                        }catch(Exception e){failed++;}
                    }
                }catch(Exception ex){failed++;}
            }
            final int attempts=checked,errors=failed,eligible=supported;
            runOnUiThread(()->{
                if(token!=searchGeneration||!"buscar".equals(activeSection))return;
                int size=searchMatches.size();
                if(size==0)
                    searchFeedback.setText("Nenhum resultado para “"+normalized+"”. Tente outro título.");
                else searchFeedback.setText(size+" resultado(s) encontrados para “"+normalized+"”.");
                if(eligible==0&&size==0)
                    searchFeedback.setText("Nenhum addon ativo oferece pesquisa. Confira as fontes.");
                else if(errors>0&&attempts==0&&size==0)
                    searchFeedback.setText("As fontes de pesquisa não responderam. Tente novamente.");
                buildSearchResults();
            });
        });
    }
    private void scheduleSearchResults(){
        if(pendingSearchRender!=null)ui.removeCallbacks(pendingSearchRender);
        pendingSearchRender=()->buildSearchResults();
        ui.postDelayed(pendingSearchRender,260L);
    }
    private void buildSearchResults(){
        if(searchResultsArea==null||!"buscar".equals(activeSection))return;
        searchResultsArea.removeAllViews();
        ArrayList<Media> list=new ArrayList<>();
        for(Media m:searchMatches.values()){
            if(("all".equals(searchFilter)||
                 ("movie".equals(searchFilter)&&"movie".equals(m.type))||
                 ("series".equals(searchFilter)&&"series".equals(m.type))))
                list.add(m);
        }
        final String query=searchText.toLowerCase(Locale.ROOT);
        Collections.sort(list,(left,right)->{
            int l=rankSearch(left.name,query),r=rankSearch(right.name,query);
            int c=Integer.compare(r,l);
            return c!=0?c:left.name.compareToIgnoreCase(right.name);
        });
        if(list.isEmpty())return;
        TextView total=text(list.size()+" títulos encontrados",16,Color.WHITE,true);
        total.setPadding(0,0,0,dp(10));searchResultsArea.addView(total);
        GridLayout grid=new GridLayout(this);grid.setColumnCount(4);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        searchResultsArea.addView(grid);
        for(int i=0;i<list.size()&&i<64;i++){
            Media item=list.get(i);
            LinearLayout tile=new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setBackground(shape(PANEL,0));
            tile.setFocusable(true);
            GridLayout.LayoutParams tileParams=new GridLayout.LayoutParams();
            tileParams.width=dp(151);tileParams.height=dp(258);
            tileParams.setMargins(0,0,dp(13),dp(14));
            grid.addView(tile,tileParams);
            ImageView poster=new ImageView(this);
            poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
            poster.setBackgroundColor(0xff1c3449);
            tile.addView(poster,new LinearLayout.LayoutParams(-1,dp(205)));
            TextView name=text(item.name,13,Color.WHITE,true);
            name.setMaxLines(2);name.setPadding(dp(7),dp(7),dp(6),0);
            tile.addView(name);
            tile.setOnClickListener(v->details(item));
            tile.setOnFocusChangeListener((v,focus)->{
                v.setBackground(shape(focus?PANEL2:PANEL,focus?AQUA:0));
                v.animate().scaleX(focus?1.05f:1f).scaleY(focus?1.05f:1f).setDuration(130L).start();
                name.setTextColor(focus?AQUA:Color.WHITE);
            });
            if(item.poster.startsWith("https://"))loadPoster(item.poster,poster);
        }
    }
    private int rankSearch(String title,String query){
        String value=title.toLowerCase(Locale.ROOT);
        if(value.equals(query))return 100;
        if(value.startsWith(query))return 75;
        return value.contains(query)?50:10;
    }

    /** A catalog does not imply a meta resource. Resolve detailed series metadata across eligible addons. */
    private void resolveItem(Media m) {
        if(!isSeries(m) || (m.episodeId!=null&&!m.episodeId.isEmpty())){
            resolveStreams(m,(m.episodeId!=null&&!m.episodeId.isEmpty())?m.episodeId:m.id);
            return;
        }
        final String cacheKey=m.type+"|"+m.id;
        JSONArray cached;
        synchronized(episodeCache){cached=episodeCache.get(cacheKey);}
        if(cached!=null&&cached.length()>0){
            showEpisodePicker(m,cached);
            return;
        }
        if(subtitle!=null)subtitle.setText("Consultando os episódios...");
        pool.execute(()->{
            ArrayList<String> candidateUrls=new ArrayList<>(sources);
            // Cinemeta is a dedicated metadata provider for IMDb IDs, regardless of which addon showed the item.
            // Prefer it to FenixFlix, Torrentio and catalog-only providers.
            if(m.id.startsWith("tt") && candidateUrls.remove(CINEMETA))candidateUrls.add(0,CINEMETA);
            int compatible=0,errors=0,withoutEpisodes=0;
            JSONArray episodes=null;
            for(String manifestUrl:candidateUrls){
                try{
                    JSONObject manifest=getManifest(manifestUrl);
                    if(!supportsResource(manifest,"meta",m.type,m.id))continue;
                    compatible++;
                    String root=manifestUrl.substring(0,manifestUrl.length()-"manifest.json".length());
                    JSONObject answer=getJson(root+"meta/"+encodePart(m.type)+"/"+encodePart(m.id)+".json");
                    JSONObject meta=answer.optJSONObject("meta");
                    if(meta==null){withoutEpisodes++;continue;}
                    JSONArray videos=meta.optJSONArray("videos");
                    if(videos==null||videos.length()==0){withoutEpisodes++;continue;}
                    boolean valid=false;
                    for(int i=0;i<videos.length();i++){
                        JSONObject episode=videos.optJSONObject(i);
                        if(episode!=null&&!episode.optString("id","").isEmpty()){valid=true;break;}
                    }
                    if(!valid){withoutEpisodes++;continue;}
                    episodes=videos;
                    break;
                }catch(Exception ex){
                    errors++;
                    android.util.Log.w("Telares-Addon","Metadata addon unavailable ("+safeHost(manifestUrl)+"): "+ex.getClass().getSimpleName());
                }
            }
            final JSONArray result=episodes;
            final int countCompatible=compatible,failed=errors,empty=withoutEpisodes;
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                if(subtitle!=null)subtitle.setText("CATÁLOGOS • "+sources.size()+" FONTES");
                if(result!=null){
                    synchronized(episodeCache){episodeCache.put(cacheKey,result);}
                    showEpisodePicker(m,result);
                }else{
                    String status=countCompatible==0
                       ?"Nenhum addon ativo informou suporte a metadados desta série."
                       :"Os "+countCompatible+" addon(s) compatíveis não forneceram uma lista de episódios.";
                    message(status+"\n\nVerifique as fontes ativas, a conexão e tente novamente. "
                            +"O título foi encontrado no catálogo, mas os episódios ainda não estão disponíveis."
                            +"\nRespostas inválidas: "+failed+" • Sem episódios: "+empty);
                }
            });
        });
    }
    private String safeHost(String url){
        try{return new URI(url).getHost();}catch(Exception ex){return "fonte";}
    }
    /** TV-friendly season browser; episodes are only taken from actual meta.videos, never invented. */
    private void showEpisodePicker(Media parent,JSONArray videos){
        ArrayList<JSONObject> all=new ArrayList<>();
        for(int i=0;i<videos.length()&&i<1200;i++){
            JSONObject item=videos.optJSONObject(i);
            if(item!=null&&!item.optString("id","").isEmpty())all.add(item);
        }
        Collections.sort(all,(a,b)->{
            int bySeason=Integer.compare(a.optInt("season",0),b.optInt("season",0));
            return bySeason!=0?bySeason:Integer.compare(a.optInt("episode",0),b.optInt("episode",0));
        });
        if(all.isEmpty()){message("O addon não enviou episódios válidos.");return;}
        ScrollView page=new ScrollView(this);
        page.setBackgroundColor(BG);
        LinearLayout column=new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(45),dp(25),dp(42),dp(38));
        page.addView(column);
        column.addView(button("← Voltar aos detalhes",()->details(parent)));
        TextView title=text(parent.name,27,Color.WHITE,true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(18);
        column.addView(title,p);
        TextView section=text(all.size()+" episódios disponíveis nos metadados",14,0xff90b3c6,false);
        column.addView(section);
        int lastSeason=Integer.MIN_VALUE;
        for(JSONObject ep:all){
            int season=ep.optInt("season",0),episode=ep.optInt("episode",0);
            if(season!=lastSeason){
                lastSeason=season;
                TextView head=text(season==0?"ESPECIAIS":"TEMPORADA "+season,19,AQUA,true);
                LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(23);hp.bottomMargin=dp(7);
                column.addView(head,hp);
            }
            String epId=ep.optString("id","");
            String epTitle=ep.optString("title","Episódio "+episode);
            Media item=new Media();
            item.id=parent.id;item.type=parent.type;item.episodeId=epId;
            item.name=parent.name+" · T"+season+"E"+episode+" · "+epTitle;
            item.source=parent.source;item.poster=parent.poster;item.description=ep.optString("overview",parent.description);
            item.genre=parent.genre;
            Button entry=button("E"+episode+"   "+epTitle,()->{
                // Episode selection directly queries streams; no stale origin-only metadata request.
                resolveStreams(item,epId);
            });
            entry.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            LinearLayout.LayoutParams epParams=new LinearLayout.LayoutParams(-1,dp(52));epParams.bottomMargin=dp(6);
            column.addView(entry,epParams);
        }
        showingDetails=true;
        setContentView(page);
        page.requestFocus();
    }
    private String encodePart(String value) throws Exception {
        return URLEncoder.encode(value,"UTF-8").replace("+","%20");
    }
    /** Query stream resource only from addons which declare stream capability. */
    /** Collect candidates, probe their HTTP responses, then start the best reachable option. */
    private void resolveStreams(Media media,String videoId) {
        if(subtitle!=null)subtitle.setText("Encontrando uma reprodução disponível…");
        pool.execute(()->{
            ArrayList<StreamProbe.Option> candidates=new ArrayList<>();
            Set<String> seen=new HashSet<>();
            int torrents=0,external=0,bad=0,failed=0;
            for(String manifestUrl:new ArrayList<>(sources)){
                try{
                    JSONObject manifest=getManifest(manifestUrl);
                    if(!supportsStreams(manifest,media.type,videoId))continue;
                    String root=manifestUrl.substring(0,manifestUrl.length()-"manifest.json".length());
                    JSONObject response=getJson(root+"stream/"+encodePart(media.type)+"/"+encodePart(videoId)+".json");
                    JSONArray streams=response.optJSONArray("streams");
                    if(streams==null)continue;
                    for(int i=0;i<streams.length()&&i<60;i++){
                        JSONObject stream=streams.optJSONObject(i);if(stream==null)continue;
                        String url=stream.optString("url","");
                        if(!isHttpMediaCandidate(url)){
                            if(!stream.optString("infoHash","").isEmpty())torrents++;
                            else if(!stream.optString("externalUrl","").isEmpty()||
                                    !stream.optString("ytId","").isEmpty())external++;
                            else bad++;
                            continue;
                        }
                        JSONObject hints=stream.optJSONObject("behaviorHints");
                        JSONObject request=null;
                        if(hints!=null){
                            JSONObject proxy=hints.optJSONObject("proxyHeaders");
                            if(proxy!=null)request=proxy.optJSONObject("request");
                        }
                        ArrayList<String> names=new ArrayList<>(),values=new ArrayList<>();
                        if(request!=null){
                            String[] allowed={"User-Agent","Referer","Origin","Accept","Authorization","Cookie"};
                            for(String name:allowed){
                                String value=request.optString(name,"");
                                if(value.isEmpty()||value.length()>2048||value.indexOf('\r')>=0||value.indexOf('\n')>=0)continue;
                                names.add(name);values.add(value);
                            }
                        }
                        String signature=url+"|"+names.toString()+"|"+values.toString();
                        if(!seen.add(signature))continue;
                        String label=stream.optString("name","Stream");
                        String details=stream.optString("description",stream.optString("title",""));
                        String displayed=manifest.optString("name","Addon")+" • "+label+
                            (details.isEmpty()?"":" • "+details);
                        candidates.add(StreamProbe.candidate(url,displayed,
                            names.toArray(new String[0]),values.toArray(new String[0])));
                    }
                }catch(Exception ex){
                    failed++;
                    android.util.Log.w("Telares-Addon","Falha na busca de streams: "+safeHost(manifestUrl));
                }
            }
            final int skippedTorrents=torrents,skippedExternal=external,skippedBad=bad,failedAddons=failed;
            final int candidateCount=candidates.size();
            // Bounded concurrency and a short-range GET: an HTTP success is preliminary, not proof of decodability.
            if(!candidates.isEmpty())runOnUiThread(()->{
                if(!isFinishing())Toast.makeText(this,
                    "Verificando "+Math.min(24,candidates.size())+" fontes de reprodução…",
                    Toast.LENGTH_SHORT).show();
            });
            StreamProbe.Report checked=StreamProbe.verify(candidates);
            // Respect preferences selected in the player without claiming all streams will decode.
            android.content.SharedPreferences settings=
                getSharedPreferences("shock_tv_stream_prefs",MODE_PRIVATE);
            final String preferredQuality=settings.getString("quality","");
            final String preferredLanguage=settings.getString("language","");
            if(!preferredQuality.isEmpty()||!preferredLanguage.isEmpty()){
                checked.available.sort((left,right)->{
                    int leftScore=StreamProbe.score(left)
                        +(!preferredQuality.isEmpty()&&preferredQuality.equals(left.quality)?60:0)
                        +(!preferredLanguage.isEmpty()&&preferredLanguage.equals(left.language)?50:0);
                    int rightScore=StreamProbe.score(right)
                        +(!preferredQuality.isEmpty()&&preferredQuality.equals(right.quality)?60:0)
                        +(!preferredLanguage.isEmpty()&&preferredLanguage.equals(right.language)?50:0);
                    return Integer.compare(rightScore,leftScore);
                });
            }
            if(isChannel(media)){
                // Live HLS endpoints frequently reject short-range probes while ExoPlayer
                // can load their playlists. Append bounded direct candidates as fallbacks.
                Set<String> verifiedUrls=new HashSet<>();
                for(StreamProbe.Option o:checked.available)verifiedUrls.add(o.url);
                for(StreamProbe.Option candidate:candidates){
                    if(checked.available.size()>=24)break;
                    if(verifiedUrls.add(candidate.url))checked.available.add(candidate);
                }
            }
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                if(subtitle!=null)subtitle.setText("CATÁLOGOS • "+sources.size()+" FONTES");
                if(checked.available.isEmpty()){
                    String detail=candidateCount>0
                        ?"Nenhuma das "+Math.min(candidateCount,24)+" URLs verificadas respondeu como mídia HTTP válida."
                        :"Nenhuma URL direta foi fornecida pelos addons ativos.";
                    message(detail+"\n\n"+skippedTorrents+" fontes BitTorrent não são compatíveis com este player."
                        +"\nLinks externos: "+skippedExternal+" • Outras entradas: "+skippedBad+
                        " • Addons que falharam: "+failedAddons+
                        "\n\nTente outra fonte ou outro título.");
                    return;
                }
                StreamProbe.Option best=checked.available.get(0);
                Toast.makeText(this,checked.available.size()+" opção(ões) HTTP verificadas · "+
                     best.shortLabel(),Toast.LENGTH_LONG).show();
                play(best.url,media,videoId,false,best.headers,best.values,checked.toJson().toString());
            });
        });
    }
    private boolean isHttpMediaCandidate(String url){
        try {
            java.net.URI parsed=new java.net.URI(url);
            return ("https".equalsIgnoreCase(parsed.getScheme())||"http".equalsIgnoreCase(parsed.getScheme()))
                && parsed.getHost()!=null&&!parsed.getHost().isEmpty();
        }catch(Exception ex){return false;}
    }
    private boolean supportsStreams(JSONObject manifest,String type,String videoId){
        return supportsResource(manifest,"stream",type,videoId);
    }
    /** Protocol filtering: per-resource type/prefix declarations OVERRIDE manifest-wide defaults. */
    private boolean supportsResource(JSONObject manifest,String resourceName,String type,String id){
        JSONArray resources=manifest.optJSONArray("resources");
        if(resources==null)return false;
        for(int i=0;i<resources.length();i++){
            Object resource=resources.opt(i);
            JSONObject qualified=resource instanceof JSONObject?(JSONObject)resource:null;
            if(qualified==null && !resourceName.equals(resource))continue;
            if(qualified!=null && !resourceName.equals(qualified.optString("name")))continue;
            JSONArray types=qualified!=null&&qualified.has("types")?
                qualified.optJSONArray("types"):manifest.optJSONArray("types");
            JSONArray prefixes=qualified!=null&&qualified.has("idPrefixes")?
                qualified.optJSONArray("idPrefixes"):manifest.optJSONArray("idPrefixes");
            if(types!=null&&!contains(types,type))continue;
            if(prefixes!=null&&prefixes.length()>0&&!startsWithAny(prefixes,id))continue;
            return true;
        }
        return false;
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
        play(url,m,videoId,restart,new String[0],new String[0]);
    }
    private void play(String url,Media m,String videoId,boolean restart,String[] headerNames,String[] headerValues){
        play(url,m,videoId,restart,headerNames,headerValues,null);
    }
    private void play(String url,Media m,String videoId,boolean restart,String[] headerNames,String[] headerValues,String verifiedOptions){
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
        intent.putExtra("isLive",isChannel(m));
        intent.putExtra("poster",m.poster);intent.putExtra("description",m.description);
        intent.putExtra("source",m.source);intent.putExtra("episodeId",m.episodeId);
        intent.putExtra("libraryKey",key);intent.putExtra("restart",restart);
        intent.putExtra("streamHeaderNames",headerNames);intent.putExtra("streamHeaderValues",headerValues);
        if(verifiedOptions!=null)intent.putExtra("verifiedStreamOptions",verifiedOptions);
        startActivity(intent);
    }
    private void message(String text){new AlertDialog.Builder(this).setTitle("Telares TV").setMessage(text).setPositiveButton("OK",null).show();}
    private static final class Media{String id="",type="",displayType="",name="",poster="",description="",source="",episodeId="",origin="",catalogId="",genre="";}
    private JSONObject getJson(String url) throws Exception{
        if(!url.startsWith("https://"))throw new IOException("Este addon exige conexão HTTPS.");
        HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(8500);
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("User-Agent","Telares/1.0");
        connection.setInstanceFollowRedirects(true);
        try{
            int code=connection.getResponseCode();
            if(code<200||code>=300)throw new IOException("Servidor retornou HTTP "+code);
            if(!"https".equalsIgnoreCase(connection.getURL().getProtocol()))
                throw new IOException("Redirecionamento para conexão não segura.");
            byte[] response;
            try(InputStream in=connection.getInputStream();
                ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){
                    out.write(buffer,0,n);
                    if(out.size()>3000000)throw new IOException("Resposta muito grande.");
                }
                response=out.toByteArray();
            }
            String content=new String(response,java.nio.charset.StandardCharsets.UTF_8).trim();
            if(content.startsWith("\uFEFF"))content=content.substring(1).trim();
            if(content.startsWith("<")){
                throw new IOException("O servidor enviou uma página HTML em vez de dados JSON.");
            }
            if(!content.startsWith("{")){
                throw new IOException("O addon retornou um formato de dados incompatível.");
            }
            try{return new JSONObject(content);}
            catch(org.json.JSONException ex){
                throw new IOException("O addon retornou JSON inválido.",ex);
            }
        }finally{connection.disconnect();}
    }
    @Override protected void onResume(){super.onResume();if(personal!=null)showPersonal();}
    @Override protected void onDestroy(){
        searchGeneration++;
        if(searchFuture!=null)searchFuture.cancel(true);
        searchExecutor.shutdownNow();
        if(pendingSearch!=null)ui.removeCallbacks(pendingSearch);
        if(pendingSearchRender!=null)ui.removeCallbacks(pendingSearchRender);
        pool.shutdownNow();
        super.onDestroy();
    }
}
