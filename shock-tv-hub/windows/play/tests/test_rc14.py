import pathlib,sys,unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import catalog
from torrent_engine import byte_range,select_file

class RC14Tests(unittest.TestCase):
    def test_no_opensubtitles_default(self):
        self.assertFalse(any(catalog.removed_addon(u) for u in catalog.DEFAULT))
        self.assertTrue(catalog.removed_addon('https://opensubtitlesv3-pro.dexter21767.com/private/manifest.json'))
    def test_native_magnet_keeps_episode_index(self):
        value=catalog.torrent_link({'infoHash':'a'*40,'fileIdx':3})
        self.assertTrue(value.startswith('magnet:?'));self.assertIn('&so=3',value)
    def test_ranges(self):
        self.assertEqual(byte_range('bytes=20-29',100),(20,29,True))
        self.assertEqual(byte_range('bytes=-10',100),(90,99,True))
        self.assertEqual(byte_range('bytes=90-',100),(90,99,True))
        for value in ('bytes=101-','bytes=9-3','bytes=-','bytes=0-1,4-5'):
            with self.assertRaises(ValueError):byte_range(value,100)
    def test_does_not_guess_episode_in_multifile_torrent(self):
        class Files:
            def num_files(self):return 2
            def file_path(self,i):return ['show/episode1.mp4','show/episode2.mp4'][i]
        with self.assertRaises(ValueError):select_file(Files())
        self.assertEqual(select_file(Files(),1),1)
