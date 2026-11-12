package tw.handoff.relay.bridge;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import tw.handoff.relay.core.*;

/** 常駐端的 viewer 加入流程。不碰遊戲類別，輸入全是函式介面，遊戲執行緒上整段只跑一次。 */
final class JoinState {
 interface Step{void run()throws Exception;}
 interface Capture{JsonObject capture(long now)throws Exception;}
 record Ports(Step world,Supplier<Collection<String>> channels,Consumer<byte[]> frame,Consumer<String> message,Capture capture,LongSupplier clock,Runnable live){}

 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("minecraft-handoff-relay");
 private final JoinGate gate;private final SaoReplayTable table;private final int payloadId;private final Ports ports;
 private long probeSentAt;private boolean probed;

 JoinState(JoinGate gate,SaoReplayTable table,int customPayloadPacketId,Ports ports){this.gate=gate;this.table=table;payloadId=customPayloadPacketId;this.ports=ports;}

 /** 整段流程排成一個遊戲執行緒工作，表快照與欄位快照因此取在同一次 run 內。 */
 void start(Executor game,BooleanSupplier proceed,Consumer<Throwable> failed,Runnable finished){
  game.execute(()->{
   try{if(proceed.getAsBoolean())run();}
   catch(Exception|LinkageError failure){failed.accept(failure);}
   finally{finished.run();}
  });
 }

 void run()throws Exception{
  ports.world().run();
  ports.frame().accept(OpaqueFrames.payload(payloadId,"minecraft:register",register(ports.channels().get())));
  var replay=table.replay();
  for(byte[] frame:replay.frames())ports.frame().accept(frame);
  var missing=new LinkedHashSet<String>(replay.missing());
  for(String piece:replay.missing())ports.message().accept(ControlWire.missing(piece));
  long capturedAt=ports.clock().getAsLong();
  JsonObject snapshot=null;
  try{snapshot=ports.capture().capture(capturedAt);}
  catch(Exception|LinkageError failure){missing.add("version");ports.message().accept(ControlWire.missing("version"));}
  gate.hostMissing(missing);
  if(snapshot!=null){
   var document=new JsonObject();
   document.addProperty("ageNanos",ports.clock().getAsLong()-capturedAt);
   document.add("snapshot",snapshot);
   for(String chunk:StateChunks.encode(document.toString().getBytes(StandardCharsets.UTF_8)))ports.message().accept(chunk);
  }
  ports.live().run();
 }

 /** 往返時間探測。viewer 在世界載入完成前回不了 pong，所以由 RemoteControl.ready 在送 R:0 的同一刻呼叫，不在 run 裡送。送出時間在這時才記下。 */
 void probe(){
  probeSentAt=ports.clock().getAsLong();probed=true;
  ports.message().accept(ControlWire.ping(gate.nonce()));
 }

 /** viewer ACTIVE 時的流程：先送 Q，再送 R:0。順序反過來時 viewer 收到 R: 會先送 K，常駐端先看到 K 還沒有 pong，閘門回 latency。 */
 void ready(Runnable sendReady){
  probe();
  sendReady.run();
 }

 private static byte[] register(Collection<String> names){
  List<String> sorted=new ArrayList<>(names);java.util.Collections.sort(sorted);
  return String.join("\0",sorted).getBytes(StandardCharsets.UTF_8);
 }

 /** viewer 送來的加入相關訊息。回傳 true 表示已處理。grant 只在閘門放行時呼叫。 */
 boolean handle(String text,Runnable grant){
  if(text.startsWith(ControlWire.PREFIX+"q:")){
   String nonce=ControlWire.parsePong(text);
   if(probed){long rtt=ports.clock().getAsLong()-probeSentAt;gate.pong(nonce,rtt);if(nonce.equals(gate.nonce()))LOG.info("Join round trip {} ms",rtt/1_000_000);}
   return true;
  }
  if(ControlWire.isAck(text)){gate.viewerAck();grantOrRefuse(grant);return true;}
  if(text.startsWith(ControlWire.PREFIX+"N:")){gate.viewerFailed(ControlWire.parseFailed(text));return true;}
  if(text.equals(ControlWire.PREFIX+"TAKE")){grantOrRefuse(grant);return true;}
  return false;
 }

 private void grantOrRefuse(Runnable grant){
  if(gate.mayGrant())grant.run();
  else ports.message().accept(ControlWire.PREFIX+"E:"+gate.blocker());
 }

}
