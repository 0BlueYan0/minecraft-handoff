package tw.handoff.relay.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ControlLeaseTest {
 @Test void onlyReadyOwnerAndCurrentEpochWithIncreasingSequenceMayAct(){
  var lease=new ControlLease<String>();
  assertEquals(0,lease.grant("A",false));
  long a=lease.grant("A",true);
  assertTrue(lease.accept("A",a,1));assertFalse(lease.accept("A",a,1));
  long b=lease.grant("B",true);
  assertFalse(lease.accept("A",a,2));assertFalse(lease.accept("B",a,3));
  assertTrue(lease.accept("B",b,1));
  lease.release("A");assertTrue(lease.ownedBy("B"));
  lease.release("B");assertFalse(lease.accept("B",b,2));
  long again=lease.grant("B",true);assertTrue(again>b);assertFalse(lease.accept("B",b,3));
 }
 @Test void invalidSequencesCannotWrap(){var l=new ControlLease<String>();long e=l.grant("A",true);assertFalse(l.accept("A",e,-1));assertFalse(l.accept("A",e,0));}
 @Test void duplicateLoadedNotificationCannotStealAnotherViewersLease(){
  var state=new PeerLifecycle();var lease=new ControlLease<String>();
  assertFalse(state.claimReadyNotification());state.handshake();state.hello();state.loginAcknowledged();state.packsSelected();state.configurationFinished();state.snapshotSent();state.playerLoaded();
  if(state.claimReadyNotification())lease.grant("A",true);
  lease.grant("B",true);state.playerLoaded();
  if(state.claimReadyNotification())lease.grant("A",true);
  assertTrue(lease.ownedBy("B"));
 }
}
