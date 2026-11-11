package tw.handoff.agent;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executor;
import static org.junit.jupiter.api.Assertions.*;

class ObserverGuardTest {
    @Test void throwingDiagnosticIsDisabledAndDoesNotInterruptFollowingGameWork() {
        var errors = new ArrayList<String>();
        var attempts = new AtomicInteger();
        var gameplay = new AtomicInteger();
        var guard = new ObserverGuard(errors::add);
        Runnable tick = () -> {
            guard.run(() -> { attempts.incrementAndGet(); throw new IllegalStateException("TEST_SECRET must not be logged"); });
            gameplay.incrementAndGet();
        };
        assertDoesNotThrow(tick::run);
        assertDoesNotThrow(tick::run);
        assertEquals(2, gameplay.get());
        assertEquals(1, attempts.get());
        assertEquals(java.util.List.of("IllegalStateException"), errors);
    }

    @Test void queuedDiagnosticAndFailingWarningSinkCannotEscapeToGameExecutor() {
        var queue = new ArrayDeque<Runnable>();
        Executor executor = queue::add;
        var gameplay = new AtomicInteger();
        var guard = new ObserverGuard(type -> { throw new IllegalStateException("warning failure"); });
        guard.run(() -> executor.execute(() -> guard.run(() -> { throw new NoClassDefFoundError("diagnostic unavailable"); })));
        queue.add(gameplay::incrementAndGet);
        assertDoesNotThrow(() -> { while (!queue.isEmpty()) queue.remove().run(); });
        assertEquals(1, gameplay.get());
    }
}
