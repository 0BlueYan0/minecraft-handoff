package tw.handoff.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticRecorderTest {
    private final AtomicLong now = new AtomicLong(0);
    private final ArrayList<DiagnosticRecorder.Status> reports = new ArrayList<>();
    private final DiagnosticRecorder recorder = new DiagnosticRecorder(now::get, reports::add);

    @Test void recordsInitializationWithoutAConnection() {
        recorder.initialize();
        assertEquals(1, reports.size());
        assertEquals(DiagnosticRecorder.Lifecycle.INITIALIZED, reports.getFirst().lifecycle());
        assertEquals(0, reports.getFirst().joinCount());
        assertEquals(0, reports.getFirst().disconnectCount());
    }

    @Test void recordsEachJoinAndDisconnectWithoutIdentities() {
        recorder.initialize();
        recorder.joined();
        recorder.disconnected();
        recorder.joined();
        assertEquals(4, reports.size());
        assertEquals(new DiagnosticRecorder.Status(DiagnosticRecorder.Lifecycle.JOINED, 2, 1), reports.getLast());
    }

    @Test void reportsOnlyAfterSixtySecondsAndDoesNotCatchUpInABurst() {
        recorder.initialize();
        now.set(59_999_999_999L);
        recorder.tick();
        assertEquals(1, reports.size());
        now.set(60_000_000_000L);
        recorder.tick();
        assertEquals(2, reports.size());
        now.set(600_000_000_000L);
        recorder.tick();
        assertEquals(3, reports.size());
        recorder.tick();
        assertEquals(3, reports.size());
    }

    @Test void lifecycleReportRestartsTheSamplingInterval() {
        recorder.initialize();
        now.set(50_000_000_000L);
        recorder.joined();
        now.set(60_000_000_000L);
        recorder.tick();
        assertEquals(2, reports.size());
        now.set(110_000_000_000L);
        recorder.tick();
        assertEquals(3, reports.size());
        assertEquals(DiagnosticRecorder.Lifecycle.JOINED, reports.getLast().lifecycle());
    }

    @Test void tickingBeforeInitializationProducesNoReports() {
        now.set(600_000_000_000L);
        recorder.tick();
        assertTrue(reports.isEmpty());
    }
}
