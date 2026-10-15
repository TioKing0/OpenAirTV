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
    private final ExecutorService pool=Executors.newFixedThreadPool(5);
    private final ArrayList<String> sources=new ArrayList<>();
    private LinearLayout content;
    private TextView subtitle;
    private UserLibrary library;
    private LinearLayout personal;
    private final ArrayList<Media> knownItems=new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        library=new UserLibrary(this);
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
        header.addView(button("⌕ Buscar",()->searchCatalog()));
        header.addView(button("Minha biblioteca",()->showFullLibrary()));
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
        nav.addView(button("Assistir mais tarde",()->showAll("later")));
        nav.addView(button("▶ Curtidos",()->playLikedPlaylist()));
        hero.addView(nav);
        personal=new LinearLayout(this);personal.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,-2);pp.topMargin=dp(20);
        content.addView(personal,pp);
        showPersonal();

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
                        runOnUiThread(()->{
                            for(Media newItem:found){
                                boolean exists=false;
                                for(Media current:knownItems)if(current.id.equals(newItem.id)&&current.type.equals(newItem.type)){exists=true;break;}
                                if(!exists)knownItems.add(newItem);
                            }
                            addCatalog(section,found);
                        });
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
    private void details(Media m){
        final UserLibrary.Entry e=identify(m);
        final ArrayList<String> options=new ArrayList<>();
        options.add("▶  Escolher reprodução / episódio");
        if(e.canContinue() && !e.streamUrl.isEmpty())options.add("↻  Continuar de "+e.progressLabel().replace("Pausado em ",""));
        options.add(e.liked?"♥  Remover dos curtidos":"♡  Adicionar aos curtidos");
        options.add(e.watchLater?"✓  Remover de Assistir mais tarde":"◷  Assistir mais tarde");
        if(e.lastPlayed>0)options.add("⌫  Remover do histórico");
        options.add("ⓘ  Informações do título");
        new AlertDialog.Builder(this).setTitle(m.name)
          .setItems(options.toArray(new String[0]),(dialog,which)->{
             String choice=options.get(which);
             if(choice.startsWith("▶"))resolveItem(m);
             else if(choice.startsWith("↻"))resumeEntry(library.get(e.key));
             else if(choice.startsWith("♥")||choice.startsWith("♡")){
                 library.liked(e.key,!library.get(e.key).liked);
                 showPersonal();Toast.makeText(this,"Lista de curtidos atualizada",Toast.LENGTH_SHORT).show();
             }else if(choice.startsWith("✓")||choice.startsWith("◷")){
                 library.later(e.key,!library.get(e.key).watchLater);
                 showPersonal();Toast.makeText(this,"Assistir mais tarde atualizado",Toast.LENGTH_SHORT).show();
             }else if(choice.startsWith("⌫")){
                 library.removeHistory(e.key);showPersonal();
                 Toast.makeText(this,"Título removido do histórico",Toast.LENGTH_SHORT).show();
             }else{
                 message((m.description==null?"":m.description)+"\n\n"+library.get(e.key).progressLabel());
             }
          }).setNegativeButton("Voltar",null).show();
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
        addPersonalRow("CONTINUAR ASSISTINDO","continue","Nenhum vídeo interrompido.");
        addPersonalRow("ASSISTIR MAIS TARDE","later","Sua lista está vazia.");
        addPersonalRow("CURTIDOS  ♥","liked","Marque seus favoritos no catálogo.");
        addPersonalRow("ASSISTIDOS RECENTEMENTE","recent","Seu histórico começa quando você reproduz um vídeo.");
    }
    private void addPersonalRow(String title,String filter,String empty){
        ArrayList<UserLibrary.Entry> entries=library.list(filter);
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
    private static final class Media{String id="",type="",name="",poster="",description="",source="",episodeId="";}
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
