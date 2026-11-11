package tw.handoff.relay.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PacketJournalTest {
 @Test void orderedHistoryOwnsItsBytes(){var j=new PacketJournal(3,10);byte[] a={1,2};j.append(a);a[0]=9;j.append(new byte[]{3});var s=j.snapshot();assertArrayEquals(new byte[]{1,2},s.get(0));assertArrayEquals(new byte[]{3},s.get(1));s.get(0)[0]=8;assertEquals(1,j.snapshot().get(0)[0]);}
 @Test void overflowRefusesCatchupInsteadOfReturningTruncatedHistory(){var j=new PacketJournal(1,10);j.append(new byte[]{1});assertFalse(j.append(new byte[]{2}));assertThrows(IllegalStateException.class,j::snapshot);var k=new PacketJournal(3,2);assertFalse(k.append(new byte[3]));assertThrows(IllegalStateException.class,k::snapshot);}
}
