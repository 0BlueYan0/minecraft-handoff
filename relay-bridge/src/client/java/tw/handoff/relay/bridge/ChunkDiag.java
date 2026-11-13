package tw.handoff.relay.bridge;
import java.util.concurrent.atomic.AtomicInteger;

/** 跟 viewer 的 [handoff-diag] 對照：伺服器送來幾個區塊封包、有幾個在 viewer 連著時轉出去、viewer 的移動寫到上游幾次。 */
final class ChunkDiag {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("minecraft-handoff-relay");
 final AtomicInteger chunks=new AtomicInteger(),chunksToViewer=new AtomicInteger(),centers=new AtomicInteger(),forgets=new AtomicInteger(),moves=new AtomicInteger();
 private int ticks;private String last="";
 /** Netty thread: name is the clientbound packet path, toViewer is whether a live viewer gets this frame. */
 void received(String name,java.util.function.BooleanSupplier toViewer){
  if(name==null)return;
  switch(name){
   case "level_chunk_with_light"->{chunks.incrementAndGet();if(toViewer.getAsBoolean())chunksToViewer.incrementAndGet();}
   case "set_chunk_cache_center"->centers.incrementAndGet();
   case "forget_level_chunk"->forgets.incrementAndGet();
   default->{}
  }
 }
 /** No viewer connected: drop the counts so the first line after a viewer joins starts from zero. */
 void reset(){chunks.set(0);chunksToViewer.set(0);centers.set(0);forgets.set(0);moves.set(0);ticks=0;last="";}
 /** Returns the line it logged, or null when nothing changed since the last one. */
 String tick(int chunkX,int chunkZ,int liveViewers){
  if(++ticks%100!=0)return null;
  int c=chunks.getAndSet(0),v=chunksToViewer.getAndSet(0),ce=centers.getAndSet(0),f=forgets.getAndSet(0),m=moves.getAndSet(0);
  String state="player="+chunkX+","+chunkZ+" live="+liveViewers;
  if(c==0&&ce==0&&f==0&&m==0&&state.equals(last))return null;
  last=state;
  String line="[handoff-diag] host "+state+" chunks="+c+" chunksToViewer="+v+" centers="+ce+" forgets="+f+" viewerMoves="+m;
  LOG.info(line);return line;
 }
}
