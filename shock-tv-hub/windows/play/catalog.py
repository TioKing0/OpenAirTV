"""Stremio protocol adapters: real catalogs, metadata, and direct stream links."""
import concurrent.futures as futures
import json
import re
import urllib.parse as urlparse
import urllib.request as request

DEFAULT = [
 "https://v3-cinemeta.strem.io/manifest.json",
 "https://fenixflix.fenixhub.online/manifest.json",
 "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/manifest.json",
 "https://torrentio.strem.fun/manifest.json",
 "https://thepiratebay-plus.strem.fun/manifest.json",
]
def get_json(url):
    req=request.Request(url,headers={"User-Agent":"TelaresPlay/0.1","Accept":"application/json"})
    with request.urlopen(req,timeout=8) as r:
        body=r.read(2_000_001)
    if len(body)>2_000_000: raise ValueError("Resposta muito grande")
    obj=json.loads(body)
    if not isinstance(obj,dict): raise ValueError("JSON inesperado")
    return obj

def base(url):
    p=urlparse.urlsplit(url)
    if p.scheme!="https" or not p.netloc or not p.path.endswith("/manifest.json"): raise ValueError("URL manifesto HTTPS invalida")
    return url[:-len("manifest.json")]

def enc(value):
    return urlparse.quote(str(value),safe=":")

def manifest(url):
    m=get_json(url)
    if not m.get("id") or not isinstance(m.get("resources"),list): raise ValueError("Manifesto invalido")
    return m

def supports(m,resource,typ,identifier):
    for x in m.get("resources",[]):
        if x==resource: return True
        if isinstance(x,dict) and x.get("name")==resource:
            if x.get("types") and typ not in x["types"]: continue
            prefix=x.get("idPrefixes") or m.get("idPrefixes") or []
            if prefix and not any(identifier.startswith(p) for p in prefix if isinstance(p,str)): continue
            return True
    return False

def key(item): return str(item.get("type",""))+"|"+str(item.get("id",""))

def unique(items):
    used=set();out=[]
    for x in items:
        k=key(x)
        if x.get("id") and k not in used:
            used.add(k);out.append(x)
    return out

import unicodedata

CATEGORY_OPTIONS = ["Anime","Netflix","Max / HBO","Disney+","Prime Video","Apple TV+","Paramount+","Crunchyroll","Documentários","Terror","Comédia","Ficção científica"]
GENRE_ALIASES = {"Anime":["anime","japanese animation","animacao japonesa"],"Documentários":["documentario","documentary","documentaries","documental"],"Terror":["terror","horror"],"Comédia":["comedia","comedy"],"Ficção científica":["ficcao cientifica","science fiction","sci fi","scifi","sci-fi","science-fiction"]}
PLATFORM_ALIASES = {"Netflix":["netflix"],"Max / HBO":["hbo","hbomax","hbo max","max originals","max catalog","max tv"],"Disney+":["disney+","disney plus","disneyplus"],"Prime Video":["prime video","primevideo","amazon prime"],"Apple TV+":["apple tv+","apple tv plus","appletv+","apple originals"],"Paramount+":["paramount+","paramount plus","paramountplus"],"Crunchyroll":["crunchyroll"]}

def normalized(value):
    text=unicodedata.normalize("NFKD",str(value or "").lower())
    text="".join(c for c in text if not unicodedata.combining(c))
    return " ".join(text.replace("-", " ").replace("_"," ").split())

def category_of(catalog_name="",provider="",genres=(),catalog_id=""):
    """Classifies addon declarations; does not assert official rights or regional availability."""
    origin=normalized(" ".join((str(catalog_name),str(provider),str(catalog_id))))
    genre_names=[normalized(x) for x in (genres if isinstance(genres,list) else [])]
    result=set()
    for category,words in GENRE_ALIASES.items():
        probes=[normalized(x) for x in words]
        if any(any(p in g for p in probes) for g in genre_names) or any(p in origin for p in probes):result.add(category)
    for category,words in PLATFORM_ALIASES.items():
        if any(normalized(w) in origin for w in words):result.add(category)
    return result

def declared_genre(cat,category):
    words=[normalized(w) for w in GENRE_ALIASES.get(category,[])]
    for entry in cat.get("extra",[]):
        if not isinstance(entry,dict) or entry.get("name")!="genre":continue
        for option in entry.get("options",[]):
            if isinstance(option,str) and any(word in normalized(option) for word in words):
                return "/genre="+enc(option)
    return ""

def discover(addons,term="",kind="all",category=""):
    term=term.strip()[:120]
    tasks=[]
    def catalogs(url):
        m=manifest(url);root=base(url);out=[]
        for cat in m.get("catalogs",[]):
            if not isinstance(cat,dict): continue
            typ=cat.get("type"); ident=cat.get("id")
            if typ not in ("movie","series") or not isinstance(ident,str): continue
            if kind!="all" and kind!=typ: continue
            if not re.fullmatch("[A-Za-z0-9_.:-]{1,100}",ident):continue
            extras=cat.get("extra",[])
            if term and not any(e=="search" or isinstance(e,dict) and e.get("name")=="search" for e in extras):continue
            provider=m.get("name","Addon")
            catalog_name=cat.get("name",ident)
            if category in PLATFORM_ALIASES and category not in category_of(catalog_name,provider,[],ident):continue
            genre_args=declared_genre(cat,category) if category in GENRE_ALIASES and not term else ""
            extra="/search="+enc(term) if term else genre_args
            out.append((root+"catalog/"+enc(typ)+"/"+enc(ident)+extra+".json",typ,root,m.get("name","Addon"),cat.get("name",ident),ident, bool(genre_args)))
            if len(out)>=(5 if term else 3):break
        return out
    with futures.ThreadPoolExecutor(max_workers=6) as pool:
        mfs=[pool.submit(catalogs,u) for u in list(dict.fromkeys(addons))[:20]]
        for f in futures.as_completed(mfs):
            try:tasks.extend(f.result())
            except Exception:pass
        fs={pool.submit(get_json,url): (typ,root,provider,catname,catid,by_genre) for url,typ,root,provider,catname,catid,by_genre in tasks[:85]}
        items=[]
        for f in futures.as_completed(fs):
            typ,root,provider,catname,catid,by_genre=fs[f]
            try:
                data=f.result().get("metas",[])
                for x in data[:32] if isinstance(data,list) else []:
                    if not isinstance(x,dict) or not isinstance(x.get("id"),str) or not isinstance(x.get("name"),str):continue
                    item=dict(x,type=x.get("type",typ),_base=root,_provider=provider,_catalog=catname)
                    if by_genre:item["_queried_genre"]=category
                    if category and category not in category_of(catname,provider,x.get("genres",[]),catid) and not by_genre:
                        continue
                    items.append(item)
            except Exception:pass
    items=unique(items)
    if term:
        needle=term.lower()
        items.sort(key=lambda m:(m.get("name","").lower()==needle,m.get("name","").lower().startswith(needle),needle in m.get("name","").lower()),reverse=True)
    return items[:250]

def episodes(addons,item):
    typ=item.get("type","series");ident=item.get("id","")
    for u in addons[:20]:
        try:
            m=manifest(u)
            if not supports(m,"meta",typ,ident):continue
            result=get_json(base(u)+"meta/"+enc(typ)+"/"+enc(ident)+".json").get("meta",{}).get("videos",[])
            if not isinstance(result,list):continue
            return sorted([x for x in result if isinstance(x,dict) and x.get("id")],key=lambda x:(int(x.get("season") or 0),int(x.get("episode") or 0)))
        except Exception:continue
    return []

def score(x):
    text=x.get("_label","").lower()
    q=40 if "1080" in text else 30 if "720" in text else 20 if "2160" in text or "4k" in text else 0
    return q+(30 if any(t in text for t in ("dublado","pt-br","portugu")) else 0)

def streams(addons,item):
    typ=item.get("type","movie");ident=item.get("_episode") or item.get("id","")
    out=[]
    for u in addons[:20]:
        try:
            m=manifest(u)
            if not supports(m,"stream",typ,ident):continue
            data=get_json(base(u)+"stream/"+enc(typ)+"/"+enc(ident)+".json")
            for x in data.get("streams",[])[:60]:
                if not isinstance(x,dict):continue
                link=x.get("url","")
                if not isinstance(link,str) or urlparse.urlsplit(link).scheme not in ("http","https"):continue
                if (x.get("behaviorHints") or {}).get("proxyHeaders"):continue
                label=" ".join(str(t) for t in (m.get("name",""),x.get("name",""),x.get("title","")))
                out.append(dict(item,_url=link,_label=label))
                if len(out)>=64:break
        except Exception:continue
    used=set();good=[]
    for x in sorted(out,key=score,reverse=True):
        if x["_url"] not in used:used.add(x["_url"]);good.append(x)
    return good[:24]

def probe(item):
    link=item["_url"]
    req=request.Request(link,headers={"Range":"bytes=0-2047","Accept-Encoding":"identity","User-Agent":"TelaresPlay/0.1"})
    try:
        with request.urlopen(req,timeout=6) as r:
            if r.status not in (200,206):return None
            ct=r.headers.get("Content-Type","").lower()
            if "html" in ct or "json" in ct:return None
            chunk=r.read(1024);final=r.geturl()
        if not chunk:return None
        start=chunk[:128].lstrip().lower()
        if b"<html" in start or b"<!doctype" in start or start.startswith(b'{"error"'):return None
        p=urlparse.urlsplit(final)
        if urlparse.urlsplit(link).scheme=="https" and p.scheme!="https":return None
        likely=b"ftyp" in chunk[:12] or b"#EXTM3U" in chunk[:16] or any(t in ct for t in ("video/","audio/","mpegurl","octet-stream")) or p.path.lower().endswith((".mp4",".m3u8",".m4v",".mov",".ts",".mkv"))
        return dict(item,_url=final) if likely else None
    except Exception:return None

def verified(candidates):
    with futures.ThreadPoolExecutor(max_workers=4) as pool:
        return [x for x in pool.map(probe,candidates[:16]) if x]
