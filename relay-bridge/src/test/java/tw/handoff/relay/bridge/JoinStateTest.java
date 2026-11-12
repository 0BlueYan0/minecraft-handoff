package tw.handoff.relay.bridge;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import tw.handoff.relay.core.*;

class JoinStateTest {
 private static final int ID=7;
 private final List<String> events=new ArrayList<>();
 private final JoinGate gate=new JoinGate();

 private static byte[] questFrame(String hud){
  byte[] id=hud.getBytes(StandardCharsets.UTF_8);
  byte[] data=new byte[2+id.length];data[0]=0;data[1]=(byte)id.length;System.arraycopy(id,0,data,2,id.length);
  return OpaqueFrames.payload(ID,"capuodev:quest_hud",data);
 }

 private JoinState state(SaoReplayTable table,JoinState.Capture capture,long... clock){
  int[] i={0};
  return new JoinState(gate,table,ID,new JoinState.Ports(
   ()->events.add("world"),
   ()->List.of("saogui:main","saoblessing:main"),
   f->events.add("frame:"+OpaqueFrames.inspect(f).channel()),
   m->events.add("msg:"+m.substring(ControlWire.PREFIX.length(),Math.min(m.length(),ControlWire.PREFIX.length()+2))),
   capture,
   ()->clock[Math.min(i[0]++,clock.length-1)],
   ()->events.add("live")));
 }

 @Test void orderIsWorldRegisterReplaySnapshotProbeLive()throws Exception{
  var table=new SaoReplayTable(ID,1<<20);table.observe(questFrame("a"));
  state(table,now->{var o=new JsonObject();o.addProperty("schema",2);return o;},100,200,300).run();
  assertEquals("world",events.get(0));
  assertEquals("frame:minecraft:register",events.get(1));
  assertEquals("frame:capuodev:quest_hud",events.get(2));
  assertEquals("msg:S:",events.get(3));
  assertEquals("live",events.get(4));
  assertEquals(5,events.size()); // The probe is no longer part of the join run; RemoteControl.ready sends it with R:0.
 }

 @Test void runSendsNoProbeAndProbeSendsOneAtTheCallersMoment()throws Exception{
  var sent=new ArrayList<String>();
  var s=new JoinState(gate,new SaoReplayTable(ID,1<<20),ID,new JoinState.Ports(()->{},()->List.of(),f->{},sent::add,now->new JsonObject(),clock(10,20,30,40),()->{}));
  s.run();
  assertTrue(sent.stream().noneMatch(m->m.startsWith(ControlWire.PREFIX+"Q:")),sent.toString());
  sent.clear();
  s.probe();
  assertEquals(List.of(ControlWire.ping(gate.nonce())),sent);
  // probeSentAt is the clock reading taken inside probe() (30), so a pong read at 40 gives 10 ns.
  assertTrue(s.handle(ControlWire.PREFIX+"q:"+gate.nonce(),()->{}));
  gate.viewerAck();
  assertEquals(null,gate.blocker());
 }

 @Test void pongBeforeProbeIsIgnored()throws Exception{
  var s=new JoinState(gate,new SaoReplayTable(ID,1<<20),ID,new JoinState.Ports(()->{},()->List.of(),f->{},m->{},now->new JsonObject(),clock(1),()->{}));
  assertTrue(s.handle(ControlWire.PREFIX+"q:"+gate.nonce(),()->{}));
  gate.viewerAck();
  assertEquals("latency",gate.blocker());
 }

 @Test void captureFailureSendsMissingVersionAndNoChunks()throws Exception{
  var table=new SaoReplayTable(ID,1<<20);
  state(table,now->{throw new IllegalStateException("contract");},1,2,3).run();
  assertTrue(events.contains("msg:M:"));
  assertTrue(events.stream().noneMatch(e->e.equals("msg:S:")));
  assertEquals("version",gate.blocker());
  assertEquals("live",events.get(events.size()-1));
 }

 @Test void skippedReplayFrameSendsMissingReplaySkippedAndBlocksGrant()throws Exception{
  var sent=new ArrayList<String>();
  var table=new SaoReplayTable(ID,1<<20);table.markSkipped();
  var s=new JoinState(gate,table,ID,new JoinState.Ports(()->{},()->List.of(),f->{},sent::add,now->new JsonObject(),clock(0,0,0,1),()->{}));
  s.run();s.probe();
  assertTrue(sent.contains(ControlWire.missing("replay-skipped")),sent.toString());
  assertNotNull(gate.blocker());
  assertEquals("replay-skipped",gate.blocker());
  gate.viewerAck();s.handle(ControlWire.PREFIX+"q:"+gate.nonce(),()->{});
  assertFalse(gate.mayGrant());
 }

 @Test void ageIsMeasuredOnHostClockOnly()throws Exception{
  var table=new SaoReplayTable(ID,1<<20);
  var sent=new ArrayList<String>();
  var s=new JoinState(gate,table,ID,new JoinState.Ports(()->{},()->List.of(),f->{},sent::add,
   now->{var o=new JsonObject();o.addProperty("capturedWith",now);return o;},
   clock(100,350,400),()->{}));
  s.run();
  var reassembler=new StateChunks.Reassembler(1<<20);byte[] doc=null;
  for(String m:sent)if(m.startsWith(ControlWire.PREFIX+"S:")){var r=reassembler.accept(m);if(r.isPresent())doc=r.get();}
  assertNotNull(doc);
  JsonObject json=JsonParser.parseString(new String(doc,StandardCharsets.UTF_8)).getAsJsonObject();
  assertEquals(250L,json.get("ageNanos").getAsLong());
  assertEquals(100L,json.getAsJsonObject("snapshot").get("capturedWith").getAsLong());
 }

 private static java.util.function.LongSupplier clock(long... values){int[] i={0};return ()->values[Math.min(i[0]++,values.length-1)];}

 @Test void tableAndFieldsCapturedInOneGameTask()throws Exception{
  var table=new SaoReplayTable(ID,1<<20);table.observe(questFrame("a"));
  var s=state(table,now->{events.add("capture@"+runs[0]);return new JsonObject();},1,2,3);
  Executor game=task->{runs[0]++;events.add("run"+runs[0]);task.run();};
  s.start(game,()->true,failure->fail(failure),()->events.add("finished"));
  assertEquals(1,runs[0]);
  int run=events.indexOf("run1"),replay=events.indexOf("frame:capuodev:quest_hud"),capture=events.indexOf("capture@1"),firstChunk=events.indexOf("msg:S:");
  assertTrue(run==0&&replay>run&&capture>run&&firstChunk>replay&&firstChunk>capture,events.toString());
  assertEquals("finished",events.get(events.size()-1));
 }
 private final int[] runs={0};

 @Test void failedNewViewerKeepsOldOwner()throws Exception{
  var lease=new ControlLease<String>();lease.grant("old",true);
  var sent=new ArrayList<String>();
  var s=new JoinState(gate,new SaoReplayTable(ID,1<<20),ID,new JoinState.Ports(()->{},()->List.of(),f->{},sent::add,now->new JsonObject(),clock(1),()->{}));
  Runnable grantNew=()->lease.grant("new",true);
  assertTrue(s.handle(ControlWire.PREFIX+"N:version",grantNew));
  assertTrue(s.handle(ControlWire.PREFIX+"K",grantNew));
  assertTrue(s.handle(ControlWire.PREFIX+"TAKE",grantNew));
  assertEquals("old",lease.owner());
  assertEquals(List.of(ControlWire.PREFIX+"E:version",ControlWire.PREFIX+"E:version"),sent);
 }

 @Test void grantOnlyAfterAckAndProbe()throws Exception{
  var lease=new ControlLease<String>();
  var sent=new ArrayList<String>();
  var s=new JoinState(gate,new SaoReplayTable(ID,1<<20),ID,new JoinState.Ports(()->{},()->List.of(),f->{},sent::add,now->new JsonObject(),clock(0,0,0,1_000_000),()->{}));
  Runnable grant=()->lease.grant("new",true);
  s.run();s.probe();sent.clear();
  assertTrue(s.handle(ControlWire.PREFIX+"TAKE",grant));
  assertNull(lease.owner());assertEquals(List.of(ControlWire.PREFIX+"E:latency"),sent);
  assertTrue(s.handle(ControlWire.PREFIX+"q:"+gate.nonce(),grant));
  sent.clear();
  assertTrue(s.handle(ControlWire.PREFIX+"TAKE",grant));
  assertNull(lease.owner());assertEquals(List.of(ControlWire.PREFIX+"E:pending"),sent);
  assertTrue(s.handle(ControlWire.PREFIX+"K",grant));
  assertEquals("new",lease.owner());
 }

 @Test void readySendsProbeBeforeReadyMessage()throws Exception{
  var sent=new ArrayList<String>();
  var s=new JoinState(gate,new SaoReplayTable(ID,1<<20),ID,new JoinState.Ports(()->{},()->List.of(),f->{},sent::add,now->new JsonObject(),clock(5),()->{}));
  s.ready(()->sent.add(ControlWire.PREFIX+"R:0"));
  assertEquals(List.of(ControlWire.ping(gate.nonce()),ControlWire.PREFIX+"R:0"),sent);
 }
}
