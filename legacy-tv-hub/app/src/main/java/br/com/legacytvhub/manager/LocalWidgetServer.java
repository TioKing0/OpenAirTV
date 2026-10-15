package br.com.legacytvhub.manager;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Read-only LAN widget server, never reports installation success without the TV. */
final class LocalWidgetServer {
    interface StatusListener { void changed(String message); }
    private final Context context;
    private final StatusListener listener;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private ServerSocket socket;
    private Thread worker;
    private String hostIp;
    private String widgetAsset;

    LocalWidgetServer(Context ctx, StatusListener listener) {
        this.context = ctx.getApplicationContext();
        this.listener = listener;
    }

    synchronized void start() {
        if (active.get()) return;
        hostIp = localIp();
        if ("127.0.0.1".equals(hostIp)) {
            listener.changed("Não foi encontrado um IP Wi-Fi válido. Verifique a rede local.");
            return;
        }
        try {
            widgetAsset = null;
            for (String name : context.getAssets().list("")) {
                if (name.equals("LegacyTVHub_" + BuildConfig.VERSION_NAME + ".zip")) {
                    if (widgetAsset != null) throw new IOException("Widgets duplicados");
                    widgetAsset = name;
                }
            }
            if (widgetAsset == null) throw new IOException("Pacote Samsung não encontrado dentro do APK");
            socket = new ServerSocket(8080, 4, InetAddress.getByName("0.0.0.0"));
            active.set(true);
        } catch (Exception error) {
            listener.changed("Não foi possível iniciar o diagnóstico: " + error.getMessage()
                + "\nVerifique se a porta 8080 está ocupada e se a rede está disponível.");
            return;
        }
        worker = new Thread(() -> {
            while (active.get()) {
                try {
                    Socket client = socket.accept();
                    pool.execute(() -> serve(client));
                } catch (Exception error) {
                    if (active.get()) listener.changed("Servidor interrompido: " + error.getMessage());
                }
            }
        }, "LegacyTVHub-HTTP");
        worker.setDaemon(true);
        worker.start();
        listener.changed("Acesso por Wi-Fi ativado. Abra o navegador da TV e digite:\nhttp://" + hostIp + ":8080/"
            + "\nEste modo NÃO instala um aplicativo no Smart Hub.");
    }

    synchronized void stop() {
        active.set(false);
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
            socket = null;
        }
        listener.changed("Servidor parado.");
    }

    boolean isRunning() { return active.get(); }

    private void serve(Socket client) {
        try (Socket s = client) {
            s.setSoTimeout(5000);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            String request = line(in);
            if (request == null || request.length() > 1024) return;
            String[] parts = request.split(" ");
            for (int i=0; i<48; i++) {
                String header = line(in);
                if (header == null || header.isEmpty()) break;
            }
            if (parts.length != 3 || !parts[0].equals("GET")) {
                response(out, 405, "text/plain", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String path = parts[1].split("\\?", 2)[0];
            if (path.equals("/") || path.equals("/index.html")) {
                String html = new String(readAsset("tv-web/index.html"), StandardCharsets.UTF_8)
                    .replace("{{VERSION}}", BuildConfig.VERSION_NAME);
                response(out, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
            } else if (path.equals("/tv.css")) {
                response(out, 200, "text/css; charset=utf-8", readAsset("tv-web/style.css"));
            } else if (path.equals("/tv.js")) {
                response(out, 200, "application/javascript; charset=utf-8", readAsset("tv-web/main.js"));
            } else if (path.equals("/widgetlist.xml")) {
                String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<rsp stat=\"ok\"><list><widget id=\"LegacyTVHub\">"
                    + "<title>Legacy TV Hub</title><compression type=\"zip\" size=\"" + assetBytes().length + "\"/>"
                    + "<description>Versão " + BuildConfig.VERSION_NAME + "</description>"
                    + "<download>http://" + hostIp + "/Widget/" + widgetAsset + "</download>"
                    + "</widget></list></rsp>";
                response(out, 200, "text/xml; charset=utf-8", xml.getBytes(StandardCharsets.UTF_8));
            } else if (path.equals("/Widget/" + widgetAsset)) {
                response(out, 200, "application/zip", assetBytes());
            } else {
                response(out, 404, "text/plain", "Not Found".getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception error) {
            listener.changed("Erro na transmissão: " + error.getMessage());
        }
    }

    private byte[] readAsset(String name) throws IOException {
        try (InputStream in = context.getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int count;
            while ((count = in.read(buf)) != -1) out.write(buf, 0, count);
            return out.toByteArray();
        }
    }

    private byte[] assetBytes() throws IOException {
        try (InputStream in = context.getAssets().open(widgetAsset); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int count;
            while ((count = in.read(buf)) != -1) out.write(buf, 0, count);
            return out.toByteArray();
        }
    }

    private static String line(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int ch;
        while ((ch = in.read()) != -1 && ch != '\n') {
            if (buffer.size() >= 2048) throw new IOException("Cabeçalho HTTP extenso");
            if (ch != '\r') buffer.write(ch);
        }
        if (ch == -1 && buffer.size() == 0) return null;
        return buffer.toString("UTF-8");
    }

    private static void response(OutputStream out, int code, String mime, byte[] body) throws IOException {
        String reason = code == 200 ? "OK" : code == 404 ? "Not Found" : "Method Not Allowed";
        String h = "HTTP/1.1 " + code + " " + reason + "\r\n"
            + "Content-Type: " + mime + "\r\nContent-Length: " + body.length
            + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        out.write(h.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static String localIp() {
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                String name = nic.getName().toLowerCase(java.util.Locale.ROOT);
                if (!name.startsWith("wlan") && !name.startsWith("eth")) continue;
                for (InetAddress a : Collections.list(nic.getInetAddresses()))
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}
