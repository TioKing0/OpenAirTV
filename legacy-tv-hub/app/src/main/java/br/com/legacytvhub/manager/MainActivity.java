package br.com.legacytvhub.manager;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** One permanent Android app; no separate diagnostic or installation APK. */
public final class MainActivity extends Activity {
    private static final int EXPORT_USB = 471;
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
        layout.setPadding(dp(24), dp(28), dp(24), dp(30));
        scroll.addView(layout);
        TextView title = text("Legacy TV Hub", 30, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        layout.addView(title);
        layout.addView(text("ANDROID • v" + BuildConfig.VERSION_NAME, 12, 0xff89b2d8));
        layout.addView(text("Samsung J4300 • Smart Hub Orsay", 18, Color.WHITE));
        layout.addView(text("Instalador e diagnóstico dentro do mesmo aplicativo.", 15, 0xffc1ccd8));

        layout.addView(text("1 • INSTALAÇÃO POR USB (RECOMENDADA PARA TESTE)", 14, 0xff86c5f3));
        Button usb = new Button(this);
        usb.setText("EXPORTAR PACOTE ORSAY PARA USB");
        layout.addView(usb);
        layout.addView(text("Salve o ZIP em seu celular ou pendrive. Extraia a pasta LegacyTVHub na raiz de um pendrive FAT32. "
            + "Confira que o caminho seja /LegacyTVHub/config.xml, e não /userwidget. "
            + "Conecte o pendrive à TV e procure o aplicativo no Smart Hub. "
            + "Esse método é comunitário e precisa ser validado na J4300.", 14, 0xffc1ccd8));
        usb.setOnClickListener(v -> {
            Intent choose = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            choose.addCategory(Intent.CATEGORY_OPENABLE);
            choose.setType("application/zip");
            choose.putExtra(Intent.EXTRA_TITLE, "LegacyTVHub_Orsay_USB_v" + BuildConfig.VERSION_NAME + ".zip");
            try {
                startActivityForResult(choose, EXPORT_USB);
            } catch (Exception error) {
                status.setText("O seletor de arquivos não está disponível: " + error.getMessage());
            }
        });

        layout.addView(text("2 • DIAGNÓSTICO DE REDE SEM ROOT", 14, 0xff86c5f3));
        action = new Button(this);
        action.setText("INICIAR SERVIDOR DE DIAGNÓSTICO");
        layout.addView(action);
        status = text("O servidor usa a porta 8080 e não instala automaticamente pela sincronização Orsay. "
            + "Para sincronizar pela rede a TV geralmente precisa de um servidor na porta 80.", 14, 0xffc1ccd8);
        layout.addView(status);

        server = new LocalWidgetServer(this, msg -> runOnUiThread(() -> status.setText(msg)));
        action.setOnClickListener(v -> {
            if (server.isRunning()) server.stop(); else server.start();
            action.setText(server.isRunning() ? "PARAR SERVIDOR" : "INICIAR SERVIDOR DE DIAGNÓSTICO");
        });
        layout.addView(text("Este aplicativo não faz root, não altera o firmware da TV e não confirma instalação sem "
            + "verificação real. O mesmo ID do aplicativo será mantido nas próximas versões.", 13, 0xffa8b5c5));
        setContentView(scroll);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT_USB) return;
        if (result != RESULT_OK || data == null || data.getData() == null) {
            status.setText("Exportação cancelada. Nada foi instalado na TV.");
            return;
        }
        Uri dest = data.getData();
        String assetName = "LegacyTVHub_USB_" + BuildConfig.VERSION_NAME + ".zip";
        try (InputStream in = getAssets().open(assetName);
             OutputStream out = getContentResolver().openOutputStream(dest, "w")) {
            if (out == null) throw new IOException("Destino não permite escrita");
            byte[] bytes = new byte[8192];
            int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
            out.flush();
            status.setText("Pacote USB exportado com sucesso. Extraia a pasta LegacyTVHub para a raiz de um pendrive FAT32. "
                + "Agora teste na TV. A instalação ainda não foi confirmada.");
        } catch (Exception error) {
            status.setText("Falha ao exportar o pacote: " + error.getMessage());
        }
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setPadding(0, 0, 0, dp(15));
        return view;
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }
}
