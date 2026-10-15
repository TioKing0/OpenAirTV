"""Shock TV Hub Studio - Windows installer for Samsung Orsay.
Standalone, no cloud, admin not normally required. Requires local port 80 and TV developer mode.
"""
import http.server
import io
import ipaddress
import os
import pathlib
import re
import socket
import sys
import threading
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import xml.etree.ElementTree as ET
import zipfile

TITLE = "Shock TV Hub Studio"
PORT = 80
PACKAGE_NAME = "LegacyTVHub"
VERSION = "0.1.0"


def bundled(name):
    root = getattr(sys, "_MEIPASS", os.path.dirname(__file__))
    return pathlib.Path(root) / name


def local_ipv4():
    # UDP connect selects the preferred route but transmits no data.
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("192.0.2.1", 1))
            ip = s.getsockname()[0]
            return ip if ipaddress.ip_address(ip).is_private else "127.0.0.1"
    except OSError:
        return "127.0.0.1"


def inspect_widget(data):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        if "config.xml" not in z.namelist():
            raise ValueError("O ZIP não possui config.xml na raiz: não é um widget Orsay válido.")
        if len(z.namelist()) > 5000 or any(n.startswith("/") or ".." in n.split("/") for n in z.namelist()):
            raise ValueError("Pacote inválido: caminhos não confiáveis.")
        config = ET.fromstring(z.read("config.xml"))
        name = config.findtext("widgetname") or "Widget"
        version = config.findtext("ver") or "desconhecida"
        # Orsay widget ID is a stable publisher-controlled identifier; ZIP filenames are not IDs.
        if not name.strip():
            raise ValueError("O widget não declara um nome.")
    return {"name": name.strip(), "version": version.strip(), "size": len(data)}


def widget_xml(ip, file_name, details):
    ET.register_namespace("", "")
    root = ET.Element("rsp", {"stat": "ok"})
    listing = ET.SubElement(root, "list")
    widget = ET.SubElement(listing, "widget", {"id": PACKAGE_NAME})
    ET.SubElement(widget, "title").text = details["name"]
    ET.SubElement(widget, "compression", {"type": "zip", "size": str(details["size"])})
    ET.SubElement(widget, "description").text = "Shock TV Hub Studio"
    ET.SubElement(widget, "download").text = f"http://{ip}/Widget/{file_name}"
    return b'<?xml version="1.0" encoding="UTF-8"?>\n' + ET.tostring(root, encoding="utf-8")


class InstallerServer(http.server.ThreadingHTTPServer):
    allow_reuse_address = True
    daemon_threads = True


class InstallerHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path == "/widgetlist.xml":
            self.send_file(self.server.widget_xml, "text/xml; charset=utf-8")
        elif path == "/Widget/" + self.server.widget_file:
            self.send_file(self.server.widget_bytes, "application/zip")
        elif path == "/health":
            self.send_file(b"Shock TV Hub Studio OK", "text/plain; charset=utf-8")
        else:
            self.send_error(404, "Not Found")

    def send_file(self, data, mime):
        self.send_response(200)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, fmt, *args):
        self.server.log(f"{self.client_address[0]}: " + fmt % args)


class Studio:
    def __init__(self):
        self.root = tk.Tk()
        self.root.title(TITLE)
        self.root.geometry("880x645")
        self.root.minsize(710, 560)
        self.root.configure(bg="#0b1522")
        self.http = None
        self.worker = None
        self.asset = None
        self.ip = tk.StringVar(value=local_ipv4())
        self.info = tk.StringVar(value="Carregando widget...")
        self.stage = tk.StringVar(value="Pronto para configurar o servidor")
        self.configure_theme()
        self.draw()
        self.load_packaged_widget()
        self.root.protocol("WM_DELETE_WINDOW", self.close)

    def configure_theme(self):
        style = ttk.Style(self.root)
        style.theme_use("clam")
        style.configure("TButton", font=("Segoe UI", 11), padding=(17, 12), background="#14738b", foreground="white")
        style.map("TButton", background=[("active", "#1b91a9")])
        style.configure("TEntry", padding=9)

    def label(self, parent, value, size=12, color="#d4e5f0", bold=False):
        t = tk.Label(parent, text=value, bg="#0b1522", fg=color, font=("Segoe UI", size, "bold" if bold else "normal"),
                     anchor="w", justify="left")
        t.pack(fill="x", pady=(0, 7))
        return t

    def draw(self):
        container = tk.Frame(self.root, bg="#0b1522", padx=37, pady=27)
        container.pack(fill="both", expand=True)
        self.label(container, "SHOCK   TV  HUB", 29, "#7ce3eb", True)
        self.label(container, "Studio para Windows  •  Samsung Smart Hub Orsay  •  Instalação local", 12, "#aecbdb")
        self.label(container, "1    Pacote Orsay", 15, "#ffffff", True)
        row = tk.Frame(container, bg="#0b1522")
        row.pack(fill="x", pady=(0, 15))
        ttk.Button(row, text="Escolher widget ZIP", command=self.select_widget).pack(side="left", padx=(0, 15))
        tk.Label(row, textvariable=self.info, bg="#0b1522", fg="#b7d1dc", font=("Segoe UI", 11)).pack(side="left")
        self.label(container, "2    Endereço do computador na rede", 15, "#ffffff", True)
        frame = tk.Frame(container, bg="#0b1522")
        frame.pack(fill="x", pady=(0, 15))
        ttk.Entry(frame, textvariable=self.ip, width=25, font=("Consolas", 14)).pack(side="left", padx=(0, 14))
        ttk.Button(frame, text="Iniciar servidor porta 80", command=self.toggle).pack(side="left")
        self.label(container, "Digite somente este IP nas configurações de desenvolvedor da TV. "
                             "Ambos os aparelhos devem estar no mesmo Wi-Fi.", 10, "#aac6d2")
        self.label(container, "3    Instalação na TV", 15, "#ffffff", True)
        self.label(container, "Smart Hub → login develop → IP Setting → Start User App Sync.\n"
                             "O menu depende do modelo e firmware. A sincronização é iniciada na televisão.", 11, "#cfdeeb")
        self.label(container, "Verificação local:", 11, "#7ce3eb", True)
        tk.Label(container, textvariable=self.stage, bg="#0b1522", fg="#ffffff",
                 wraplength=760, justify="left", anchor="w", font=("Consolas", 11)).pack(fill="x", pady=(0, 8))
        self.label(container, "REGISTRO DO SERVIDOR", 11, "#7ce3eb", True)
        self.logview = tk.Text(container, bg="#101f2c", fg="#c9e0ea", font=("Consolas", 10),
                               relief="flat", wrap="word", height=9, state="disabled")
        self.logview.pack(fill="both", expand=True)
        self.label(container, "Nenhum APK, widget ou instalação é declarado compatível até ser validado na TV real.",
                   10, "#829eac")

    def write_log(self, text):
        def do():
            self.logview.configure(state="normal")
            self.logview.insert("end", text + "\n")
            self.logview.see("end")
            self.logview.configure(state="disabled")
        self.root.after(0, do)

    def load_packaged_widget(self):
        pkg = bundled("LegacyTVHub_0.1.2.zip")
        try:
            self.asset = pkg.read_bytes()
            details = inspect_widget(self.asset)
            self.info.set(f"{details['name']}  •  v{details['version']}")
            self.write_log("Widget Orsay empacotado e verificado.")
        except Exception as exc:
            self.info.set("Nenhum pacote incluído; selecione um ZIP.")
            self.write_log("Pacote padrão indisponível: " + str(exc))

    def select_widget(self):
        filename = filedialog.askopenfilename(filetypes=[("Widget Orsay", "*.zip")])
        if not filename:
            return
        try:
            data = pathlib.Path(filename).read_bytes()
            if len(data) > 120_000_000:
                raise ValueError("Limite de 120 MB excedido.")
            info = inspect_widget(data)
            self.asset = data
            self.info.set(f"{info['name']}  •  v{info['version']}")
            self.write_log("Pacote selecionado: " + filename)
        except Exception as exc:
            messagebox.showerror("Pacote inválido", str(exc))

    def toggle(self):
        if self.http:
            self.http.shutdown()
            self.http.server_close()
            self.http = None
            self.stage.set("Servidor parado.")
            self.write_log("Servidor HTTP interrompido.")
            return
        if not self.asset:
            messagebox.showerror("Sem widget", "Escolha um widget Orsay válido.")
            return
        ip = self.ip.get().strip()
        try:
            parsed = ipaddress.IPv4Address(ip)
            if parsed.is_loopback or parsed.is_multicast or parsed.is_unspecified:
                raise ValueError("Escolha o IPv4 da rede local, não 127.0.0.1.")
            details = inspect_widget(self.asset)
            file_name = "LegacyTVHub_" + re.sub(r"[^A-Za-z0-9._-]", "_", details["version"]) + ".zip"
            handler = InstallerServer(("", PORT), InstallerHandler)
            handler.widget_bytes = self.asset
            handler.widget_file = file_name
            handler.widget_xml = widget_xml(ip, file_name, details)
            handler.log = self.write_log
            self.http = handler
            self.worker = threading.Thread(target=handler.serve_forever, daemon=True)
            self.worker.start()
            self.stage.set(f"Servidor ativo: http://{ip}/widgetlist.xml\n"
                           "Verifique o endereço em outro aparelho antes de sincronizar na TV.")
            self.write_log("Servidor ativo na porta 80: " + ip)
        except (OSError, ValueError) as exc:
            messagebox.showerror("Falha ao abrir porta 80",
                                 str(exc) + "\n\nFeche aplicativos que usam a porta 80. "
                                 "O Windows Firewall poderá pedir permissão para redes privadas.")
            self.write_log("Servidor não iniciado: " + str(exc))

    def close(self):
        if self.http:
            self.http.shutdown()
            self.http.server_close()
        self.root.destroy()

    def run(self):
        self.root.mainloop()


if __name__ == "__main__":
    Studio().run()
