package tw.handoff.relay.core;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class JoinGateTest {
 @Test void gateNeedsAckRttAndNoMissing(){
  var g=new JoinGate();g.viewerAck();
  assertEquals("latency",g.blocker());assertFalse(g.mayGrant());
  g.pong(g.nonce(),1_000_000_000L);
  assertTrue(g.mayGrant());assertNull(g.blocker());
  g.pong(g.nonce(),1_600_000_000L);
  assertEquals("latency",g.blocker());
 }
 @Test void hostMissingBlocksEvenAfterAck(){
  var g=new JoinGate();g.hostMissing(Set.of("saoblessing"));g.viewerAck();g.pong(g.nonce(),1_000_000L);
  assertFalse(g.mayGrant());assertEquals("saoblessing",g.blocker());
 }
 @Test void rttExactlyAtLimitIsAccepted(){
  var g=new JoinGate();g.viewerAck();g.pong(g.nonce(),1_500_000_000L);
  assertTrue(g.mayGrant());
  g.pong(g.nonce(),1_500_000_001L);
  assertEquals("latency",g.blocker());
 }
 @Test void clearingHostMissingAllowsGrant(){
  var g=new JoinGate();g.hostMissing(Set.of("snapshot"));g.viewerAck();g.pong(g.nonce(),1_000_000L);
  assertEquals("snapshot",g.blocker());
  g.hostMissing(Set.of());
  assertTrue(g.mayGrant());assertNull(g.blocker());
 }
 @Test void viewerFailureWins(){
  var g=new JoinGate();g.hostMissing(Set.of("saoblessing"));g.viewerAck();g.pong(g.nonce(),1_000_000L);
  g.viewerFailed("version");
  assertFalse(g.mayGrant());assertEquals("version",g.blocker());
 }
 @Test void wrongNonceIgnored(){
  var g=new JoinGate();g.viewerAck();g.pong("other",1_000_000L);
  assertFalse(g.mayGrant());assertEquals("latency",g.blocker());
 }
 @Test void takeBeforeAckIsRefusedWithBlocker(){
  var g=new JoinGate();g.pong(g.nonce(),1_000_000L);
  assertFalse(g.mayGrant());assertEquals("pending",g.blocker());
 }
}
