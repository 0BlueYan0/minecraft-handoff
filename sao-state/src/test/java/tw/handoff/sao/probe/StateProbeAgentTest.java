package tw.handoff.sao.probe;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** detail() goes into the status line the user sees; it must never carry quoted text, paths or non-ASCII game strings. */
class StateProbeAgentTest {
 @Test void detailKeepsOnlySafeCharactersAndIsBounded(){
  assertEquals("",StateProbeAgent.detail(new IllegalStateException()));
  assertEquals("",StateProbeAgent.detail(new IllegalStateException("  ")));
  assertEquals(":quests.x: Invalid number",StateProbeAgent.detail(new IllegalArgumentException("quests.x: Invalid number")));
  assertEquals(":a?b?c?d?e?f",StateProbeAgent.detail(new IllegalArgumentException("a\"b\\c\nd劍e/f")));
  assertEquals(301,StateProbeAgent.detail(new IllegalArgumentException("x".repeat(500))).length());
 }
}
