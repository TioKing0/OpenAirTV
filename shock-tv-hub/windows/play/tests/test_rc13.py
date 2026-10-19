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
    def test_torrent_route_needs_real_index(self):
        stream={'infoHash':'a'*40}
        link,external=catalog.torrent_link(stream,'http://localhost:11470')
        self.assertTrue(external);self.assertTrue(link.startswith('magnet:'))
        stream['fileIdx']=2
        self.assertEqual(catalog.torrent_link(stream,'http://localhost:11470'),('http://localhost:11470/'+'a'*40+'/2',False))
        self.assertTrue(catalog.torrent_link(stream,'http://secret:token@server')[1])
    def test_config_required_does_not_query_subtitle_resource(self):
        m={'id':'opensubtitles','resources':['subtitles'],'types':['movie'],'behaviorHints':{'configurationRequired':True}}
        with patch.object(catalog,'manifest',return_value=m),patch.object(catalog,'get_json') as get:
            tracks,config=catalog.subtitles(['https://example.com/manifest.json'],{'type':'movie','id':'tt123'})
            self.assertEqual(tracks,[]);self.assertTrue(config);get.assert_not_called()
    def test_ptbr_sort_and_episode_identifier(self):
        m={'id':'sub','resources':['subtitles'],'types':['series']}
        with patch.object(catalog,'manifest',return_value=m),patch.object(catalog,'get_json',return_value={'subtitles':[{'lang':'eng','url':'https://x/a.srt'},{'lang':'pob','url':'https://x/b.srt'}]}) as get:
            tracks,_=catalog.subtitles(['https://example.com/manifest.json'],{'type':'series','id':'tt123','_episode':'tt123:2:4'})
            self.assertEqual(tracks[0]['lang'],'pob');self.assertIn('tt123:2:4.json',get.call_args.args[0])
    def test_external_torrent_is_not_probed_as_http(self):
        with patch.object(catalog.request,'urlopen') as network:
            self.assertIsNone(catalog.probe({'_url':'magnet:?xt=urn:btih:'+'a'*40,'_external':True}));network.assert_not_called()

if __name__=='__main__':unittest.main()
