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

    def test_anime_requires_explicit_genre_or_catalog(self):
        self.assertIn("Anime", catalog.category_of("Anime HD","Cinemeta",[],"top"))
        self.assertIn("Anime", catalog.category_of("Top","Cinemeta",["Action","Anime"],"top"))
        self.assertNotIn("Anime", catalog.category_of("Animation", "Generic", ["Animation"], "top"))
    def test_platforms_use_addon_provenance_not_title(self):
        self.assertIn("Netflix", catalog.category_of("Netflix Movies","Addon",[],"netflix"))
        self.assertIn("Disney+", catalog.category_of("Disney Plus","Addon",[],"disney"))
        self.assertIn("Max / HBO", catalog.category_of("HBO Series","Addon",[],"hbo"))
        self.assertEqual(catalog.category_of("Top movies","Cinemeta",[],"top"),set())

    def test_extra_genres(self):
        for label,genre in [("Documentários","Documentary"),("Terror","Horror"),("Comédia","Comedy"),("Ficção científica","Sci-Fi")]:
            self.assertIn(label,catalog.category_of("Top","Addon",[genre],"featured"))
        self.assertNotIn("Anime",catalog.category_of("Top","Addon",["Animation"],"featured"))
    def test_extra_platforms(self):
        for label,name in {"Prime Video":"Amazon Prime Video","Apple TV+":"Apple TV+","Paramount+":"Paramount Plus","Crunchyroll":"Crunchyroll"}.items():
            self.assertIn(label,catalog.category_of(name,"Addon",[],"top"))
        self.assertNotIn("Prime Video",catalog.category_of("Top","Cinemeta",[],"top"))
    def test_declared_genre_option(self):
        options={"extra":[{"name":"genre","options":["Documentary","Sci-Fi","Horror","Comedy"]}]}
        for label in ("Documentários","Ficção científica","Terror","Comédia"):
            self.assertTrue(catalog.declared_genre(options,label).startswith("/genre="))
    def test_existing_categories_kept(self):
        self.assertEqual({"Anime","Netflix","Max / HBO","Disney+"}.difference(catalog.CATEGORY_OPTIONS),set())
        self.assertEqual({"Prime Video","Apple TV+","Paramount+","Crunchyroll","Documentários","Terror","Comédia","Ficção científica"}.difference(catalog.CATEGORY_OPTIONS),set())

if __name__=="__main__":unittest.main()
