package br.com.legacytvhub.manager;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The permanent Android app also contains diagnostics, no separate test APK. */
public final class MainActivity extends Activity {
    private LocalWidgetServer server;
    private TextView status;
    private Button action;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(13, 18, 27));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(40), dp(24), dp(24));
        scroll.addView(layout);
        TextView title = text("Legacy TV Hub", 30, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        layout.addView(title);
        layout.addView(text("ANDROID • v" + BuildConfig.VERSION_NAME, 12, 0xff89b2d8));
        layout.addView(text("Samsung J4300 • Smart Hub Orsay", 18, Color.WHITE));
        layout.addView(text("Aplicativo permanente. Diagnósticos e melhorias chegam por atualizações.", 15, 0xffc1ccd8));
        action = new Button(this);
        action.setText("INICIAR SERVIDOR LOCAL");
        layout.addView(action);
        status = text("Conecte a TV e o celular à mesma rede Wi-Fi e autorize a sincronização no Smart Hub.", 15, 0xffc1ccd8);
        layout.addView(status);
        layout.addView(text("LIMITAÇÕES", 14, 0xfff6bf71));
        layout.addView(text("• A TV normalmente exige HTTP na porta 80, possivelmente bloqueada sem root.\n"
            + "• Nem todo firmware oferece o modo de desenvolvedor.\n"
            + "• Instalação e atualização precisam de validação física na J4300.\n"
            + "• O APK Android só será atualizável mantendo a mesma assinatura e ID.", 14, 0xffc1ccd8));
        server = new LocalWidgetServer(this, msg -> runOnUiThread(() -> status.setText(msg)));
        action.setOnClickListener(v -> {
            if (server.isRunning()) server.stop(); else server.start();
            action.setText(server.isRunning() ? "PARAR SERVIDOR" : "INICIAR SERVIDOR LOCAL");
        });
        setContentView(scroll);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, 0, 0, dp(18));
        return view;
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }
}
