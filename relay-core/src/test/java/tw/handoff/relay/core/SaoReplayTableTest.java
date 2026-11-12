package tw.handoff.relay.core;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class SaoReplayTableTest {
 private static final int ID=0x18;
 private static byte[] utf8(String s){return s.getBytes(StandardCharsets.UTF_8);}
 private static byte[] direct(String channel,byte[] data){return OpaqueFrames.payload(ID,channel,data);}
 private static byte[] muxData(String sub,byte[] content){var out=new ByteArrayOutputStream();byte[] name=utf8(sub);out.write(name.length);out.writeBytes(name);out.writeBytes(content);return out.toByteArray();}
 private static byte[] mux(String sub,byte[] content){return direct("saocore:mux",muxData(sub,content));}
 private static byte[] json(String op,String extra){return utf8("{\"op\":\""+op+"\""+extra+"}");}
 private static byte[] gui(String op,String extra){return mux("saogui:main",json(op,extra));}
 private static byte[] questData(int type,String hudId,String extra){var out=new ByteArrayOutputStream();out.write(type);byte[] id=utf8(hudId);out.write(id.length);out.writeBytes(id);out.writeBytes(utf8(extra));return out.toByteArray();}
 private static byte[] questMux(int type,String hudId,String extra){return mux("capuodev:quest_hud",questData(type,hudId,extra));}
 private static byte[] questDirect(int type,String hudId,String extra){return direct("capuodev:quest_hud",questData(type,hudId,extra));}
 private static byte[] blessing(String op,String extra){return direct("saoblessing:main",json(op,extra));}
 private static void same(List<byte[]> actual,byte[]... expected){assertEquals(expected.length,actual.size());for(int i=0;i<expected.length;i++)assertArrayEquals(expected[i],actual.get(i),"frame "+i);}

 @Test void latestFramePerKeyInArrivalOrder(){
  var t=new SaoReplayTable(ID,1<<20);
  byte[] init=gui("INIT",",\"n\":1"),mana=gui("MANA_UPDATE",",\"n\":2"),use=gui("USE_SKILL_OK",",\"n\":3");
  t.observe(init);t.observe(mana);t.observe(use);
  same(t.replay().frames(),init,mana,use);
  byte[] init2=gui("INIT",",\"n\":4");
  t.observe(init2);
  same(t.replay().frames(),init2);
 }
 @Test void questHudRemoveSingleAndAll(){
  var t=new SaoReplayTable(ID,1<<20);
  byte[] a=questMux(0,"a","x"),b=questMux(0,"b","y");
  t.observe(a);t.observe(b);
  same(t.replay().frames(),a,b);
  t.observe(questMux(1,"b",""));
  same(t.replay().frames(),a);
  t.observe(b);
  t.observe(questMux(1,"*",""));
  same(t.replay().frames());
 }
 @Test void questHudDirectAndMuxShareKey(){
  var t=new SaoReplayTable(ID,1<<20);
  byte[] direct=questDirect(0,"a","v1"),mux=questMux(0,"a","v2");
  t.observe(direct);t.observe(mux);
  same(t.replay().frames(),mux);
 }
 @Test void blessingKeepsSessionLogAndResets(){
  var t=new SaoReplayTable(ID,1<<20);
  byte[] start=blessing("SESSION_START",""),c1=blessing("CHARGE_UPDATE",",\"value\":1"),c2=blessing("CHARGE_UPDATE",",\"max\":9");
  t.observe(start);t.observe(c1);t.observe(c2);
  same(t.replay().frames(),start,c1,c2);
  byte[] end=blessing("SESSION_END","");
  t.observe(end);
  same(t.replay().frames(),end);
 }
 @Test void blessingOverflowMarksMissingUntilSession(){
  var t=new SaoReplayTable(ID,1<<24);
  for(int i=0;i<257;i++)t.observe(blessing("CHARGE_UPDATE",",\"value\":"+i));
  assertTrue(t.replay().missing().contains("saoblessing"));
  byte[] start=blessing("SESSION_START","");
  t.observe(start);
  var r=t.replay();
  assertFalse(r.missing().contains("saoblessing"));
  same(r.frames(),start);
 }
 @Test void excludedOpsAreNotStored(){
  var t=new SaoReplayTable(ID,1<<20);
  t.observe(gui("ITEM_SKILL_COOLDOWN",""));
  t.observe(gui("ASK",""));
  t.observe(gui("SERVER_HELLO",""));
  t.observe(gui("DISCOVERY_REGION",""));
  t.observe(gui("MOB_HB_TICK",""));
  t.observe(blessing("SERVER_HELLO",""));
  var r=t.replay();
  same(r.frames());
  assertTrue(r.missing().isEmpty());
 }
 @Test void budgetOverflowIsPermanent(){
  var t=new SaoReplayTable(ID,64);
  byte[] init=gui("INIT",""),settings=gui("SETTINGS","");
  assertTrue(init.length<64&&settings.length<64&&init.length+settings.length>64);
  t.observe(init);
  same(t.replay().frames(),init);
  assertTrue(t.replay().missing().isEmpty());
  t.observe(settings);
  var r=t.replay();
  assertTrue(r.missing().contains("replay-budget"));
  same(r.frames());
  t.observe(questMux(0,"a",""));
  r=t.replay();
  assertTrue(r.missing().contains("replay-budget"));
  same(r.frames());
 }
 @Test void malformedFramesIgnored(){
  var t=new SaoReplayTable(ID,1<<20);
  byte[] ok=gui("INIT","");
  t.observe(ok);
  t.observe(direct("saocore:mux",new byte[]{(byte)200,'a','b'}));
  t.observe(direct("saocore:mux",new byte[0]));
  t.observe(mux("saogui:main",utf8("{not json")));
  t.observe(mux("saogui:main",utf8("[1,2]")));
  t.observe(direct("saoblessing:main",utf8("{not json")));
  t.observe(direct("capuodev:quest_hud",new byte[]{0,(byte)100,'a'}));
  t.observe(direct("capuodev:quest_hud",new byte[0]));
  t.observe(new byte[]{(byte)0xff});
  t.observe(OpaqueFrames.payload(ID+1,"saogui:main",json("SETTINGS","")));
  same(t.replay().frames(),ok);
  assertEquals(java.util.Set.of("replay-skipped"),t.replay().missing());
 }
 @Test void unparsableHandledFramesMarkReplaySkipped(){
  byte[][] bad={
   direct("saocore:mux",new byte[]{(byte)200,'a','b'}),
   direct("saocore:mux",new byte[0]),
   mux("saogui:main",utf8("{not json")),
   mux("saogui:main",utf8("[1,2]")),
   mux("saogui:main",utf8("{\"op\":5}")),
   mux("capuodev:quest_hud",new byte[]{0,(byte)100,'a'}),
   direct("saoblessing:main",utf8("{not json")),
   direct("saoblessing:main",utf8("{\"op\":5}")),
   direct("capuodev:quest_hud",new byte[]{0,(byte)100,'a'}),
   direct("capuodev:quest_hud",new byte[0])};
  for(int i=0;i<bad.length;i++){
   var t=new SaoReplayTable(ID,1<<20);
   t.observe(bad[i]);
   assertTrue(t.replay().missing().contains("replay-skipped"),"frame "+i);
  }
 }
 @Test void skippedStaysAfterLaterGoodFrames(){
  var t=new SaoReplayTable(ID,1<<20);
  t.observe(mux("saogui:main",utf8("{not json")));
  t.observe(gui("INIT",""));
  assertTrue(t.replay().missing().contains("replay-skipped"));
 }
 @Test void hostCanMarkAFrameItCouldNotRecord(){
  var t=new SaoReplayTable(ID,1<<20);
  assertTrue(t.replay().missing().isEmpty());
  t.markSkipped();
  assertTrue(t.replay().missing().contains("replay-skipped"));
 }
 @Test void framesTheTableDoesNotHandleNeverMarkSkipped(){
  var t=new SaoReplayTable(ID,1<<20);
  t.observe(mux("saogui:other",utf8("{not json")));
  t.observe(mux("someother:sub",utf8("garbage")));
  t.observe(mux(":unknown",utf8("garbage")));
  t.observe(direct("saocore:mux",new byte[]{0,'x'}));
  t.observe(gui("ITEM_SKILL_COOLDOWN",""));
  t.observe(gui("ASK",""));
  t.observe(gui("SERVER_HELLO",""));
  t.observe(questMux(2,"a",""));
  t.observe(questDirect(2,"a",""));
  t.observe(blessing("SERVER_HELLO",""));
  t.observe(direct("some:other_channel",utf8("{not json")));
  t.observe(OpaqueFrames.payload(ID+1,"saocore:mux",new byte[]{(byte)200}));
  t.observe(new byte[]{(byte)0xff});
  assertTrue(t.replay().missing().isEmpty(),t.replay().missing().toString());
 }
}
