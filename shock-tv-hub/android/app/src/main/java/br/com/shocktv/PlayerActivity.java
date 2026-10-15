package br.com.shocktv;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import java.util.ArrayList;

/** Media3 full player with local resume checkpoints, subtitles and an actual favorites queue. */
public final class PlayerActivity extends Activity {
    private static final long SAVE_INTERVAL_MS=10000L;
    private ExoPlayer player;
    private UserLibrary library;
    private String currentKey;
    private boolean markedFinished;
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
            Toast.makeText(this,"Mídia inválida.",Toast.LENGTH_LONG).show();finish();return;
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

        player=new ExoPlayer.Builder(this).build();
        view.setPlayer(player);
        ArrayList<MediaItem> items=new ArrayList<>();
        if(isQueue){
            for(int i=0;i<urls.length;i++){
                if(!isMediaUrl(urls[i])||keys[i]==null||keys[i].isEmpty())continue;
                UserLibrary.Entry record=library.get(keys[i]);
                items.add(new MediaItem.Builder().setUri(urls[i]).setMediaId(keys[i])
                    .setMediaMetadata(new MediaMetadata.Builder().setTitle(record.title).build())
                    .build());
            }
        }
        if(items.isEmpty()){
            items.add(new MediaItem.Builder().setUri(url).setMediaId(key)
                .setMediaMetadata(new MediaMetadata.Builder().setTitle(name).build()).build());
        }
        currentKey=items.get(0).mediaId;
        library.start(currentKey,items.get(0).localConfiguration.uri.toString());
        markedFinished=false;
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException error){
                save(false);
                Toast.makeText(PlayerActivity.this,"Stream indisponível: "+error.getErrorCodeName(),Toast.LENGTH_LONG).show();
            }
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_ENDED){
                    markedFinished=true;
                    save(true);
                }
            }
            @Override public void onMediaItemTransition(MediaItem mediaItem,int reason){
                if(mediaItem==null)return;
                String next=mediaItem.mediaId;
                if(!next.equals(currentKey)){
                    if(reason==Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && currentKey!=null){
                        UserLibrary.Entry previous=library.get(currentKey);
                        library.progress(currentKey,previous.durationMs,previous.durationMs,true);
                    }else save(false);
                    currentKey=next;
                    markedFinished=false;
                    String stream=mediaItem.localConfiguration!=null?
                        mediaItem.localConfiguration.uri.toString():"";
                    library.start(currentKey,stream);
                }
            }
        });
        player.setMediaItems(items,0,restore);
        player.prepare();player.play();
        handler.postDelayed(periodic,SAVE_INTERVAL_MS);
        view.requestFocus();
    }

    private static boolean isMediaUrl(String url){
        return url.startsWith("https://")||url.startsWith("http://");
    }
    private void save(boolean ended){
        if(player==null||library==null||currentKey==null)return;
        long position=player.getCurrentPosition();
        long duration=player.getDuration();
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
