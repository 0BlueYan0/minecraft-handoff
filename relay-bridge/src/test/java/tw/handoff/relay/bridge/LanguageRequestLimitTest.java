package tw.handoff.relay.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LanguageRequestLimitTest {
 @Test void fivePerTenSecondsThenTheWindowResets(){
  var peer=new NativePeer(null,null,null);long t=50_000_000_000L;
  for(int i=0;i<5;i++)assertTrue(peer.allowLanguageRequest(t+i));
  assertFalse(peer.allowLanguageRequest(t+9_000_000_000L));
  assertTrue(peer.allowLanguageRequest(t+10_500_000_000L));
 }
}
