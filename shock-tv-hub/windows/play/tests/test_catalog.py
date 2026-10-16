import sys
import pathlib
import unittest
from unittest.mock import patch
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import catalog

class CatalogTest(unittest.TestCase):
    def test_safe_manifest(self):
        self.assertEqual(catalog.base("https://example.org/manifest.json"),"https://example.org/")
        with self.assertRaises(ValueError):catalog.base("http://localhost/manifest.json")
    def test_support(self):
        m={"resources":[{"name":"stream","types":["movie"],"idPrefixes":["tt"]}]}
        self.assertTrue(catalog.supports(m,"stream","movie","tt123"))
        self.assertFalse(catalog.supports(m,"stream","series","tt123"))
        self.assertFalse(catalog.supports(m,"stream","movie","series:1"))
    def test_unique(self):
        self.assertEqual(len(catalog.unique([{"type":"movie","id":"a"},{"type":"movie","id":"a"},{"type":"series","id":"a"}])),2)
    def test_prefer_portuguese_1080(self):
        self.assertGreater(catalog.score({"_label":"1080p Dublado pt-BR"}),catalog.score({"_label":"720p Original"}))
    def test_torrent_only_is_not_playable(self):
        doc={"id":"provider","resources":[{"name":"stream","types":["movie"]}]}
        with patch.object(catalog,"manifest",return_value=doc),patch.object(catalog,"get_json",return_value={"streams":[{"infoHash":"abcdef","title":"4k"}]}):
            self.assertEqual(catalog.streams(["https://example.com/manifest.json"],{"type":"movie","id":"tt01"}),[])
    def test_search_only_declared_catalog(self):
        doc={"id":"provider","name":"A","resources":["catalog"],"catalogs":[{"type":"movie","id":"top"},{"type":"movie","id":"search","extra":[{"name":"search"}]}]}
        def fake_json(url):
            if "/search=" in url:return {"metas":[{"id":"tt01","name":"Teste"}]}
            return {"metas":[{"id":"tt02","name":"Errado"}]}
        with patch.object(catalog,"manifest",return_value=doc),patch.object(catalog,"get_json",side_effect=fake_json):
            items=catalog.discover(["https://example.com/manifest.json"],"Teste")
        self.assertEqual([x["name"] for x in items],["Teste"])

    def test_platforms_use_addon_provenance_not_title(self):
        self.assertIn("Netflix", catalog.category_of("Netflix Movies","Addon",[],"netflix"))
        self.assertIn("Disney+", catalog.category_of("Disney Plus","Addon",[],"disney"))
        self.assertIn("Max / HBO", catalog.category_of("HBO Series","Addon",[],"hbo"))
        self.assertEqual(catalog.category_of("Top movies","Cinemeta",[],"top"),set())

    def test_extra_genres(self):
        for label,genre in [("Documentários","Documentary"),("Terror","Horror"),("Comédia","Comedy"),("Ficção científica","Sci-Fi")]:
            self.assertIn(label,catalog.category_of("Top","Addon",[genre],"featured"))
        self.assertNotIn("Documentários",catalog.category_of("Top","Addon",["Animation"],"featured"))
    def test_extra_platforms(self):
        for label,name in {"Prime Video":"Amazon Prime Video","Apple TV+":"Apple TV+","Paramount+":"Paramount Plus","Crunchyroll":"Crunchyroll"}.items():
            self.assertIn(label,catalog.category_of(name,"Addon",[],"top"))
        self.assertNotIn("Prime Video",catalog.category_of("Top","Cinemeta",[],"top"))
    def test_declared_genre_option(self):
        options={"extra":[{"name":"genre","options":["Documentary","Sci-Fi","Horror","Comedy"]}]}
        for label in ("Documentários","Ficção científica","Terror","Comédia"):
            self.assertTrue(catalog.declared_genre(options,label).startswith("/genre="))
    def test_removed_anime_category_but_preserved_animation_shelf(self):
        self.assertNotIn("Anime",catalog.CATEGORY_OPTIONS)
        self.assertNotIn("Anime",catalog.GENRE_ALIASES)
        root=pathlib.Path(__file__).resolve().parents[3]
        android=(root/"android/app/src/main/java/br/com/shocktv/MainActivity.java").read_text(encoding="utf-8")
        ipad=(root/"ios/ShockViewController.m").read_text(encoding="utf-8")
        self.assertIn('"Animação","Ficção científica"',android)
        self.assertNotIn('if("Anime".equals(category))',android)
        self.assertNotIn('if([tag isEqualToString:@"Anime"])',ipad)
        self.assertIn('@"Filmes"',ipad)
        self.assertIn('@"Séries"',ipad)

    def test_existing_categories_kept(self):
        self.assertEqual({"Netflix","Max / HBO","Disney+"}.difference(catalog.CATEGORY_OPTIONS),set())
        self.assertEqual({"Prime Video","Apple TV+","Paramount+","Crunchyroll","Documentários","Terror","Comédia","Ficção científica"}.difference(catalog.CATEGORY_OPTIONS),set())

    def test_navigation_and_original_genres_are_preserved(self):
        root=pathlib.Path(__file__).resolve().parents[3]
        android=(root/"android/app/src/main/java/br/com/shocktv/MainActivity.java").read_text(encoding="utf-8")
        ipad=(root/"ios/ShockViewController.m").read_text(encoding="utf-8")
        for text in ('"Filmes","filmes"', '"Séries","series"', 'String[] genres={"Ação","Comédia","Drama","Suspense","Terror","Animação","Ficção científica","Documentário","Romance"}'):
            self.assertIn(text,android)
        for text in ('@"Início"', '@"Filmes"', '@"Séries"', '@"Minha lista"'):
            self.assertIn(text,ipad)
        for tag in ("Prime Video","Apple TV+","Paramount+","Crunchyroll","Documentários","Terror","Comédia","Ficção científica"):
            self.assertIn(tag,android)
            self.assertIn(tag,ipad)

    def test_new_addons_are_bundled(self):
        links=["https://catalogo-iptv-production.up.railway.app/manifest.json",
               "https://plaviohd.edon.workers.dev/manifest.json",
               "https://c69e439124f3-tvvio.baby-beamup.club/manifest.json",
               "https://h-gate.site/dc/manifest.json"]
        self.assertTrue(all(link in catalog.DEFAULT for link in links))
    def test_tvvio_catalog_discovery(self):
        self.assertEqual("tv",catalog.effective_kind("tv","Todos","tvvio_channels"))
        self.assertEqual("tv",catalog.effective_kind("channel","Live","channels"))
        self.assertEqual("movie",catalog.effective_kind("DC Universe","Movies","103575:movies"))
        self.assertEqual("series",catalog.effective_kind("DC Universe","Series","103575:series"))
        manifest_doc={"id":"org.tvvio","name":"Tvvio","resources":["catalog","stream"],
                      "catalogs":[{"type":"tv","id":"tvvio_channels","name":"Todos"}]}
        def endpoint(url):
            if "/catalog/tv/" in url:return {"metas":[{"id":"tvvio:canal","name":"Canal Teste","type":"tv"}]}
            raise ValueError(url)
        with patch.object(catalog,"manifest",return_value=manifest_doc),patch.object(catalog,"get_json",side_effect=endpoint):
            rows=catalog.discover(["https://example.com/manifest.json"],kind="tv")
        self.assertEqual(len(rows),1)
        self.assertEqual(rows[0]["_kind"],"tv")
        self.assertEqual(rows[0]["type"],"tv")
    def test_existing_movie_series_not_mixed_with_live(self):
        manifest_doc={"id":"provider","name":"Test","resources":["catalog"],
            "catalogs":[{"type":"tv","id":"channels","name":"Live"},{"type":"movie","id":"top","name":"Movies"}]}
        def endpoint(url):
            if "/catalog/tv/" in url:return {"metas":[{"type":"tv","name":"TV","id":"tv:1"}]}
            return {"metas":[{"type":"movie","name":"Filme","id":"tt1"}]}
        with patch.object(catalog,"manifest",return_value=manifest_doc),patch.object(catalog,"get_json",side_effect=endpoint):
            titles=catalog.discover(["https://example.com/manifest.json"])
        self.assertEqual([x["_kind"] for x in titles],["movie"])

    def test_iptv_exposed_as_series_is_live(self):
        meta={"id":"catalogo-iptv","name":"Catalogo IPTV","description":"Canais ao vivo",
            "catalogs":[{"type":"series","id":"channels","name":"Telecine"}]}
        def loader(url):
            if "/catalog/series/" in url:return {"metas":[{"id":"iptv:telecine","name":"Telecine Fun"}]}
            raise ValueError(url)
        with patch.object(catalog,"manifest",return_value=meta),patch.object(catalog,"get_json",side_effect=loader):
            series=catalog.discover(["https://catalogo-iptv-production.up.railway.app/manifest.json"],kind="series")
            tv=catalog.discover(["https://catalogo-iptv-production.up.railway.app/manifest.json"],kind="tv")
        self.assertEqual(series,[])
        self.assertEqual(len(tv),1)
        self.assertEqual(tv[0]["type"],"series")
        self.assertEqual(tv[0]["_kind"],"tv")
    def test_regular_series_not_mistaken_for_tv(self):
        self.assertFalse(catalog.live_catalog({"name":"Cinemeta","id":"cinemeta"},
            {"type":"series","id":"top","name":"Popular TV series"},"https://v3-cinemeta.strem.io/manifest.json"))


    def test_other_clients_enforce_independent_display_type(self):
        root=pathlib.Path(__file__).resolve().parents[3]
        ios=(root/"ios/ShockViewController.m").read_text(encoding="utf-8")
        java=(root/"android/app/src/main/java/br/com/shocktv/MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("isBroadcastCatalog:catalog manifest:manifest source:url",ios)
        self.assertIn("isBroadcastCatalog:cat manifest:manifest source:source",ios)
        self.assertIn('type=[self str:item[@"_kind"]]',ios)
        self.assertIn("liveCatalog=isBroadcastCatalog(manifest,catalog,manifestUrl)",java)
        self.assertIn('m.displayType=liveCatalog?"tv"',java)
        self.assertIn('if(!isChannel(m)&&m.name.toLowerCase(Locale.ROOT).contains(needle))',java)

    def test_live_guide_cross_platform(self):
        root=pathlib.Path(__file__).resolve().parents[3]
        windows=(root/"windows/play/main.py").read_text(encoding="utf-8")
        ipad=(root/"ios/ShockViewController.m").read_text(encoding="utf-8")
        tv=(root/"android/app/src/main/java/br/com/shocktv/MainActivity.java").read_text(encoding="utf-8")
        for s in ("def render_live_guide","self.live_preview_frame.winfo_id()"):self.assertIn(s,windows)
        for s in ("updateLiveGuide","liveGroups","liveChannels"):self.assertIn(s,ipad)
        for s in ("buildLiveChannelGuide","liveGroupOf","isBroadcastCatalog"):self.assertIn(s,tv)

if __name__=="__main__":unittest.main()
