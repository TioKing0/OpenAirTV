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
if __name__=="__main__":unittest.main()
