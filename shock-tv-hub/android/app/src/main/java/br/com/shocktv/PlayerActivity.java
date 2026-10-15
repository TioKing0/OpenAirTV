package br.com.shocktv;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public final class PlayerActivity extends Activity {
    private ExoPlayer player;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        String url=getIntent().getStringExtra("url");
        if(url==null||!(url.startsWith("https://")||url.startsWith("http://"))){finish();return;}
        PlayerView view=new PlayerView(this);
        view.setUseController(true);
        view.setControllerShowTimeoutMs(8000);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        setContentView(view,new FrameLayout.LayoutParams(-1,-1));
        player=new ExoPlayer.Builder(this).build();
        view.setPlayer(player);
        player.addListener(new Player.Listener(){
            @Override public void onPlayerError(PlaybackException error){
                Toast.makeText(PlayerActivity.this,"Mídia indisponível: "+error.getErrorCodeName(),Toast.LENGTH_LONG).show();
            }
        });
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();player.play();
        view.requestFocus();
    }
    @Override protected void onPause(){if(player!=null)player.pause();super.onPause();}
    @Override protected void onDestroy(){if(player!=null){player.release();player=null;}super.onDestroy();}
}
