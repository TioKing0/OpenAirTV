package br.com.shocktv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Playback with actionable HTTP status errors and Stremio-declared media request headers. */
@UnstableApi
public final class PlayerActivity extends Activity {
    private static final long SAVE_INTERVAL_MS=10000L;
    private ExoPlayer player;
    private UserLibrary library;
    private String currentKey;
    private boolean startedCurrent;
    private boolean markedFinished;
    private boolean showingError;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable periodic=new Runnable(){
        @Override public void run(){
            save(false);
            if(player!=null)handler.postDelayed(this,SAVE_INTERVAL_MS);
        }
    };

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        String url=getIntent().getStringExtra("url");
        String name=getIntent().getStringExtra("title");
        String key=getIntent().getStringExtra("libraryKey");
        if(url==null||!isMediaUrl(url)||key==null||key.isEmpty()){
            Toast.makeText(this,"O endereço de mídia não é válido.",Toast.LENGTH_LONG).show();
            finish();return;
        }
        library=new UserLibrary(this);
        library.identify(key,getIntent().getStringExtra("id"),getIntent().getStringExtra("type"),
            name,getIntent().getStringExtra("poster"),getIntent().getStringExtra("description"),
            getIntent().getStringExtra("source"),getIntent().getStringExtra("episodeId"));
        boolean restart=getIntent().getBooleanExtra("restart",false);
        UserLibrary.Entry initial=library.get(key);
        long restore=initial.canContinue()&&!restart?initial.positionMs:0L;

        String[] urls=getIntent().getStringArrayExtra("playlistUrls");
        String[] keys=getIntent().getStringArrayExtra("playlistKeys");
        boolean isQueue=urls!=null&&keys!=null&&urls.length>0&&urls.length<=200&&urls.length==keys.length;

        PlayerView view=new PlayerView(this);
        view.setUseController(true);
        view.setControllerShowTimeoutMs(8500);
        view.setFocusable(true);view.setFocusableInTouchMode(true);
        view.setKeepScreenOn(true);
        view.setShowSubtitleButton(true);
        setContentView(view,new FrameLayout.LayoutParams(-1,-1));

        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
            .setUserAgent("ShockTV/0.3.3 (Android TV)");
        Map<String,String> headers=new HashMap<>();
        String[] headerNames=getIntent().getStringArrayExtra("streamHeaderNames");
        String[] headerValues=getIntent().getStringArrayExtra("streamHeaderValues");
        if(!isQueue&&headerNames!=null&&headerValues!=null&&headerNames.length==headerValues.length){
            for(int i=0;i<headerNames.length&&i<12;i++){
                String header=headerNames[i],value=headerValues[i];
                if(header==null||value==null||value.length()>2048||value.indexOf('\r')>=0||value.indexOf('\n')>=0)continue;
                // Sensitive headers may only be transmitted over TLS.
                if(!url.startsWith("https://")&&("Authorization".equalsIgnoreCase(header)||"Cookie".equalsIgnoreCase(header)))continue;
                if("User-Agent".equalsIgnoreCase(header)||"Referer".equalsIgnoreCase(header)
                    ||"Origin".equalsIgnoreCase(header)||"Accept".equalsIgnoreCase(header)
                    ||"Authorization".equalsIgnoreCase(header)||"Cookie".equalsIgnoreCase(header))
                    headers.put(header,value);
            }
        }
        if(!headers.isEmpty())http.setDefaultRequestProperties(headers);
        DefaultDataSource.Factory dataSource=new DefaultDataSource.Factory(this,http);
        player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource)).build();
        view.setPlayer(player);

        ArrayList<MediaItem> items=new ArrayList<>();
        if(isQueue){
            for(int i=0;i<urls.length;i++){
                if(!isMediaUrl(urls[i])||keys[i]==null||keys[i].isEmpty())continue;
                UserLibrary.Entry record=library.get(keys[i]);
                items.add(createItem(urls[i],keys[i],record.title));
            }
        }
        if(items.isEmpty())items.add(createItem(url,key,name));
        currentKey=items.get(0).mediaId;
        startedCurrent=false;
        markedFinished=false;

        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException error){
                save(false);
                presentError(error);
            }
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_READY){
                    if(!startedCurrent){
                        startedCurrent=true;
                        String mediaUrl=currentUrl();
                        library.start(currentKey,mediaUrl);
                    }
                }else if(state==Player.STATE_ENDED){
                    markedFinished=true;
                    save(true);
                }
            }
            @Override public void onMediaItemTransition(MediaItem mediaItem,int reason){
                if(mediaItem==null)return;
                String next=mediaItem.mediaId;
                if(!next.equals(currentKey)){
                    if(reason==Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && currentKey!=null&&startedCurrent){
                        UserLibrary.Entry previous=library.get(currentKey);
                        library.progress(currentKey,previous.durationMs,previous.durationMs,true);
                    }else save(false);
                    UserLibrary.Entry nextProgress=library.get(next);
                    long nextPosition=nextProgress.canContinue()?nextProgress.positionMs:0L;
                    currentKey=next;
                    startedCurrent=false;
                    markedFinished=false;
                    if(nextPosition>=5000L)player.seekTo(nextPosition);
                }
            }
        });
        player.setMediaItems(items,0,restore);
        player.prepare();player.play();
        handler.postDelayed(periodic,SAVE_INTERVAL_MS);
        view.requestFocus();
    }

    private static MediaItem createItem(String url,String key,String title){
        MediaItem.Builder b=new MediaItem.Builder().setUri(url).setMediaId(key)
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build());
        String suffix=url.toLowerCase(Locale.ROOT).split("[?#]",2)[0];
        if(suffix.endsWith(".m3u8"))b.setMimeType(MimeTypes.APPLICATION_M3U8);
        else if(suffix.endsWith(".mpd"))b.setMimeType(MimeTypes.APPLICATION_MPD);
        return b.build();
    }
    private static boolean isMediaUrl(String url){
        try{
            java.net.URI uri=new java.net.URI(url);
            return ("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost()!=null;
        }catch(Exception ex){return false;}
    }
    private String currentUrl(){
        if(player==null||player.getCurrentMediaItem()==null||
            player.getCurrentMediaItem().localConfiguration==null)return "";
        return player.getCurrentMediaItem().localConfiguration.uri.toString();
    }
    private void presentError(PlaybackException error){
        if(showingError||isFinishing()||isDestroyed())return;
        int httpStatus=-1;
        Throwable cause=error;
        for(int i=0;cause!=null&&i<12;i++,cause=cause.getCause()){
            if(cause instanceof HttpDataSource.InvalidResponseCodeException){
                httpStatus=((HttpDataSource.InvalidResponseCodeException)cause).responseCode;
                break;
            }
        }
        final int status=httpStatus;
        String explanation;
        switch(status){
            case 401: explanation="O servidor exige autenticação.";break;
            case 403: explanation="O servidor negou acesso. A URL pode estar expirada, restrita ou exigir autenticação.";break;
            case 404:case 410: explanation="O arquivo não existe mais neste endereço.";break;
            case 416: explanation="O servidor rejeitou a posição de retomada. Tente reproduzir do início.";break;
            case 429: explanation="O servidor limitou o número de requisições. Tente novamente mais tarde.";break;
            case 500:case 502:case 503:case 504:
                explanation="O servidor de mídia está temporariamente indisponível.";break;
            default:
                if(status>0)explanation="O servidor recusou a reprodução.";
                else if(error.errorCode==PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                    ||error.errorCode==PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED)
                    explanation="A URL não retornou uma mídia válida ou o formato está incorreto.";
                else explanation="Não foi possível abrir ou decodificar esta mídia.";
        }
        String diagnostic=status>0?"Resposta HTTP "+status:"Código "+error.getErrorCodeName();
        Log.w("ShockTV-Player","Falha de reprodução: "+diagnostic+"; "+error.getErrorCodeName());
        showingError=true;
        new AlertDialog.Builder(this).setTitle("Não foi possível reproduzir")
            .setMessage(explanation+"\n\n"+diagnostic+
                "\n\nEscolha outra fonte do addon ou tente novamente. O Shock TV não marcará esta tentativa como vídeo assistido.")
            .setPositiveButton("Tentar novamente",(dialog,which)->{
                showingError=false;startedCurrent=false;markedFinished=false;
                if(player!=null){
                    if(status==416)player.seekTo(0L);
                    player.prepare();player.play();
                }
            })
            .setNegativeButton("Escolher outra fonte",(dialog,which)->{
                showingError=false;finish();
            })
            .setOnCancelListener(d->{showingError=false;finish();})
            .show();
    }
    private void save(boolean ended){
        if(!startedCurrent||player==null||library==null||currentKey==null)return;
        long position=player.getCurrentPosition(),duration=player.getDuration();
        if(position<0||position==C.TIME_UNSET)position=0;
        if(duration<=0||duration==C.TIME_UNSET)duration=0;
        library.progress(currentKey,position,duration,ended||markedFinished);
    }
    @Override protected void onPause(){
        save(false);
        if(player!=null)player.pause();
        super.onPause();
    }
    @Override protected void onStop(){save(false);super.onStop();}
    @Override protected void onDestroy(){
        handler.removeCallbacks(periodic);
        save(false);
        if(player!=null){player.release();player=null;}
        super.onDestroy();
    }
}
