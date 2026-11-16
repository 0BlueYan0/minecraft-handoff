package tw.handoff.relay.core;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ZapAudioUrlTest {
 static final String BASE="https%3A%2F%2Faudio.example.test%2Faudio";
 @Test void localPageGoesBackToTheServerOriginWithoutTheHostLanguage(){
  String host="file:///Users/x/.zapaudio-web-cache/v-1/index.html?player=p&token=t&audioBaseUrl="+BASE+"&zapLang=en_us";
  assertEquals(Optional.of("https://audio.example.test?player=p&token=t&audioBaseUrl="+BASE),ZapAudioUrl.forViewer(host));
 }
 @Test void languageInTheMiddleIsDroppedToo(){
  String host="file:///C:/c/index.html?player=p&zapLang=zh_tw&audioBaseUrl="+BASE;
  assertEquals(Optional.of("https://audio.example.test?player=p&audioBaseUrl="+BASE),ZapAudioUrl.forViewer(host));
 }
 @Test void serverUrlIsSentAsIs(){
  assertEquals(Optional.of("https://audio.example.test?player=p"),ZapAudioUrl.forViewer("https://audio.example.test?player=p"));
 }
 @Test void nothingUsableGivesNothing(){
  assertEquals(Optional.empty(),ZapAudioUrl.forViewer(null));
  assertEquals(Optional.empty(),ZapAudioUrl.forViewer(""));
  assertEquals(Optional.empty(),ZapAudioUrl.forViewer("file:///c/index.html?player=p"));
  assertEquals(Optional.empty(),ZapAudioUrl.forViewer("file:///c/index.html"));
  assertEquals(Optional.empty(),ZapAudioUrl.forViewer("javascript:alert(1)"));
 }
 @Test void frameIsALengthPrefixedString(){
  byte[] frame=ZapAudioUrl.frame(7,"https://a.test?x=1");
  var view=OpaqueFrames.inspect(frame);
  assertEquals(7,view.packetId());assertEquals(ZapAudioUrl.CHANNEL,view.channel());
  int[] p={view.dataOffset()};int len=OpaqueFrames.readVarInt(frame,p);
  assertEquals("https://a.test?x=1",new String(frame,p[0],len,StandardCharsets.UTF_8));
  assertEquals(frame.length,p[0]+len);
 }
}
