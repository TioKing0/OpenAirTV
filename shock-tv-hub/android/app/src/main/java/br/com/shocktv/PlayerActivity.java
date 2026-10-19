package br.com.shocktv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Full-screen player with bounded verified stream alternatives, adaptive tracks and quick D-pad controls. */
@UnstableApi
public final class PlayerActivity extends Activity {
    private static final String TAG="Telares-Player";
    private ExoPlayer player;
    private TorrentEngine torrentEngine;
    private int torrentGeneration=0;
    private final java.util.concurrent.ExecutorService torrentWorker=java.util.concurrent.Executors.newSingleThreadExecutor();
    private PlayerView playerView;
    private UserLibrary library;
    private String currentKey;
    private String title;
    private final ArrayList<StreamProbe.Option> verified=new ArrayList<>();
    private final Set<String> failedSources=new HashSet<>();
    private int selectedIndex=-1;
    private boolean startedCurrent=false,markedFinished=false,showingError=false;
    private boolean queue=false,closing=false,isLive=false;
    private long lastKnownPosition=0L;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable periodic=new Runnable(){
        @Override public void run(){
            save(false);
            if(!closing)handler.postDelayed(this,10000L);
        }
    };
    private String[] queuedUrls,queuedKeys;
    private Button qualityButton,languageButton,sourceButton;
    private LinearLayout quickControls;
    private boolean optionMenuOpen=false;
    private static final long CONTROLS_TIMEOUT_MS=5000L;
    private final Runnable hideControls=()->hideQuickControls();

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        String original=getIntent().getStringExtra("url");
        title=getIntent().getStringExtra("title");
        currentKey=getIntent().getStringExtra("libraryKey");
        isLive=getIntent().getBooleanExtra("isLive",false);
        if(!isMediaUrl(original)||currentKey==null||currentKey.isEmpty()){
            Toast.makeText(this,"Endereço de mídia inválido.",Toast.LENGTH_LONG).show();
            finish();return;
        }
        library=new UserLibrary(this);
        library.identify(currentKey,getIntent().getStringExtra("id"),getIntent().getStringExtra("type"),
            title,getIntent().getStringExtra("poster"),getIntent().getStringExtra("description"),
            getIntent().getStringExtra("source"),getIntent().getStringExtra("episodeId"));
        UserLibrary.Entry prior=library.get(currentKey);
        lastKnownPosition=!isLive&&!getIntent().getBooleanExtra("restart",false)&&prior.canContinue()?
            prior.positionMs:0;
        queuedUrls=getIntent().getStringArrayExtra("playlistUrls");
        queuedKeys=getIntent().getStringArrayExtra("playlistKeys");
        queue=queuedUrls!=null&&queuedKeys!=null&&queuedUrls.length>0&&
              queuedUrls.length==queuedKeys.length&&queuedUrls.length<=200;

        String encoded=getIntent().getStringExtra("verifiedStreamOptions");
        if(!queue&&encoded!=null){
            try{
                JSONArray arr=new JSONArray(encoded);
                for(int i=0;i<arr.length()&&i<24;i++){
                    JSONObject obj=arr.optJSONObject(i);if(obj==null)continue;
                    StreamProbe.Option o=StreamProbe.Option.fromJson(obj);
                    if(isMediaUrl(o.url))verified.add(o);
                }
            }catch(Exception ignored){}
        }
        if(verified.isEmpty()){
            StreamProbe.Option direct=StreamProbe.candidate(original,
               "Fonte selecionada",getIntent().getStringArrayExtra("streamHeaderNames"),
               getIntent().getStringArrayExtra("streamHeaderValues"));
            verified.add(direct);
        }
        selectedIndex=0;
        for(int i=0;i<verified.size();i++)
            if(original.equals(verified.get(i).url)){selectedIndex=i;break;}

        FrameLayout layout=new FrameLayout(this);
        layout.setBackgroundColor(0xff000000);
        playerView=new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerShowTimeoutMs(5000);
        playerView.setControllerAutoShow(false);
        playerView.setFocusable(true);playerView.setFocusableInTouchMode(true);
        playerView.setKeepScreenOn(true);
        playerView.setShowSubtitleButton(true);
        layout.addView(playerView,new FrameLayout.LayoutParams(-1,-1));

        if(!queue)installQuickControls(layout);
        setContentView(layout);
        playOption(selectedIndex,lastKnownPosition,false);

        handler.postDelayed(periodic,10000L);
        playerView.requestFocus();
        playerView.hideController();
        if(!queue)hideQuickControls();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getAction()!=KeyEvent.ACTION_DOWN||queue||optionMenuOpen)
            return super.dispatchKeyEvent(event);
        int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_INFO){
            showQuickControls(true);
            return true;
        }
        if(key==KeyEvent.KEYCODE_DPAD_UP && (!isQuickVisible()||playerView.hasFocus())){
            showQuickControls(true);
            return true;
        }
        if((key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER) &&
            !isQuickVisible() && player!=null){
            player.setPlayWhenReady(!player.getPlayWhenReady());
            playerView.showController();
            showQuickControls(false);
            return true;
        }
        if((key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT)&&
            playerView.hasFocus()&&!playerView.isControllerFullyVisible()&&player!=null){
            long next=player.getCurrentPosition()+
                 (key==KeyEvent.KEYCODE_DPAD_LEFT?-10000L:10000L);
            player.seekTo(Math.max(0L,next));
            playerView.showController();
            showQuickControls(false);
            return true;
        }
        if(isQuickVisible()&&
            (key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||
             key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_CENTER||
             key==KeyEvent.KEYCODE_ENTER)){
            scheduleQuickHide();
        }
        return super.dispatchKeyEvent(event);
    }
    private boolean isQuickVisible(){
        return quickControls!=null&&quickControls.getVisibility()==View.VISIBLE;
    }
    private void showQuickControls(boolean focusFirst){
        if(queue||quickControls==null||closing)return;
        if(!isQuickVisible()){
            quickControls.setAlpha(0f);
            quickControls.setVisibility(View.VISIBLE);
            quickControls.animate().alpha(1f).setDuration(175L).start();
        }
        if(focusFirst&&qualityButton!=null)qualityButton.requestFocus();
        scheduleQuickHide();
    }
    private void scheduleQuickHide(){
        handler.removeCallbacks(hideControls);
        if(!optionMenuOpen)handler.postDelayed(hideControls,CONTROLS_TIMEOUT_MS);
    }
    private void hideQuickControls(){
        if(quickControls==null||optionMenuOpen)return;
        handler.removeCallbacks(hideControls);
        if(quickControls.getVisibility()==View.GONE)return;
        if(quickControls.hasFocus()&&playerView!=null)playerView.requestFocus();
        quickControls.animate().alpha(0f).setDuration(230L)
            .withEndAction(()->{if(!optionMenuOpen&&quickControls!=null)
                quickControls.setVisibility(View.GONE);}).start();
    }
    @Override public void onBackPressed(){
        if(optionMenuOpen){super.onBackPressed();return;}
        if(isQuickVisible()){hideQuickControls();playerView.hideController();return;}
        if(playerView!=null&&playerView.isControllerFullyVisible()){
            playerView.hideController();return;
        }
        super.onBackPressed();
    }

    private int dp(int size){return (int)(size*getResources().getDisplayMetrics().density+.5f);}
    private Button control(String label){
        Button b=new Button(this);
        b.setText(label);b.setTextSize(13);b.setTextColor(0xffedfaff);
        b.setAllCaps(false);b.setFocusable(true);
        b.setBackgroundTintList(ColorStateList.valueOf(0xff16485b));
        b.setMinWidth(0);b.setMinimumWidth(0);
        return b;
    }
    private void installQuickControls(FrameLayout root){
        LinearLayout top=new LinearLayout(this);
        quickControls=top;
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8),0,dp(8),0);
        top.setBackgroundColor(0xbb06121c);
        qualityButton=control("Qualidade");
        languageButton=control("Idioma");
        sourceButton=control("Fontes");
        top.addView(qualityButton);top.addView(languageButton);top.addView(sourceButton);

        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(-2,dp(52),Gravity.TOP|Gravity.RIGHT);
        params.topMargin=dp(6);params.rightMargin=dp(16);
        root.addView(top,params);
        qualityButton.setOnClickListener(v->showQualityOptions());
        languageButton.setOnClickListener(v->showLanguageOptions());
        sourceButton.setOnClickListener(v->showSourceOptions());
        updateControls();
        top.setVisibility(View.GONE);
        View.OnFocusChangeListener focus=(v,active)->{
            v.setScaleX(active?1.035f:1f);
            v.setScaleY(active?1.035f:1f);
            if(active){
                v.setBackgroundTintList(ColorStateList.valueOf(0xff276b85));
                scheduleQuickHide();
            }else v.setBackgroundTintList(ColorStateList.valueOf(0xff16485b));
        };
        qualityButton.setOnFocusChangeListener(focus);
        languageButton.setOnFocusChangeListener(focus);
        sourceButton.setOnFocusChangeListener(focus);
    }
    private void updateControls(){
        if(queue||verified.isEmpty())return;
        StreamProbe.Option o=verified.get(selectedIndex);
        qualityButton.setText("Qualidade · "+o.quality);
        languageButton.setText("Idioma · "+o.language);
        sourceButton.setText("Fontes · "+verified.size());
    }
    private void showQualityOptions(){
        optionMenuOpen=true;
        handler.removeCallbacks(hideControls);
        LinkedHashMap<String,Integer> byQuality=new LinkedHashMap<>();
        for(int i=0;i<verified.size();i++){
            String quality=verified.get(i).quality;
            if(!byQuality.containsKey(quality))byQuality.put(quality,i);
        }
        ArrayList<String> names=new ArrayList<>(byQuality.keySet());
        // Same media with HLS/DASH often contains adaptive video tracks inside one URL.
        names.add("Automática · adaptação à conexão");
        new AlertDialog.Builder(this).setTitle("Qualidade disponível")
            .setItems(names.toArray(new String[0]),(dialog,index)->{
                if(index==names.size()-1){
                    getSharedPreferences("shock_tv_stream_prefs",MODE_PRIVATE)
                        .edit().remove("quality").apply();
                    if(player!=null)player.setTrackSelectionParameters(player.getTrackSelectionParameters()
                        .buildUpon().setMaxVideoSize(Integer.MAX_VALUE,Integer.MAX_VALUE).build());
                    Toast.makeText(this,"Qualidade automática",Toast.LENGTH_SHORT).show();return;
                }
                String chosen=names.get(index);
                int target=byQuality.get(chosen);
                getSharedPreferences("shock_tv_stream_prefs",MODE_PRIVATE)
                    .edit().putString("quality",chosen).apply();
                if(target!=selectedIndex)switchSource(target,true);
                int height=verified.get(target).qualityRank;
                if(player!=null&&height>0){
                    int width=(int)Math.round(height*16.0/9.0);
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters()
                        .buildUpon().setMaxVideoSize(width,height).build());
                }
            }).setNegativeButton("Fechar",null)
            .setOnDismissListener(dialog->{optionMenuOpen=false;scheduleQuickHide();}).show();
    }
    private void showLanguageOptions(){
        optionMenuOpen=true;
        handler.removeCallbacks(hideControls);
        LinkedHashMap<String,Integer> byLanguage=new LinkedHashMap<>();
        for(int i=0;i<verified.size();i++){
            String language=verified.get(i).language;
            if(!byLanguage.containsKey(language))byLanguage.put(language,i);
        }
        // Track-level languages inside one HLS/DASH/MP4 can differ from source labels.
        final LinkedHashMap<String,String> audioTracks=new LinkedHashMap<>();
        if(player!=null){
            for(Tracks.Group group:player.getCurrentTracks().getGroups()){
                if(group.getType()!=C.TRACK_TYPE_AUDIO)continue;
                for(int i=0;i<group.length;i++){
                    String language=group.getTrackFormat(i).language;
                    if(language!=null&&!language.isEmpty()&&!"und".equals(language)){
                        String nativeLabel=new Locale(language).getDisplayLanguage(new Locale("pt","BR"));
                        audioTracks.put(language,nativeLabel);
                    }
                }
            }
        }
        ArrayList<String> choices=new ArrayList<>();
        ArrayList<Runnable> handlers=new ArrayList<>();
        for(Map.Entry<String,Integer> entry:byLanguage.entrySet()){
            final String label=entry.getKey();
            final int at=entry.getValue();
            choices.add("Fonte · "+label);
            handlers.add(()->{
                getSharedPreferences("shock_tv_stream_prefs",MODE_PRIVATE)
                    .edit().putString("language",label).apply();
                switchSource(at,true);
            });
        }
        for(Map.Entry<String,String> item:audioTracks.entrySet()){
            String language=item.getKey();
            choices.add("Faixa de áudio · "+item.getValue());
            handlers.add(()->{
                if(player!=null)player.setTrackSelectionParameters(player.getTrackSelectionParameters()
                    .buildUpon().setPreferredAudioLanguage(language).build());
                Toast.makeText(this,"Áudio: "+item.getValue(),Toast.LENGTH_SHORT).show();
            });
        }
        new AlertDialog.Builder(this).setTitle("Idioma disponível")
            .setItems(choices.toArray(new String[0]),(dialog,index)->handlers.get(index).run())
            .setNegativeButton("Fechar",null)
            .setOnDismissListener(dialog->{optionMenuOpen=false;scheduleQuickHide();}).show();
    }
    private void showSourceOptions(){
        optionMenuOpen=true;
        handler.removeCallbacks(hideControls);
        String[] labels=new String[verified.size()];
        for(int i=0;i<verified.size();i++){
            StreamProbe.Option o=verified.get(i);
            labels[i]=(i==selectedIndex?"▶  ":"✓  ")+o.shortLabel()+
                "  ·  "+o.label.replace('\n',' ').replace('\r',' ');
        }
        new AlertDialog.Builder(this).setTitle("Fontes com resposta HTTP válida")
            .setItems(labels,(dialog,index)->switchSource(index,true))
            .setNegativeButton("Voltar",null)
            .setOnDismissListener(dialog->{optionMenuOpen=false;scheduleQuickHide();}).show();
    }
    private void switchSource(int index,boolean fromUser){
        if(queue||index<0||index>=verified.size())return;
        if(index==selectedIndex&&player!=null)return;
        long position=player==null?lastKnownPosition:Math.max(0,player.getCurrentPosition());
        save(false);
        selectedIndex=index;
        failedSources.remove(verified.get(index).url);
        playOption(index,position,fromUser);
    }
    private void playOption(int index,long position,boolean initiated){
        int generation=++torrentGeneration;
        if(torrentEngine!=null){torrentEngine.close();torrentEngine=null;}
        StreamProbe.Option chosen=verified.get(index);
        if(SmartPlayback.isTorrent(chosen.url)){
            if(player!=null){save(false);player.pause();}
            Toast.makeText(this,"Preparando fonte BitTorrent…",Toast.LENGTH_LONG).show();
            torrentWorker.execute(()->{
                TorrentEngine engine=null;
                try{
                    engine=new TorrentEngine(getCacheDir());final TorrentEngine active=engine;
                    handler.post(()->{if(!closing&&generation==torrentGeneration)torrentEngine=active;else active.close();});
                    String local=engine.open(chosen.url);
                    handler.post(()->{if(!closing&&generation==torrentGeneration)playResolved(index,position,initiated,local);else active.close();});
                }catch(Throwable error){if(engine!=null)engine.close();
                    handler.post(()->{if(!closing&&generation==torrentGeneration)handlePlaybackError(
                        new PlaybackException("BitTorrent indisponível ou cache sem espaço",error,PlaybackException.ERROR_CODE_IO_UNSPECIFIED));});
                }
            });return;
        }
        playResolved(index,position,initiated,chosen.url);
    }
    private void playResolved(int index,long position,boolean initiated,String resolvedUrl){
        StreamProbe.Option chosen=verified.get(index);
        if(player!=null){
            player.pause();
            playerView.setPlayer(null);
            player.release();
            player=null;
        }
        startedCurrent=false;markedFinished=false;showingError=false;
        // Media3 usually refuses cross-protocol 301 by default. Our probe already normalized
        // the final URL. Allow HTTP->HTTPS redirection on header-free public streams.
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
            .setUserAgent("Telares/1.0 (Android TV)")
            .setConnectTimeoutMs(6000).setReadTimeoutMs(12000);
        Map<String,String> headers=new HashMap<>();
        for(int i=0;i<chosen.headers.length&&i<chosen.values.length;i++){
            String header=chosen.headers[i],value=chosen.values[i];
            if(header==null||value==null||value.length()>2048||value.indexOf('\r')>=0||value.indexOf('\n')>=0)continue;
            if(!"https://".equalsIgnoreCase(chosen.url.substring(0,Math.min(8,chosen.url.length())))
                &&("Cookie".equalsIgnoreCase(header)||"Authorization".equalsIgnoreCase(header)))continue;
            if("User-Agent".equalsIgnoreCase(header)||"Referer".equalsIgnoreCase(header)||
                "Origin".equalsIgnoreCase(header)||"Accept".equalsIgnoreCase(header)||
                "Authorization".equalsIgnoreCase(header)||"Cookie".equalsIgnoreCase(header))
                headers.put(header,value);
        }
        if(!headers.isEmpty())http.setDefaultRequestProperties(headers);
        boolean sensitive=headers.containsKey("Authorization")||headers.containsKey("Cookie");
        http.setAllowCrossProtocolRedirects(!sensitive);
        DefaultDataSource.Factory data=new DefaultDataSource.Factory(this,http);
        player=new ExoPlayer.Builder(this)
            .setRenderersFactory(new androidx.media3.exoplayer.DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setMediaSourceFactory(new DefaultMediaSourceFactory(data)).build();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
            .setPreferredTextLanguages("pt-BR","pt","por").build());

        playerView.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_READY&&!startedCurrent){
                    startedCurrent=true;
                    library.start(currentKey,currentUrl());
                }else if(state==Player.STATE_ENDED){
                    if(!isLive){markedFinished=true;save(true);}
                    else handlePlaybackError(new PlaybackException("Live stream ended",
                        null,PlaybackException.ERROR_CODE_IO_UNSPECIFIED));
                }
            }
            @Override public void onPlayerError(PlaybackException error){
                save(false);handlePlaybackError(error);
            }
            @Override public void onMediaItemTransition(MediaItem item,int reason){
                if(item==null||item.mediaId.equals(currentKey))return;
                if(startedCurrent){
                    if(reason==Player.MEDIA_ITEM_TRANSITION_REASON_AUTO){
                        UserLibrary.Entry previous=library.get(currentKey);
                        library.progress(currentKey,previous.durationMs,previous.durationMs,true);
                    }else save(false);
                }
                currentKey=item.mediaId;
                UserLibrary.Entry next=library.get(currentKey);
                long position=next.canContinue()?next.positionMs:0;
                startedCurrent=false;markedFinished=false;
                if(position>=5000)player.seekTo(position);
            }
        });
        ArrayList<MediaItem> items=new ArrayList<>();
        if(queue){
            for(int i=0;i<queuedUrls.length;i++){
                if(!isMediaUrl(queuedUrls[i])||queuedKeys[i]==null||queuedKeys[i].isEmpty())continue;
                UserLibrary.Entry record=library.get(queuedKeys[i]);
                items.add(createItem(queuedUrls[i],queuedKeys[i],record.title,null));
            }
        }
        if(items.isEmpty())items.add(createItem(resolvedUrl,currentKey,title,chosen.mime));
        player.setMediaItems(items,0,Math.max(0,position));
        String preference=getSharedPreferences("shock_tv_stream_prefs",MODE_PRIVATE).getString("quality","");
        if(!preference.isEmpty()&&chosen.qualityRank>0){
            int h=chosen.qualityRank;
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setMaxVideoSize((int)(16*h/9.0),h).build());
        }
        player.prepare();player.play();
        if(!queue)updateControls();
        if(initiated)Toast.makeText(this,"Fonte: "+chosen.shortLabel(),Toast.LENGTH_SHORT).show();
    }
    private static MediaItem createItem(String url,String key,String name,String mime){
        MediaItem.Builder builder=new MediaItem.Builder().setUri(url).setMediaId(key)
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(name).build());
        String ext=url.toLowerCase(Locale.ROOT).split("[?#]",2)[0];
        if((mime!=null&&mime.toLowerCase(Locale.ROOT).contains("mpegurl"))||ext.endsWith(".m3u8"))
            builder.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if("application/dash+xml".equals(mime)||ext.endsWith(".mpd"))
            builder.setMimeType(MimeTypes.APPLICATION_MPD);
        return builder.build();
    }
    private static boolean isMediaUrl(String url){
        if(SmartPlayback.isTorrent(url))return true;
        if(url==null)return false;
        try{
            java.net.URI uri=new java.net.URI(url);
            return uri.getHost()!=null&&
                ("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()));
        }catch(Exception e){return false;}
    }
    private String currentUrl(){
        if(player==null||player.getCurrentMediaItem()==null||
           player.getCurrentMediaItem().localConfiguration==null)return "";
        return player.getCurrentMediaItem().localConfiguration.uri.toString();
    }
    private void handlePlaybackError(PlaybackException error){
        if(showingError||closing)return;
        int httpStatus=-1;
        for(Throwable cause=error;cause!=null;cause=cause.getCause()){
            if(cause instanceof HttpDataSource.InvalidResponseCodeException){
                httpStatus=((HttpDataSource.InvalidResponseCodeException)cause).responseCode;break;
            }
        }
        if(!queue&&verified.size()>1){
            failedSources.add(verified.get(selectedIndex).url);
            int next=-1;
            for(int i=0;i<verified.size();i++){
                if(i!=selectedIndex&&!failedSources.contains(verified.get(i).url)){next=i;break;}
            }
            if(next>=0){
                int remaining=verified.size()-failedSources.size();
                Toast.makeText(this,"Fonte indisponível. Tentando outra ("+remaining+" restantes)…",
                    Toast.LENGTH_LONG).show();
                long resume=player==null?lastKnownPosition:Math.max(0,player.getCurrentPosition());
                final int chosen=next;
                handler.post(()->{
                    selectedIndex=chosen;
                    playOption(chosen,resume,false);
                });
                return;
            }
        }
        final int status=httpStatus;
        String explanation;
        switch(status){
            case 301:case 302:case 307:case 308:
                explanation="A fonte mudou de endereço e o redirecionamento não pôde ser concluído.";break;
            case 401: explanation="A fonte exige autenticação.";break;
            case 403: explanation="A fonte negou acesso. O endereço pode ter expirado.";break;
            case 404:case 410: explanation="A mídia não existe mais no servidor.";break;
            case 416: explanation="O servidor rejeitou a retomada do vídeo.";break;
            case 429: explanation="O servidor limitou as requisições. Tente mais tarde.";break;
            case 500:case 502:case 503:case 504: explanation="Servidor de mídia indisponível.";break;
            default: explanation=status>0?"O servidor recusou a requisição.":"Mídia incompatível ou erro de conexão.";
        }
        Log.w(TAG,"Falha no player: HTTP "+status+" / "+error.getErrorCodeName());
        showingError=true;
        new AlertDialog.Builder(this).setTitle("Não foi possível reproduzir")
          .setMessage(explanation+"\n\n"+
            (status>0?"HTTP "+status:error.getErrorCodeName())+
            "\n\nNenhum vídeo foi marcado como assistido se não chegou a carregar.")
          .setPositiveButton("Tentar novamente",(d,w)->{
              showingError=false;failedSources.clear();
              long pos=status==416?0:Math.max(0,lastKnownPosition);
              playOption(selectedIndex,pos,true);
          })
          .setNeutralButton("Player externo",(d,w)->{
              try{android.content.Intent intent=new android.content.Intent(android.content.Intent.ACTION_VIEW);
                  String mime=verified.get(selectedIndex).mime;
                  if(SmartPlayback.isTorrent(verified.get(selectedIndex).url)){showingError=false;Toast.makeText(this,"A fonte BitTorrent não disponibilizou dados. Tente outra fonte.",Toast.LENGTH_LONG).show();return;}
                  intent.setDataAndType(android.net.Uri.parse(verified.get(selectedIndex).url),mime.isEmpty()?"video/*":mime);
                  intent.putExtra("position",(int)Math.max(0,lastKnownPosition));
                  startActivity(intent);showingError=false;
              }catch(Exception e){showingError=false;Toast.makeText(this,"Nenhum player externo compatível instalado",Toast.LENGTH_LONG).show();}
          })
          .setNegativeButton("Voltar ao título",(d,w)->{closing=true;finish();})
          .setOnCancelListener(d->{closing=true;finish();})
          .show();
    }
    private void save(boolean completed){
        if(isLive)return; // Linear broadcasts have no VOD checkpoint.
        if(!startedCurrent||player==null||library==null||currentKey==null)return;
        long position=player.getCurrentPosition(),duration=player.getDuration();
        if(position<0||position==C.TIME_UNSET)position=0;
        if(duration<=0||duration==C.TIME_UNSET)duration=0;
        lastKnownPosition=position;
        library.progress(currentKey,position,duration,completed||markedFinished);
    }
    @Override protected void onPause(){
        save(false);
        if(player!=null)player.pause();
        if(torrentEngine!=null)torrentEngine.pause();
        super.onPause();
    }
    @Override protected void onResume(){super.onResume();if(torrentEngine!=null)torrentEngine.resume();}
    @Override protected void onStop(){save(false);super.onStop();}
    @Override protected void onDestroy(){
        closing=true;
        torrentGeneration++;if(torrentEngine!=null)torrentEngine.close();torrentWorker.shutdownNow();
        handler.removeCallbacks(periodic);
        handler.removeCallbacks(hideControls);
        save(false);
        if(player!=null){player.release();player=null;}
        super.onDestroy();
    }
}

