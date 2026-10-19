package br.com.shocktv;
import org.junit.Test;
import static org.junit.Assert.*;
public class TorrentPolicyTest {
 @Test public void ranges()throws Exception{
  assertArrayEquals(new long[]{90,99,1},TorrentEngine.range("bytes=-10",100));
  assertArrayEquals(new long[]{4,9,1},TorrentEngine.range("bytes=4-9",100));
  try{TorrentEngine.range("bytes=100-",100);fail();}catch(java.io.IOException expected){}
 }
 @Test public void episodesAreNotGuessed()throws Exception{
  String[] files={"episode1.mp4","episode2.mp4"};long[] sizes={100,100};
  try{TorrentEngine.chooseFile(files,sizes,-1);fail();}catch(java.io.IOException expected){}
  assertEquals(1,TorrentEngine.chooseFile(files,sizes,1));
 }
}
