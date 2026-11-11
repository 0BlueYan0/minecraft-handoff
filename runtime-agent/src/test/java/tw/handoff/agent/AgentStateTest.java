package tw.handoff.agent;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class AgentStateTest {
    @Test void snapshotsAnExistingConnectionAndKeepsCountersDistinctFromAuth() {
        var lines = new ArrayList<String>();
        var state = new AgentState(() -> 0L, lines::add);
        state.start(true);
        assertTrue(lines.getFirst().contains("\"state\":\"ATTACHED_IN_GAME\""));
        assertTrue(lines.getFirst().contains("\"joins\":0"));
        assertFalse(lines.getFirst().contains("token"));
        state.disconnected();
        state.joined();
        assertTrue(lines.getLast().contains("\"joins\":1"));
        assertTrue(lines.getLast().contains("\"disconnects\":1"));
    }

    @Test void samplesAtSixtySecondsWithoutCatchupBursts() {
        var now = new AtomicLong();
        var lines = new ArrayList<String>();
        var state = new AgentState(now::get, lines::add);
        state.start(false);
        now.set(59_000_000_000L); state.tick();
        assertEquals(1, lines.size());
        now.set(60_000_000_000L); state.tick();
        assertEquals(2, lines.size());
        now.set(600_000_000_000L); state.tick(); state.tick();
        assertEquals(3, lines.size());
    }
}
