package tw.handoff.relay.core;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReadPauseTest {
    @Test void timeoutAndCompletionRestoreOnlyOnceAndLateSnapshotIsRefused(){
        var restores=new AtomicInteger();var pause=new ReadPause(restores::incrementAndGet);
        assertTrue(pause.active());pause.close();assertFalse(pause.active());pause.close();assertEquals(1,restores.get());
    }
}
