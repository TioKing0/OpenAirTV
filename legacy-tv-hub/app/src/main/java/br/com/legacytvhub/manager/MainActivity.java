package br.com.legacytvhub.manager;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** One permanent Android app. Wi-Fi browser mode is distinct from native Orsay installation. */
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
        layout.setPadding(dp(24), dp(35), dp(24), dp(26));
        scroll.addView(layout);

        TextView title = text("Legacy TV Hub", 30, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        layout.addView(title);
        layout.addView(text("ANDROID • v" + BuildConfig.VERSION_NAME, 12, 0xff89b2d8));
        layout.addView(text("Samsung J4300 • Wi-Fi sem pendrive", 18, Color.WHITE));
        layout.addView(text("MODO 1 — ACESSO PELO NAVEGADOR DA TV", 14, 0xff86c5f3));
        layout.addView(text("O celular hospeda a interface na rede local. Não precisa de PC, USB ou root. Abra o navegador da TV e digite o endereço exibido abaixo.", 15, 0xffc1ccd8));

        action = new Button(this);
        action.setText("INICIAR ACESSO POR WI-FI");
        layout.addView(action);
        status = text("Conecte a TV e o celular à mesma rede Wi-Fi. Inicie o servidor para obter o endereço.", 16, 0xffc1ccd8);
        status.setTextIsSelectable(true);
        layout.addView(status);

        layout.addView(text("MODO 2 — INSTALAÇÃO NATIVA NO SMART HUB", 14, 0xfff6bf71));
        layout.addView(text("Ainda não disponível somente com este celular. A sincronização Samsung Orsay normalmente exige HTTP na porta 80, bloqueada neste Android. "
            + "A porta 8080 serve para o navegador e diagnóstico, não instala um aplicativo nativo. "
            + "Para instalar nativamente por Wi-Fi sem root, será necessário validar um servidor intermediário compatível.", 14, 0xffc1ccd8));
        layout.addView(text("O teste de abertura no navegador não comprova a instalação do widget no Smart Hub.", 13, 0xffa8b5c5));

        server = new LocalWidgetServer(this, msg -> runOnUiThread(() -> status.setText(msg)));
        action.setOnClickListener(v -> {
            if (server.isRunning()) server.stop(); else server.start();
            if (server.isRunning()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            action.setText(server.isRunning() ? "PARAR ACESSO POR WI-FI" : "INICIAR ACESSO POR WI-FI");
        });
        setContentView(scroll);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, 0, 0, dp(17));
        return view;
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    @Override protected void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }
}
