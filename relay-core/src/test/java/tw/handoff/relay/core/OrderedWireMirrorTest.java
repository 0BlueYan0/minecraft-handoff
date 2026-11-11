package tw.handoff.relay.core;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OrderedWireMirrorTest {
 @Test void bundleDelimitersOrdinaryAndOpaqueFramesKeepOneWireOrder(){
  var out=new ArrayList<byte[]>();var journal=new PacketJournal(8,1000);var mirror=new OrderedWireMirror(2,id->true,journal,out::add);
  byte[] delimiter={0},a={1,11},b=OpaqueFrames.payload(2,"some_new_mod:event",new byte[]{-1,0}),c={1,22};
  for(byte[] frame:List.of(delimiter,a,b,c,delimiter))mirror.receive(frame);
  assertEquals(5,out.size());assertArrayEquals(a,out.get(1));assertArrayEquals(b,out.get(2));assertArrayEquals(c,out.get(3));assertEquals(1,journal.snapshot().size());
 }
 @Test void connectionOwnedFramesAndAuthPayloadsNeverEnterMirrorOrHistory(){var out=new ArrayList<byte[]>();var journal=new PacketJournal(8,1000);var mirror=new OrderedWireMirror(2,id->id!=5,journal,out::add);mirror.receive(new byte[]{5});mirror.receive(OpaqueFrames.payload(2,"launcher:validation",new byte[]{1}));assertTrue(out.isEmpty());assertTrue(journal.snapshot().isEmpty());}
}
