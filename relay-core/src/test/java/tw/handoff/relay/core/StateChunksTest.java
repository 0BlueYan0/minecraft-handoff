package tw.handoff.relay.core;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
class StateChunksTest {
 private static final String FULL="A".repeat(ControlWire.MAX_CHUNK_CHARS);
 private static String s(int index,int total,String data){return ControlWire.PREFIX+"S:"+index+":"+total+":"+data;}
 @Test void chunksRoundTripTwoMegabytes(){
  byte[] doc=new byte[2*1024*1024];new Random(7).nextBytes(doc);
  var chunks=StateChunks.encode(doc);
  int b64=((doc.length+2)/3)*4;
  assertEquals((b64+23999)/24000,chunks.size());
  var r=new StateChunks.Reassembler(2*1024*1024+64*1024);
  byte[] out=null;
  for(int i=0;i<chunks.size();i++){var got=r.accept(chunks.get(i));if(i<chunks.size()-1)assertTrue(got.isEmpty());else out=got.orElseThrow();}
  assertArrayEquals(doc,out);
 }
 @Test void reassemblerRejectsGapsAndOverflow(){
  assertThrows(IllegalArgumentException.class,()->new StateChunks.Reassembler(100000).accept(s(1,3,FULL)));
  var gap=new StateChunks.Reassembler(100000);gap.accept(s(0,3,FULL));
  assertThrows(IllegalArgumentException.class,()->gap.accept(s(2,3,FULL)));
  var total=new StateChunks.Reassembler(100000);total.accept(s(0,3,FULL));
  assertThrows(IllegalArgumentException.class,()->total.accept(s(1,4,FULL)));
  assertThrows(IllegalArgumentException.class,()->new StateChunks.Reassembler(100000).accept(s(0,201,FULL)));
  var big=new StateChunks.Reassembler(10);
  assertThrows(IllegalArgumentException.class,()->StateChunks.encode(new byte[20]).forEach(big::accept));
 }
 @Test void nonFinalChunkMustBeFullLength(){
  assertThrows(IllegalArgumentException.class,()->new StateChunks.Reassembler(100000).accept(s(0,2,"YQ==")));
  assertThrows(IllegalArgumentException.class,()->new StateChunks.Reassembler(100000).accept(s(0,2,FULL.substring(0,23996)+"YQ==")));
  var ok=new StateChunks.Reassembler(100000);
  assertTrue(ok.accept(s(0,2,FULL)).isEmpty());
  assertEquals(18001,ok.accept(s(1,2,"YQ==")).orElseThrow().length);
 }
 @Test void pingPongRoundTripAndRejectBadNonce(){
  assertEquals("abc123",ControlWire.parsePing(ControlWire.ping("abc123")));
  assertEquals("abc123",ControlWire.parsePong(ControlWire.pong("abc123")));
  assertThrows(IllegalArgumentException.class,()->ControlWire.ping("XYZ"));
  assertThrows(IllegalArgumentException.class,()->ControlWire.parsePing(ControlWire.pong("abc")));
  assertThrows(IllegalArgumentException.class,()->ControlWire.parsePong(ControlWire.PREFIX+"q:"));
  assertThrows(IllegalArgumentException.class,()->ControlWire.parsePing(null));
 }
 @Test void missingFailedAckRoundTripAndRejectBadPiece(){
  assertEquals("saoblessing",ControlWire.parseMissing(ControlWire.missing("saoblessing")));
  assertEquals("replay-budget",ControlWire.parseFailed(ControlWire.failed("replay-budget")));
  assertEquals(ControlWire.PREFIX+"K",ControlWire.ack());
  assertTrue(ControlWire.isAck(ControlWire.ack()));
  assertFalse(ControlWire.isAck(ControlWire.ack()+"x"));
  assertThrows(IllegalArgumentException.class,()->ControlWire.missing("Bad Piece"));
  assertThrows(IllegalArgumentException.class,()->ControlWire.failed(""));
  assertThrows(IllegalArgumentException.class,()->ControlWire.parseMissing(ControlWire.failed("version")));
  assertThrows(IllegalArgumentException.class,()->ControlWire.parseFailed(ControlWire.PREFIX+"N:a:b"));
 }
}
