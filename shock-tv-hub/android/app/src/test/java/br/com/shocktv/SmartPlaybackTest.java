package br.com.shocktv;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONObject;
public class SmartPlaybackTest {
    @Test public void torrentRequiresEngineAndExplicitFile()throws Exception{
        JSONObject s=new JSONObject().put("infoHash","0123456789abcdef0123456789abcdef01234567");
        assertTrue(SmartPlayback.isTorrent(SmartPlayback.magnet(s)));
        s.put("fileIdx",3);
        assertTrue(SmartPlayback.magnet(s).contains("&so=3"));
        assertTrue(SmartPlayback.removedAddon("https://opensubtitlesv3-pro.dexter21767.com/secret/manifest.json"));
    }
    @Test public void magnetNeverBecomesHttp(){
        assertFalse(SmartPlayback.http("magnet:?xt=urn:btih:abcdef"));
        assertFalse(SmartPlayback.http("file:///sdcard/test.mp4"));
        assertTrue(SmartPlayback.http("https://cdn.example/stream?id=2"));
    }
    @Test public void ptBrHasPriority(){assertTrue(SmartPlayback.subtitleRank("pob")<SmartPlayback.subtitleRank("por"));assertTrue(SmartPlayback.subtitleRank("pt-BR")<SmartPlayback.subtitleRank("eng"));}
    @Test public void timelineHandlesDelayAndSeek(){
        SubtitleTimeline s=SubtitleTimeline.parse("1\n00:00:01,000 --> 00:00:02,500\nOlá\n\n2\n00:00:04,000 --> 00:00:05,000\nOutro\n");
        assertEquals("Olá",s.at(1200,0));assertEquals("",s.at(1200,500));
        assertEquals("Olá",s.at(1700,500));assertEquals("Outro",s.at(4500,0));assertEquals("",s.at(3000,0));assertEquals("Olá",s.at(1100,0));
    }
    @Test public void vttAndInvalidCues(){
        SubtitleTimeline s=SubtitleTimeline.parse("WEBVTT\n\n00:01.000 --> 00:03.000 align:center\n<b>Teste</b>\n\nbad --> time\nIgnored\n");
        assertEquals(1,s.cues.size());assertEquals("Teste",s.at(2000,0));
    }
}
