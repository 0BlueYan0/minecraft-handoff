package tw.handoff.relay.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChunkDiagTest {
 private static String after100(ChunkDiag d,int x,int z,int live){String line=null;for(int i=0;i<100;i++)line=d.tick(x,z,live);return line;}
 @Test void countsChunkPacketsAndOnlyLogsOnChange(){
  var d=new ChunkDiag();
  d.received("level_chunk_with_light",()->true);d.received("level_chunk_with_light",()->false);d.received("set_chunk_cache_center",()->true);d.received("system_chat",()->{throw new AssertionError("only chunk frames look up viewers");});d.received(null,()->true);
  d.moves.incrementAndGet();
  assertEquals("[handoff-diag] host player=1,2 live=1 chunks=2 chunksToViewer=1 centers=1 forgets=0 viewerMoves=1",after100(d,1,2,1));
  assertNull(after100(d,1,2,1));
  assertEquals("[handoff-diag] host player=3,2 live=1 chunks=0 chunksToViewer=0 centers=0 forgets=0 viewerMoves=0",after100(d,3,2,1));
 }
 @Test void resetDropsCountsFromBeforeAViewerJoined(){
  var d=new ChunkDiag();
  d.received("level_chunk_with_light",()->false);d.reset();
  assertEquals("[handoff-diag] host player=0,0 live=1 chunks=0 chunksToViewer=0 centers=0 forgets=0 viewerMoves=0",after100(d,0,0,1));
 }
}
