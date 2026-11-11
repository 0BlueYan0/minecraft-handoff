package tw.handoff.relay.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InputInboxTest {
 @Test void coalescesHeldFramesButPreservesOrderingAroundActions(){var q=new InputInbox(4);assertTrue(q.offer("I1",true));assertTrue(q.offer("I2",true));assertTrue(q.offer("A3",false));assertTrue(q.offer("I4",true));assertTrue(q.offer("I5",true));assertEquals(java.util.List.of("I2","A3","I5"),q.drain());assertTrue(q.drain().isEmpty());}
 @Test void refusesOverflowInsteadOfGrowingGameThreadQueue(){var q=new InputInbox(2);assertTrue(q.offer("A1",false));assertTrue(q.offer("A2",false));assertFalse(q.offer("A3",false));assertEquals(2,q.drain().size());}
}
