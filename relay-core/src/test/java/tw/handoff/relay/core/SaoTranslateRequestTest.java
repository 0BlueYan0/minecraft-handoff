package tw.handoff.relay.core;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SaoTranslateRequestTest {
 private static byte[] mux(String sub,String json){
  var out=new ByteArrayOutputStream();byte[] name=sub.getBytes(StandardCharsets.UTF_8);
  out.write(name.length);out.writeBytes(name);out.writeBytes(json.getBytes(StandardCharsets.UTF_8));return out.toByteArray();
 }
 // SAOUI 2.52.4 TranslateClient.ask 送的就是這個形狀。
 private static final String SYNC="{\"op\":\"SYNC\",\"data\":{\"p\":2,\"lang\":\"zh_tw\",\"v\":\"\"}}";

 @Test void translateOpsPass(){
  assertTrue(SaoTranslateRequest.allows("saocore:mux",mux("saocore:translate",SYNC)));
  assertTrue(SaoTranslateRequest.allows("saocore:mux",mux("saocore:translate","{\"op\":\"NAMES\",\"data\":{}}")));
  assertTrue(SaoTranslateRequest.allows("saocore:mux",mux("saocore:translate","{\"op\":\"MISSING\",\"data\":{\"n\":[]}}")));
 }
 @Test void otherOpsSubChannelsAndChannelsAreRefused(){
  assertFalse(SaoTranslateRequest.allows("saocore:mux",mux("saocore:translate","{\"op\":\"USE_SKILL\",\"data\":{}}")));
  assertFalse(SaoTranslateRequest.allows("saocore:mux",mux("saogui:main",SYNC)));
  assertFalse(SaoTranslateRequest.allows("saogui:main",mux("saocore:translate",SYNC)));
  assertFalse(SaoTranslateRequest.allows("saocore:mux",mux("saocore:translate","{\"op\":1}")));
  assertFalse(SaoTranslateRequest.allows("saocore:mux",mux("saocore:translate","[\"SYNC\"]")));
 }
 @Test void malformedAndOversizedAreRefused(){
  assertFalse(SaoTranslateRequest.allows("saocore:mux",null));
  assertFalse(SaoTranslateRequest.allows("saocore:mux",new byte[]{0}));
  assertFalse(SaoTranslateRequest.allows("saocore:mux",new byte[]{(byte)200,'a'}));
  assertFalse(SaoTranslateRequest.allows("saocore:mux",mux("saocore:translate","{not json")));
  String big="{\"op\":\"SYNC\",\"data\":{\"x\":\""+"a".repeat(SaoTranslateRequest.MAX_BYTES)+"\"}}";
  assertFalse(SaoTranslateRequest.allows("saocore:mux",mux("saocore:translate",big)));
 }
 @Test void wholeFrameIsInspected(){
  assertTrue(SaoTranslateRequest.allowsFrame(OpaqueFrames.payload(20,"saocore:mux",mux("saocore:translate",SYNC))));
  assertFalse(SaoTranslateRequest.allowsFrame(OpaqueFrames.payload(20,"saocore:mux",mux("saogui:main",SYNC))));
  assertFalse(SaoTranslateRequest.allowsFrame(new byte[]{1}));
 }
 @Test void languageEnvelopeRoundTrips(){
  String data=java.util.Base64.getEncoder().encodeToString(new byte[]{1,2,3});
  assertEquals(data,ControlWire.parseLanguage(ControlWire.language(data)));
  assertThrows(IllegalArgumentException.class,()->ControlWire.language(""));
  assertThrows(IllegalArgumentException.class,()->ControlWire.language("not base64!"));
  assertThrows(IllegalArgumentException.class,()->ControlWire.parseLanguage(ControlWire.PREFIX+"A:"+data));
 }
}
