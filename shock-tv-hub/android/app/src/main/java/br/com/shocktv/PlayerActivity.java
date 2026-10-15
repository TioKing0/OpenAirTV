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
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

/** Real Media3 playback: position is persisted every 10 seconds and at lifecycle boundaries. */
public final class PlayerActivity extends Activity {
    private static final long SAVE_INTERVAL_MS=10000;
    private ExoPlayer player;
    private UserLibrary library;
    private String libraryKey;
    private boolean markedFinished;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable periodic=new Runnable(){
        @Override public void run(){
            save(false);
            if(player!=null)handler.postDelayed(this,SAVE_INTERVAL_MS);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        String url=getIntent().getStringExtra("url");
        String name=getIntent().getStringExtra("title");
        String id=getIntent().getStringExtra("id");
        String type=getIntent().getStringExtra("type");
        String poster=getIntent().getStringExtra("poster");
        String description=getIntent().getStringExtra("description");
        String source=getIntent().getStringExtra("source");
        String episodeId=getIntent().getStringExtra("episodeId");
        libraryKey=getIntent().getStringExtra("libraryKey");
        boolean restart=getIntent().getBooleanExtra("restart",false);
        if(url==null||!(url.startsWith("https://")||url.startsWith("http://"))||libraryKey==null){
            Toast.makeText(this,"Mídia inválida ou sem identificador.",Toast.LENGTH_LONG).show();
            finish();return;
        }
        library=new UserLibrary(this);
        library.identify(libraryKey,id,type,name,poster,description,source,episodeId);
        UserLibrary.Entry saved=library.get(libraryKey);
        long restore=(saved.canContinue() && !restart)?saved.positionMs:0L;
        library.start(libraryKey,url);
        markedFinished=false;

        PlayerView view=new PlayerView(this);
        view.setUseController(true);
        view.setControllerShowTimeoutMs(8500);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        setContentView(view,new FrameLayout.LayoutParams(-1,-1));

        player=new ExoPlayer.Builder(this).build();
        view.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException error){
                save(false);
                Toast.makeText(PlayerActivity.this,
                  "Não foi possível reproduzir este stream: "+error.getErrorCodeName(),Toast.LENGTH_LONG).show();
            }
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_ENDED){
                    markedFinished=true;
                    save(true);
                }
            }
        });
        player.setMediaItem(MediaItem.fromUri(url));
        // Seek before prepare avoids racing the first audio/video frame.
        if(restore>=5000L)player.seekTo(restore);
        player.prepare();
        player.play();
        handler.postDelayed(periodic,SAVE_INTERVAL_MS);
        view.requestFocus();
    }

    private void save(boolean ended){
        if(player==null||library==null||libraryKey==null)return;
        long position=player.getCurrentPosition();
        long duration=player.getDuration();
        if(position<0||position==C.TIME_UNSET)position=0;
        if(duration<=0||duration==C.TIME_UNSET)duration=0;
        library.progress(libraryKey,position,duration,ended||markedFinished);
    }

    @Override protected void onPause(){
        save(false);
        if(player!=null)player.pause();
        super.onPause();
    }
    @Override protected void onStop(){
        save(false);
        super.onStop();
    }
    @Override protected void onDestroy(){
        handler.removeCallbacks(periodic);
        save(false);
        if(player!=null){player.release();player=null;}
        super.onDestroy();
    }
}
