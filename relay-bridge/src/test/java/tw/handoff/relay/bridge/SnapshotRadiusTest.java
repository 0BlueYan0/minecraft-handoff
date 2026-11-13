package tw.handoff.relay.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotRadiusTest {
 /** 2026-11-13 host: render distance 6, while the server sent 17 new chunks per step, so it tracked radius 8 around the player. */
 static boolean serverSquare(int x,int z){return Math.abs(x)<=8&&Math.abs(z)<=8;}
 @Test void everyChunkTheHostHoldsIsSentNotOnlyTheRenderDistance(){
  var held=SnapshotPackets.held(SnapshotRadiusTest::serverSquare);
  assertEquals(17*17,held.size());
  int farthest=Math.max(Math.abs(held.getLast()[0]),Math.abs(held.getLast()[1]));
  assertEquals(8,farthest);
  assertEquals(8,SnapshotPackets.radius(6,farthest));
 }
 @Test void nearestChunksGoFirstSoABudgetCutDropsTheFarthest(){
  var held=SnapshotPackets.held(SnapshotRadiusTest::serverSquare);
  assertArrayEquals(new int[]{0,0},held.getFirst());
  int last=0;for(int[] o:held){int d=Math.max(Math.abs(o[0]),Math.abs(o[1]));assertTrue(d>=last);last=d;}
 }
 @Test void radiusKeepsTheRenderDistanceAndTheClientLimits(){
  assertEquals(6,SnapshotPackets.radius(6,4));
  assertEquals(2,SnapshotPackets.radius(0,0));
  assertEquals(32,SnapshotPackets.radius(12,35));
 }
 @Test void chunksOutsideTheScanAreNotLookedUp(){
  assertEquals(0,SnapshotPackets.held((x,z)->Math.abs(x)>SnapshotPackets.SCAN||Math.abs(z)>SnapshotPackets.SCAN).size());
 }
}
