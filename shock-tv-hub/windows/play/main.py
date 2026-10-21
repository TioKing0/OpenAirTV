"""Telares Play: Windows desktop streaming client, mouse/keyboard, Tk/VideoLAN."""
import os,sys
if __name__=="__main__" and len(sys.argv)>2 and sys.argv[1]=="--self-test":
    import traceback,tempfile
    test_log=os.environ.get('TELARES_TEST_LOG',os.path.join(tempfile.gettempdir(),'telares-playback-test.log'))
    with open(test_log,'w',encoding='utf-8',buffering=1) as output:
        sys.stdout=output;sys.stderr=output
        try:
            from self_test import run
            result=run(sys.argv[2])
        except BaseException:
            traceback.print_exc();result=1
    sys.stdout=sys.__stdout__;sys.stderr=sys.__stderr__
    sys.exit(result)
import concurrent.futures
import io
import json
import os
import pathlib
import queue
import sys
import time
import tkinter as tk
from tkinter import messagebox, simpledialog
import urllib.request
from collections import OrderedDict
from PIL import Image, ImageTk
import catalog
from player import VideoEngine

BG="#091420";PANEL="#14283A";LIFT="#1B3447";CYAN="#68D8E9";WHITE="#F3FAFF";MUTED="#9BB8C7"
APPDATA=pathlib.Path(os.environ.get("APPDATA") or pathlib.Path.home())/"TelaresPlay"
STATE_FILE=APPDATA/"state.json"

def load_state():
    try:
        data=json.loads(STATE_FILE.read_text(encoding="utf-8"))
        if isinstance(data,dict):return data
    except Exception:pass
    return {"addons":list(catalog.DEFAULT),"entries":{}}

def persist(state):
    APPDATA.mkdir(parents=True,exist_ok=True)
    temp=STATE_FILE.with_suffix(".tmp")
    temp.write_text(json.dumps(state,ensure_ascii=False,indent=2),encoding="utf-8")
    os.replace(temp,STATE_FILE)

def safe_item(item):
    return {k:v for k,v in item.items() if k not in ("_url","_label","_torrent","_file_index","_headers") and
            isinstance(v,(str,int,float,bool,list,dict))}

class TelaresPlay:
    def __init__(self):
        self.root=tk.Tk()
        self.root.title("Telares Play")
        self.root.geometry("1160x750")
        self.root.minsize(920,620)
        self.root.configure(bg=BG)
        try:self.root.iconbitmap(default=str(pathlib.Path(getattr(sys,"_MEIPASS",pathlib.Path(__file__).parent))/"TelaresPlay.ico"))
        except Exception:pass
        self.data=load_state();self.data.setdefault("entries",{})
        self.removed_defaults=set(self.data.get("removed_default_addons",[]))
        self.addons=list(dict.fromkeys((self.data.get("addons") or [])+
            [url for url in catalog.DEFAULT if url not in self.removed_defaults]))
        # Preserve user-customized addons and explicit opt-outs while migrating new defaults.
        self.addons=[url for url in self.addons if not catalog.removed_addon(url)]
        self.data.pop("torrent_server",None)
        self.data["addons"]=self.addons
        persist(self.data)
        self.executor=concurrent.futures.ThreadPoolExecutor(max_workers=7)
        self.messages=queue.Queue()
        self.results=[]
        self.section="Início"
        self.search_text=tk.StringVar()
        self.search_job=None
        self.req=0
        self.detail=None
        self.images=OrderedDict()
        self.image_pending=set()
        self.references=[]
        self.video=None
        self.subtitle_delay=0
        self.source_list=[]
        self.source_index=-1
        self.current_item=None
        self.last_save=0
        self.started=False
        self.resume_sec=0
        self.play_started_at=0
        self.playing=False
        self.live_group="Todos os canais"
        self.live_selected=None
        self.live_preview_frame=None
        self.build_ui()
        self.root.after(90,self.poll)
        self.root.after(1500,self.tick)
        self.reload()
        self.root.protocol("WM_DELETE_WINDOW",self.close)

    def job(self,fn,ok,err=None):
        future=self.executor.submit(fn)
        def completed(f):
            try:self.messages.put((ok,f.result()))
            except Exception as exc:
                if err:self.messages.put((err,str(exc)))
        future.add_done_callback(completed)

    def poll(self):
        for _ in range(32):
            try:fn,value=self.messages.get_nowait()
            except queue.Empty:break
            try:fn(value)
            except Exception as err:print("Telares UI:",type(err).__name__,str(err))
        self.root.after(90,self.poll)

    def label(self,parent,value,size=13,color=WHITE,bold=False,**opts):
        return tk.Label(parent,text=value,font=("Segoe UI",size,"bold" if bold else "normal"),
                 bg=opts.pop("bg",BG),fg=color,anchor=opts.pop("anchor","w"),
                 justify="left",**opts)

    def btn(self,parent,text,action,bg=PANEL,color=WHITE,padx=15,pady=9,**opts):
        return tk.Button(parent,text=text,command=action,bd=0,relief="flat",cursor="hand2",
          bg=bg,fg=color,activebackground=LIFT,activeforeground=CYAN,
          font=("Segoe UI",11,"bold"),padx=padx,pady=pady,**opts)

    def build_ui(self):
        self.nav=tk.Frame(self.root,bg=PANEL,width=214)
        self.nav.pack(side="left",fill="y")
        self.nav.pack_propagate(False)
        self.label(self.nav,"TELARES",24,CYAN,True,bg=PANEL).pack(anchor="w",padx=25,pady=(28,0))
        self.label(self.nav,"P L A Y    •    WINDOWS",10,MUTED,bg=PANEL).pack(anchor="w",padx=26,pady=(0,26))
        self.nav_buttons={}
        for label in ("Início","Filmes","Séries","Canais ao vivo","Buscar","Categorias","Continuar assistindo","Minha lista","Fontes"):
            btn=self.btn(self.nav,label,lambda v=label:self.open_section(v),PANEL,padx=16)
            btn.pack(fill="x",padx=12,pady=4)
            self.nav_buttons[label]=btn
        self.label(self.nav,"v0.2.12 RC15 • Telares\nStreaming via VLC",10,MUTED,bg=PANEL).pack(side="bottom",anchor="w",padx=20,pady=25)
        self.right=tk.Frame(self.root,bg=BG)
        self.right.pack(side="left",fill="both",expand=True)
        bar=tk.Frame(self.right,bg=BG,height=78)
        bar.pack(fill="x")
        self.heading=self.label(bar,"Início",23,WHITE,True)
        self.heading.pack(side="left",padx=28,pady=19)
        self.search_entry=tk.Entry(bar,textvariable=self.search_text,font=("Segoe UI",13),bg=LIFT,
           fg=WHITE,insertbackground=CYAN,relief="flat",width=26)
        self.search_entry.pack(side="right",padx=(0,25),ipady=9)
        self.search_entry.bind("<Return>",lambda e:self.find())
        self.search_entry.bind("<KeyRelease>",self.debounced)
        self.label(bar,"Buscar:",11,MUTED).pack(side="right",padx=10)
        self.stage=tk.Frame(self.right,bg=BG)
        self.stage.pack(fill="both",expand=True)
        self.status=self.label(self.right,"Carregando catálogos reais dos addons...",10,MUTED)
        self.status.pack(fill="x",padx=28,pady=7)
        self.root.bind("<Control-f>",lambda e:self.focus_search())
        self.root.bind("<Escape>",lambda e:self.escape())
        self.root.bind("<space>",lambda e:self.toggle_play())
        self.root.bind("<Left>",lambda e:self.jump(-10))
        self.root.bind("<Right>",lambda e:self.jump(10))
        self.root.bind("<F11>",lambda e:self.fullscreen())
        self.full=False
        self.open_section("Início")

    def status_set(self,text):self.status.config(text=text)

    def open_section(self,label):
        if label=="Fontes":self.sources_dialog();return
        if label=="Categorias":self.show_categories();return
        if label=="Buscar":self.focus_search();return
        if self.playing:self.exit_player()
        self.section=label;self.detail=None
        self.heading.config(text=label)
        for name,button in self.nav_buttons.items():
            button.config(bg=LIFT if name==label else PANEL,
                          fg=CYAN if name==label else WHITE)
        if label=="Minha lista" or label=="Continuar assistindo":
            saved=self.data["entries"].values()
            if label=="Minha lista":self.results=[x for x in saved if x.get("liked") or x.get("later")]
            else:self.results=[x for x in saved if x.get("position",0)>5]
            self.results.sort(key=lambda x:x.get("updated",0),reverse=True)
            self.render_grid()
        else:self.reload()

    def show_categories(self):
        if self.playing:self.exit_player()
        self.heading.config(text="Explorar categorias")
        panel=self.scroll_frame()
        self.label(panel,"Escolha um catálogo ou gênero",20,WHITE,True).pack(anchor="w",padx=28,pady=(22,13))
        for name in ("Canais ao vivo",)+tuple(catalog.CATEGORY_OPTIONS):
            self.btn(panel,name,lambda tag=name:self.select_category(tag),PANEL,padx=20,pady=13).pack(anchor="w",fill="x",padx=28,pady=5)
        self.label(panel,"Esses filtros complementam Filmes e Séries. Plataformas: catálogos dos addons, sem confirmar disponibilidade no Brasil.",
            11,MUTED,wraplength=720).pack(anchor="w",padx=28,pady=18)

    def select_category(self,tag):
        if tag=="Canais ao vivo":
            self.open_section(tag)
            return
        self.section="Categorias"
        self.heading.config(text=tag)
        self.req+=1
        seq=self.req
        self.results=[]
        self.render_grid()
        self.status_set("Consultando catálogos de "+tag+"...")
        def done(items):
            if seq!=self.req or self.playing:return
            self.results=items
            self.render_grid()
            self.status_set(str(len(items))+" títulos • "+tag+" • classificação baseada nos addons")
            if not items:
                self.status_set("Nenhum catálogo conectado declarou títulos para "+tag+". Filmes e Séries continuam disponíveis; adicione um addon compatível em Fontes.")
        self.job(lambda:catalog.discover(self.addons,category=tag),done,
                 lambda reason:self.status_set("Erro ao consultar "+tag+": "+reason))

    def focus_search(self):
        self.search_entry.focus_set()
        self.search_entry.select_range(0,"end")
        self.heading.config(text="Buscar")
        self.section="Buscar"

    def debounced(self,event):
        if self.search_job:self.root.after_cancel(self.search_job)
        if len(self.search_text.get().strip())>=2:
            self.search_job=self.root.after(650,self.find)

    def find(self):
        query=self.search_text.get().strip()
        if len(query)<2:
            self.status_set("Digite ao menos dois caracteres para pesquisar.")
            return
        self.section="Buscar";self.heading.config(text="Resultados da pesquisa")
        self.reload(query=query)

    def reload(self,query=""):
        self.req+=1
        current=self.req
        kind="movie" if self.section=="Filmes" else "series" if self.section=="Séries" else "tv" if self.section=="Canais ao vivo" else "all"
        self.status_set("Consultando addons... Aguarde.")
        self.results=[];self.render_grid()
        def done(items):
            if current!=self.req or self.playing:return
            self.results=items
            self.status_set("{} {} • Catálogos reais".format(len(items),"canais" if kind=="tv" else "títulos"))
            self.render_grid()
        self.job(lambda:catalog.discover(self.addons,query,kind),done,
                lambda error:self.status_set("Falha ao consultar os catálogos: "+error))

    def clear_stage(self):
        for child in self.stage.winfo_children():child.destroy()
        self.references=[]

    def scroll_frame(self):
        self.clear_stage()
        panel=tk.Frame(self.stage,bg=BG)
        panel.pack(fill="both",expand=True)
        canvas=tk.Canvas(panel,bg=BG,highlightthickness=0)
        scroll=tk.Scrollbar(panel,orient="vertical",command=canvas.yview)
        canvas.configure(yscrollcommand=scroll.set)
        scroll.pack(side="right",fill="y")
        canvas.pack(side="left",fill="both",expand=True)
        child=tk.Frame(canvas,bg=BG)
        win=canvas.create_window((0,0),window=child,anchor="nw")
        child.bind("<Configure>",lambda e:canvas.configure(scrollregion=canvas.bbox("all")))
        canvas.bind("<Configure>",lambda e:canvas.itemconfigure(win,width=e.width))
        canvas.bind_all("<MouseWheel>",lambda e:canvas.yview_scroll(-int(e.delta/120),"units"))
        return child

    def render_grid(self):
        if self.section=="Canais ao vivo":
            self.render_live_guide()
            return
        surface=self.scroll_frame()
        if not self.results:
            self.label(surface,"Nenhum título nesta seção.",19,MUTED).pack(anchor="w",padx=28,pady=32)
            return
        featured=self.results[0] if self.section=="Início" else None
        if featured:
            hero=tk.Frame(surface,bg=LIFT,height=156)
            hero.pack(fill="x",padx=27,pady=(12,24))
            hero.pack_propagate(False)
            htitle=self.label(hero,featured.get("name",""),25,WHITE,True,bg=LIFT)
            htitle.pack(anchor="w",padx=24,pady=(20,4))
            self.label(hero,(featured.get("description") or "Descubra este título no Telares Play.")[:190],
                11,MUTED,bg=LIFT,wraplength=690).pack(anchor="w",padx=24)
            self.btn(hero,"Ver detalhes  ›",lambda item=featured:self.show_detail(item),CYAN,BG,pady=5).pack(anchor="w",padx=22,pady=7)
        row_title=self.label(surface,"Títulos disponíveis",17,WHITE,True)
        row_title.pack(anchor="w",padx=27,pady=(5,15))
        grid=tk.Frame(surface,bg=BG)
        grid.pack(fill="x",padx=21)
        cols=max(3,min(6,(self.root.winfo_width()-250)//174))
        for i,item in enumerate(self.results[:90]):
            frame=tk.Frame(grid,width=150,height=238,bg=PANEL,highlightthickness=1,
                highlightbackground="#284556",cursor="hand2")
            frame.grid(row=i//cols,column=i%cols,padx=7,pady=9,sticky="nw")
            frame.grid_propagate(False)
            poster_area=tk.Frame(frame,bg=LIFT,width=147,height=176)
            poster_area.pack(padx=1,pady=1)
            poster_area.pack_propagate(False)
            poster=tk.Label(poster_area,text="TELARES",font=("Segoe UI",11,"bold"),bg=LIFT,fg=CYAN)
            poster.pack(fill="both",expand=True)
            for widget in (frame,poster):
                widget.bind("<Button-1>",lambda e,x=item:self.show_detail(x))
            self.load_art(item,poster)
            name=self.label(frame,item.get("name","")[:37],10,WHITE,True,bg=PANEL,
                            wraplength=137)
            name.pack(anchor="w",padx=7,pady=(4,1))
            name.bind("<Button-1>",lambda e,x=item:self.show_detail(x))
            self.label(frame,"AO VIVO" if item.get("_kind")=="tv" else
                       "SÉRIE" if item.get("_kind",item.get("type"))=="series" else
                       "FILME",9,CYAN,bg=PANEL).pack(anchor="w",padx=7)

    def live_group_for(self,item):
        name=catalog.normalized(item.get("name",""))
        source=catalog.normalized(item.get("_catalog",""))
        if "telecine" in name:return "Telecine"
        if "hbo" in name or "cinemax" in name:return "HBO / Max"
        if any(w in name for w in ("sportv","espn","premiere","tnt sports")) or "esporte" in source:return "Esportes"
        if any(w in name for w in ("globo","record","sbt","tv cultura","rede tv")) or "aberto" in source:return "Canais abertos"
        if "noticia" in source or any(w in name for w in ("news","cnn")):return "Notícias"
        if "infantil" in source or "cartoon" in name:return "Infantil"
        if "religioso" in source:return "Religiosos"
        if "entretenimento" in source:return "Entretenimento"
        if "filmes" in source or "series" in source:return "Filmes e séries"
        return "Outros canais"

    def render_live_guide(self):
        self.clear_stage()
        root=tk.Frame(self.stage,bg=BG)
        root.pack(fill="both",expand=True,padx=16,pady=10)
        left=tk.Frame(root,width=175,bg=PANEL)
        left.pack(side="left",fill="y",padx=(0,9))
        left.pack_propagate(False)
        middle=tk.Frame(root,width=245,bg=PANEL)
        middle.pack(side="left",fill="y",padx=(0,9))
        middle.pack_propagate(False)
        preview=tk.Frame(root,bg=LIFT)
        preview.pack(side="left",fill="both",expand=True)
        groups=["Todos os canais"]
        channels=[i for i in self.results if i.get("_kind")=="tv"]
        for item in channels:
            group=self.live_group_for(item)
            if group not in groups:groups.append(group)
        if self.live_group not in groups:self.live_group="Todos os canais"
        filtered=[x for x in channels if self.live_group=="Todos os canais"
                  or self.live_group_for(x)==self.live_group]
        if self.live_selected not in filtered:self.live_selected=filtered[0] if filtered else None

        self.label(left,"CATEGORIAS",12,CYAN,True,bg=PANEL).pack(fill="x",padx=11,pady=13)
        group_list=tk.Listbox(left,activestyle="none",exportselection=False,bg=PANEL,fg=WHITE,
                             selectbackground="#24556A",selectforeground=WHITE,
                             bd=0,highlightthickness=0,font=("Segoe UI",11))
        group_list.pack(fill="both",expand=True,padx=7,pady=4)
        for name in groups:group_list.insert("end",name)
        group_list.selection_set(groups.index(self.live_group))
        def choose_group(event):
            picked=group_list.curselection()
            if not picked:return
            self.live_group=groups[picked[0]]
            self.live_selected=None
            if self.playing:
                self.playing=False
                if self.video:self.video.close();self.video=None
            self.render_live_guide()
        group_list.bind("<<ListboxSelect>>",choose_group)

        self.label(middle,"CANAIS • {}".format(len(filtered)),12,CYAN,True,bg=PANEL).pack(fill="x",padx=10,pady=13)
        channel_list=tk.Listbox(middle,activestyle="none",exportselection=False,bg=PANEL,fg=WHITE,
                               selectbackground="#24556A",selectforeground=WHITE,
                               bd=0,highlightthickness=0,font=("Segoe UI",11))
        channel_list.pack(fill="both",expand=True,padx=7,pady=4)
        for item in filtered:channel_list.insert("end",item.get("name","Canal"))
        if self.live_selected in filtered:channel_list.selection_set(filtered.index(self.live_selected))
        def choose_channel(event):
            picked=channel_list.curselection()
            if not picked:return
            item=filtered[picked[0]]
            self.live_selected=item
            if self.playing:
                self.playing=False
                if self.video:self.video.close();self.video=None
            self.current_item=None
            self.render_live_guide()
            self.start_title(item)
        channel_list.bind("<<ListboxSelect>>",choose_channel)

        chosen=self.live_selected
        self.label(preview,"AO VIVO",13,CYAN,True,bg=LIFT).pack(anchor="w",padx=16,pady=(16,2))
        self.label(preview,chosen.get("name","Escolha um canal") if chosen else "Escolha um canal",
            18,WHITE,True,bg=LIFT,wraplength=330).pack(anchor="w",padx=16,pady=(0,10))
        self.live_preview_frame=tk.Frame(preview,bg="#000000",height=260)
        self.live_preview_frame.pack(fill="both",expand=True,padx=12,pady=8)
        self.live_preview_frame.pack_propagate(False)
        controls=tk.Frame(preview,bg=LIFT)
        controls.pack(fill="x",padx=12,pady=8)
        self.video_note=self.label(preview,"Selecione um canal para iniciar a transmissão.",
                                   10,MUTED,bg=LIFT,wraplength=325)
        self.video_note.pack(anchor="w",padx=14,pady=(3,12))
        self.btn(controls,"▶ Reproduzir",lambda:self.start_title(chosen) if chosen else None,CYAN,BG,padx=6).pack(side="left",padx=3)
        self.btn(controls,"⏯",self.toggle_play,PANEL,padx=6).pack(side="left",padx=3)
        self.btn(controls,"Próxima fonte",self.next_source,PANEL,padx=6).pack(side="left",padx=3)
        self.btn(controls,"■",self.exit_player,PANEL,padx=6).pack(side="left",padx=3)

    def load_art(self,item,label):
        url=item.get("poster","")
        if not isinstance(url,str) or not url.startswith("https://"):return
        if url in self.images:
            img=self.images[url];label.configure(image=img,text="");self.references.append(img);return
        if url in self.image_pending:return
        self.image_pending.add(url)
        generation=self.req
        def fetch():
            req=urllib.request.Request(url,headers={"User-Agent":"TelaresPlay/0.1"})
            with urllib.request.urlopen(req,timeout=6) as r:data=r.read(1_200_000)
            im=Image.open(io.BytesIO(data));im.thumbnail((145,181))
            return im.copy()
        def done(image):
            self.image_pending.discard(url)
            if generation!=self.req:return
            image=ImageTk.PhotoImage(image)
            self.images[url]=image
            while len(self.images)>55:self.images.popitem(last=False)
            if label.winfo_exists():label.configure(image=image,text="");self.references.append(image)
        self.job(fetch,done,lambda error:self.image_pending.discard(url))

    def show_detail(self,item):
        self.detail=item
        target=self.scroll_frame()
        self.heading.config(text="Canais ao vivo" if item.get("_kind")=="tv" else "Detalhes")
        self.status_set("Metadados fornecidos pelos addons conectados")
        box=tk.Frame(target,bg=LIFT)
        box.pack(fill="x",padx=28,pady=22)
        self.label(box,item.get("name",""),27,WHITE,True,bg=LIFT).pack(anchor="w",padx=23,pady=(22,5))
        self.label(box,"AO VIVO" if item.get("_kind")=="tv" else
                   "SÉRIE" if item.get("_kind",item.get("type"))=="series" else
                   "FILME",11,CYAN,True,bg=LIFT).pack(anchor="w",padx=23)
        self.label(box,item.get("description","Sem sinopse disponível.")[:1050],13,MUTED,bg=LIFT,
                   wraplength=680).pack(anchor="w",padx=23,pady=15)
        actions=tk.Frame(box,bg=LIFT);actions.pack(anchor="w",padx=21,pady=(0,22))
        self.btn(actions,"▶  Assistir ao vivo" if item.get("_kind")=="tv" else "▶  Assistir",
                 lambda:self.start_title(item),CYAN,BG).pack(side="left",padx=3)
        entry=self.get_entry(item)
        self.btn(actions,"♥  Curtido" if entry.get("liked") else "♡  Curtir",
                 lambda:self.toggle_saved(item,"liked"),PANEL).pack(side="left",padx=6)
        self.btn(actions,"✓  Na lista" if entry.get("later") else "+  Assistir mais tarde",
                 lambda:self.toggle_saved(item,"later"),PANEL).pack(side="left",padx=6)
        if item.get("_kind",item.get("type"))=="series":
            self.label(target,"Episódios",18,WHITE,True).pack(anchor="w",padx=28,pady=12)
            list_box=tk.Frame(target,bg=BG);list_box.pack(fill="x",padx=28)
            generation=self.req
            def ready(eps):
                if not list_box.winfo_exists() or generation!=self.req:return
                if not eps:self.label(list_box,"Sem episódios disponíveis nos addons.",12,MUTED).pack();return
                for ep in eps[:150]:
                    ident=ep.get("id","")
                    button=self.btn(list_box,"T{} · E{}   {}".format(ep.get("season",0),ep.get("episode",0),ep.get("title",ident)),
                        lambda e=ep:self.start_title(dict(item,_episode=e["id"],_episode_name=e.get("title",""))),PANEL)
                    button.pack(fill="x",pady=3)
            self.job(lambda:catalog.episodes(self.addons,item),ready)

    def token(self,item):
        return catalog.key(item)+"|"+str(item.get("_episode",""))

    def get_entry(self,item):
        return self.data["entries"].get(self.token(item),{})

    def toggle_saved(self,item,field):
        ident=self.token(item)
        value=dict(self.get_entry(item))
        value.update(safe_item(item))
        value[field]=not value.get(field,False)
        value["updated"]=time.time()
        self.data["entries"][ident]=value
        persist(self.data)
        self.show_detail(item)

    def start_title(self,item):
        if not item:return
        if self.playing:
            self.playing=False
            if self.video:self.video.close();self.video=None
        self.status_set("Localizando fontes HTTP/HLS compatíveis...")
        seq=self.req=self.req+1
        def done(source_list):
            if seq!=self.req:return
            if not source_list:
                messagebox.showwarning("Telares Play","Não foram encontradas fontes HTTP/HLS/DASH ou BitTorrent utilizáveis. DRM depende de um cliente autorizado.")
                return
            self.source_list=source_list
            self.source_index=0
            self.current_item=item
            self.resume_sec=0 if item.get("_kind")=="tv" else float(self.get_entry(item).get("position") or 0)
            self.enter_player()
            self.play_source()
        def select_sources():
            candidates=catalog.streams(self.addons,item)
            if item.get("_kind")=="tv":
                # Channels are continuous HLS/HTTP signals. Avoid downloading
                # segments with Range before giving the bundled VLC a chance.
                return [x for x in candidates[:16] if x["_url"].startswith(("http://","https://"))]
            return catalog.verified(candidates)+[x for x in candidates if x.get("_torrent")]
        self.job(select_sources,done,
                lambda reason:messagebox.showerror("Erro ao abrir o vídeo",reason))

    def enter_player(self):
        if self.section=="Canais ao vivo" and self.live_preview_frame is not None:
            self.playing=True
            self.live_preview_frame.update_idletasks()
            try:
                self.video=VideoEngine(self.live_preview_frame.winfo_id(),
                    lambda kind:self.messages.put((self.player_event,kind)))
            except Exception as e:
                self.playing=False
                messagebox.showerror("VLC indisponível",str(e))
            return
        self.clear_stage()
        self.playing=True
        area=tk.Frame(self.stage,bg="#000000")
        area.pack(fill="both",expand=True,padx=14,pady=5)
        video_frame=tk.Frame(area,bg="#000000")
        video_frame.pack(fill="both",expand=True)
        video_frame.update_idletasks()
        controls=tk.Frame(area,bg=PANEL,height=58)
        controls.pack(fill="x")
        self.btn(controls,"← Voltar",self.exit_player,PANEL).pack(side="left",padx=5)
        self.btn(controls,"◀ 10s",lambda:self.jump(-10),PANEL).pack(side="left",padx=4)
        self.btn(controls,"⏯",self.toggle_play,CYAN,BG).pack(side="left",padx=4)
        self.btn(controls,"10s ▶",lambda:self.jump(10),PANEL).pack(side="left",padx=4)
        self.btn(controls,"Fonte alternativa",self.next_source,PANEL).pack(side="right",padx=8)
        self.btn(controls,"Legendas",self.subtitles_dialog,PANEL).pack(side="right",padx=4)
        self.video_note=self.label(controls,"Selecionando reprodução...",10,MUTED,bg=PANEL)
        self.video_note.pack(side="right",padx=8)
        try:self.video=VideoEngine(video_frame.winfo_id(),lambda kind:self.messages.put((self.player_event,kind)))
        except Exception as e:
            self.playing=False
            messagebox.showerror("VLC indisponível",str(e))
            self.open_section("Início")

    def play_source(self):
        if not self.playing or not self.video:return
        if self.source_index>=len(self.source_list):
            messagebox.showerror("Telares Play","As fontes verificadas falharam na reprodução.")
            self.exit_player();return
        chosen=self.source_list[self.source_index]
        self.started=False
        self.play_started_at=time.monotonic()
        self.video_note.config(text="Fonte {}/{} • iniciando".format(self.source_index+1,len(self.source_list)))
        if self.video.torrent:self.video.torrent.close();self.video.torrent=None
        if chosen.get("_torrent"):
            from torrent_engine import TorrentEngine
            video=self.video;index=self.source_index;token=self.req
            try:engine=TorrentEngine();video.torrent=engine
            except Exception:
                self.next_source();return
            self.video_note.config(text="Preparando BitTorrent · aguardando dados…")
            def ready(url):
                if self.playing and self.video is video and self.req==token and self.source_index==index and video.torrent is engine:video.play(url)
                else:engine.close()
            def failed(error):
                engine.close()
                if self.playing and self.video is video and self.req==token and self.source_index==index:self.next_source()
            self.job(lambda:engine.open(chosen["_url"],chosen.get("_file_index")),ready,failed)
        else:self.video.play(chosen["_url"],chosen.get("_headers"))

    def next_source(self):
        if not self.playing:return
        self.save_progress()
        self.source_index+=1
        self.play_source()

    def player_event(self,event):
        if not self.playing or not self.video:return
        if event=="playing":
            if not self.started:
                self.started=True
                self.video_note.config(text="Reproduzindo • fonte automática")
                if self.resume_sec>5:
                    self.video.resume(self.resume_sec)
                    self.resume_sec=0
        elif event=="error":
            self.next_source()
        elif event=="ended":
            if self.current_item and self.current_item.get("_kind")=="tv":
                self.next_source()
            else:
                self.save_progress()
                self.exit_player()

    def tick(self):
        if self.playing and self.video:
            if self.started and time.monotonic()-self.last_save>10:
                self.last_save=time.monotonic();self.save_progress()
            if not self.started and time.monotonic()-self.play_started_at>(100 if self.current_item and self.source_list[self.source_index].get("_torrent") else 22):
                self.next_source()
        self.root.after(1500,self.tick)

    def save_progress(self):
        if not self.playing or not self.video or not self.current_item or not self.started:return
        if self.current_item.get("_kind")=="tv":return
        pos=self.video.position()
        if pos<2:return
        ident=self.token(self.current_item)
        saved=dict(self.get_entry(self.current_item))
        saved.update(safe_item(self.current_item))
        saved["position"]=pos
        saved["updated"]=time.time()
        saved["watched"]=True
        self.data["entries"][ident]=saved
        persist(self.data)

    def exit_player(self):
        if self.playing:
            self.save_progress()
            self.playing=False
            if self.video:self.video.close();self.video=None
        self.current_item=None;self.source_list=[]
        if self.section=="Canais ao vivo":
            self.render_live_guide()
        else:
            self.open_section("Início")

    def toggle_play(self):
        if self.playing and self.video:self.video.pause()

    def jump(self,seconds):
        if self.playing and self.video and (not self.current_item or self.current_item.get("_kind")!="tv"):
            self.video.seek(seconds)

    def fullscreen(self):
        self.full=not self.full
        self.root.attributes("-fullscreen",self.full)

    def escape(self):
        if self.full:self.fullscreen()
        elif self.playing:self.exit_player()

    def subtitles_dialog(self):
        if not self.video:return
        window=tk.Toplevel(self.root);window.title("Legendas do vídeo")
        tracks=self.video.player.video_get_spu_description() or []
        for ident,name in tracks:
            label=name.decode("utf-8","replace") if isinstance(name,bytes) else str(name)
            self.btn(window,label,lambda n=ident:self.video.player.video_set_spu(n) if self.video else None).pack(fill="x")
        if not tracks:self.label(window,"Esta fonte ainda não disponibilizou faixas de legenda.",12).pack(padx=15,pady=15)
        note=self.label(window,"Atraso: {:.1f} s".format(self.subtitle_delay));note.pack()
        def delay(delta):
            self.subtitle_delay=max(-60,min(60,self.subtitle_delay+delta))
            if self.video:self.video.subtitle_delay(self.subtitle_delay)
            note.config(text="Atraso: {:.1f} s".format(self.subtitle_delay))
        self.btn(window,"Antecipar 0,5 s",lambda:delay(-.5)).pack(side="left")
        self.btn(window,"Atrasar 0,5 s",lambda:delay(.5)).pack(side="left")

    def sources_dialog(self):
        window=tk.Toplevel(self.root)
        window.title("Telares Play • addons")
        window.configure(bg=BG)
        window.geometry("720x420")
        self.label(window,"Gerenciar addons Stremio HTTPS",20,WHITE,True).pack(anchor="w",padx=23,pady=18)
        listing=tk.Listbox(window,bg=LIFT,fg=WHITE,font=("Segoe UI",11),selectbackground=CYAN)
        listing.pack(fill="both",expand=True,padx=22)
        for source in self.addons:listing.insert("end",source)
        buttons=tk.Frame(window,bg=BG);buttons.pack(pady=15)
        def add():
            src=simpledialog.askstring("Manifesto","URL HTTPS /manifest.json",parent=window)
            if not src:return
            try:catalog.base(src)
            except Exception as e:messagebox.showwarning("URL inválida",str(e),parent=window);return
            def complete(value):
                if src not in self.addons:
                    self.addons.append(src)
                    self.removed_defaults.discard(src)
                    self.data["addons"]=self.addons
                    self.data["removed_default_addons"]=sorted(self.removed_defaults)
                    persist(self.data);listing.insert("end",src)
            self.job(lambda:catalog.manifest(src),complete,
                     lambda error:messagebox.showerror("Falha no addon",error,parent=window))
        def remove():
            selection=listing.curselection()
            if not selection:return
            value=listing.get(selection[0]);self.addons.remove(value)
            if value in catalog.DEFAULT:self.removed_defaults.add(value)
            self.data["removed_default_addons"]=sorted(self.removed_defaults)
            self.data["addons"]=self.addons;persist(self.data);listing.delete(selection[0])
        self.btn(buttons,"Adicionar",add,CYAN,BG).pack(side="left",padx=7)
        self.btn(buttons,"Remover selecionado",remove).pack(side="left",padx=7)
        self.btn(buttons,"Fechar",window.destroy).pack(side="left",padx=7)

    def close(self):
        try:
            if self.playing:self.save_progress()
            if self.video:self.video.close()
            persist(self.data)
            self.executor.shutdown(wait=False,cancel_futures=True)
        finally:self.root.destroy()

    def run(self):self.root.mainloop()

if __name__=="__main__":
    if len(sys.argv)>2 and sys.argv[1]=="--self-test":
        from self_test import run
        sys.exit(run(sys.argv[2]))
    TelaresPlay().run()

