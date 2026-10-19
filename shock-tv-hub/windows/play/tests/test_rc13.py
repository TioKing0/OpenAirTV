import pathlib,sys,unittest
from unittest.mock import patch
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import catalog

class RC13Tests(unittest.TestCase):
    def test_mixed_provider_does_not_turn_movies_into_tv(self):
        self.assertFalse(catalog.live_catalog({'id':'mixed.iptv.provider','name':'IPTV plus films'},{'type':'movie','name':'Movies'},'https://mixed.example/manifest.json'))
        self.assertTrue(catalog.live_catalog({'id':'mixed','name':'Mixed'},{'type':'channel','name':'News'}))
    def test_resource_specific_types_override_manifest(self):
        m={'types':['movie'],'idPrefixes':['tt'],'resources':['subtitles',{'name':'stream','types':['series'],'idPrefixes':['kitsu']}]}
        self.assertTrue(catalog.supports(m,'subtitles','movie','tt123'))
        self.assertFalse(catalog.supports(m,'subtitles','series','tt123:1:1'))
        self.assertTrue(catalog.supports(m,'stream','series','kitsu:123'))
        self.assertFalse(catalog.supports(m,'stream','series','tt123'))

