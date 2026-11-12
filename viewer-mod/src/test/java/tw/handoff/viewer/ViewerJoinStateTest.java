package tw.handoff.viewer;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tw.handoff.relay.core.StateChunks;

class ViewerJoinStateTest {
    private final List<String> sent = new ArrayList<>();
    private final List<String> overlays = new ArrayList<>();
    private final List<Long> preparedElapsed = new ArrayList<>();
    private final Queue<Runnable> gameQueue = new ArrayDeque<>();
    private long clock;
    private int applied;
    private ViewerJoinState.Preparer preparer;
    private ViewerJoinState state;

    @BeforeEach
    void setUp() {
        preparer = (snapshot, elapsed) -> {
            preparedElapsed.add(elapsed);
            return () -> applied++;
        };
        state = new ViewerJoinState(sent::add, (s, e) -> preparer.prepare(s, e), gameQueue::add, () -> clock, overlays::add);
    }

    private void hostReady() {
        assertFalse(state.receive("HANDOFF2:R:0", 0)); // R: is also handled by ViewerClient, so the join state reports it as not consumed.
    }

    private void drainGameThread() {
        while (!gameQueue.isEmpty()) gameQueue.remove().run();
    }

    private List<String> threeChunks(long ageNanos) {
        var snapshot = new JsonObject();
        snapshot.addProperty("filler", "a".repeat(40_000));
        var document = new JsonObject();
        document.addProperty("ageNanos", ageNanos);
        document.add("snapshot", snapshot);
        var chunks = StateChunks.encode(document.toString().getBytes(StandardCharsets.UTF_8));
        assertEquals(3, chunks.size());
        return chunks;
    }

    @Test
    void echoesProbe() {
        assertTrue(state.receive("HANDOFF2:Q:ab", 0));
        assertEquals(List.of("HANDOFF2:q:ab"), sent);
    }

    @Test
    void appliesAfterLastChunkAndAcks() {
        state.onJoin();
        hostReady();
        var chunks = threeChunks(500);
        assertTrue(state.receive(chunks.get(0), 900));
        assertTrue(state.receive(chunks.get(1), 950));
        assertTrue(state.receive(chunks.get(2), 1000));
        assertEquals(0, preparedElapsed.size());
        clock = 1700;
        drainGameThread();
        assertEquals(List.of(500L + (1700 - 1000)), preparedElapsed);
        assertEquals(1, applied);
        assertEquals(List.of("HANDOFF2:K"), sent);
    }

    @Test
    void contractMismatchSendsVersion() {
        preparer = (snapshot, elapsed) -> {
            throw new IllegalArgumentException("Snapshot contract mismatch");
        };
        state.onJoin();
        hostReady();
        for (String chunk : threeChunks(0)) state.receive(chunk, 0);
        drainGameThread();
        assertEquals(List.of("HANDOFF2:N:version"), sent);
        assertEquals(0, applied);
    }

    @Test
    void chunkBeforeJoinIsKeptAndAppliedAfterJoin() {
        hostReady();
        for (String chunk : threeChunks(0)) assertTrue(state.receive(chunk, 0));
        drainGameThread();
        assertEquals(List.of(), sent);
        assertEquals(0, preparedElapsed.size());
        state.onJoin();
        drainGameThread();
        assertEquals(1, applied);
        assertEquals(List.of("HANDOFF2:K"), sent);
    }

    @Test
    void waitTimeBeforeJoinCountsTowardElapsed() {
        hostReady();
        feedAll(threeChunks(500), 1000);
        clock = 4000;
        state.onJoin();
        drainGameThread();
        assertEquals(List.of(500L + 3000L), preparedElapsed);
    }

    @Test
    void chunksBeforeJoinFromADroppedConnectionAreNotApplied() {
        feedAll(threeChunks(0), 0);
        state.onDisconnect();
        state.onJoin();
        drainGameThread();
        assertEquals(0, applied);
        assertEquals(List.of(), sent);
    }

    @Test
    void ackWaitsForHostReadyAndIsSentOnceWhenItArrives() {
        state.onJoin();
        feedAll(threeChunks(0), 0);
        drainGameThread();
        assertEquals(1, applied);
        assertEquals(List.of(), sent);
        assertFalse(state.receive("HANDOFF2:R:0", 0));
        assertEquals(List.of("HANDOFF2:K"), sent);
        state.receive("HANDOFF2:R:7", 0);
        assertEquals(List.of("HANDOFF2:K"), sent);
    }

    @Test
    void failureWaitsForHostReadyToo() {
        state.onJoin();
        state.receive(threeChunks(0).get(0), 0);
        state.receive(threeChunks(0).get(2), 0);
        assertEquals(List.of(), sent);
        state.receive("HANDOFF2:R:0", 0);
        assertEquals(List.of("HANDOFF2:N:snapshot"), sent);
    }

    @Test
    void probeIsAnsweredImmediatelyBeforeHostReady() {
        assertTrue(state.receive("HANDOFF2:Q:ab", 0));
        assertEquals(List.of("HANDOFF2:q:ab"), sent);
    }

    @Test
    void heldReplyIsDroppedOnDisconnect() {
        state.onJoin();
        feedAll(threeChunks(0), 0);
        drainGameThread();
        state.onDisconnect();
        state.onJoin();
        state.receive("HANDOFF2:R:0", 0);
        assertEquals(List.of(), sent);
    }

    @Test
    void hostReadyDoesNotSurviveDisconnect() {
        state.onJoin();
        hostReady();
        state.onDisconnect();
        state.onJoin();
        feedAll(threeChunks(0), 0);
        drainGameThread();
        assertEquals(List.of(), sent);
        assertEquals(1, applied);
        state.receive("HANDOFF2:R:0", 0);
        assertEquals(List.of("HANDOFF2:K"), sent);
    }

    @Test
    void readyThenReplyGoesOutAtOnce() {
        state.onJoin();
        hostReady();
        feedAll(threeChunks(0), 0);
        drainGameThread();
        assertEquals(List.of("HANDOFF2:K"), sent);
    }

    @Test
    void blockerLabelsAreChinese() {
        assertEquals("SAO 版本不符", ViewerJoinState.label("version"));
        assertEquals("saoblessing 紀錄超過上限", ViewerJoinState.label("saoblessing"));
        assertEquals("重播紀錄超過上限", ViewerJoinState.label("replay-budget"));
        assertEquals("重播紀錄有缺", ViewerJoinState.label("replay-skipped"));
        assertEquals("延遲過大", ViewerJoinState.label("latency"));
        assertEquals("狀態快照失敗", ViewerJoinState.label("snapshot"));
        assertEquals("等待狀態同步", ViewerJoinState.label("pending"));
    }

    @Test
    void missingPieceAndHostErrorUseTheSameLabels() {
        assertTrue(state.receive("HANDOFF2:M:latency", 0));
        assertTrue(state.receive("HANDOFF2:E:pending", 0));
        assertEquals(List.of("觀察中 · 缺：延遲過大", "等待狀態同步"), overlays);
    }

    private void feedAll(List<String> chunks, long at) {
        for (String chunk : chunks) state.receive(chunk, at);
    }

    @Test
    void totalChangingBetweenChunksSendsSnapshotFailure() {
        state.onJoin();
        hostReady();
        String first = tw.handoff.relay.core.ControlWire.chunk(0, 3, "A".repeat(24000)).encode();
        String second = tw.handoff.relay.core.ControlWire.chunk(1, 4, "A".repeat(24000)).encode();
        state.receive(first, 0);
        state.receive(second, 0);
        drainGameThread();
        assertEquals(List.of("HANDOFF2:N:snapshot"), sent);
        assertEquals(0, preparedElapsed.size());
    }

    @Test
    void nextRoundAfterFailureStartsOverAndSucceeds() {
        state.onJoin();
        hostReady();
        var chunks = threeChunks(0);
        state.receive(chunks.get(0), 0);
        state.receive(chunks.get(2), 0);
        feedAll(chunks, 0);
        clock = 0;
        drainGameThread();
        assertEquals(List.of("HANDOFF2:N:snapshot", "HANDOFF2:K"), sent);
        assertEquals(1, applied);
    }

    @Test
    void negativeAgeSendsSnapshotFailure() {
        state.onJoin();
        hostReady();
        feedAll(threeChunks(-1), 0);
        drainGameThread();
        assertEquals(List.of("HANDOFF2:N:snapshot"), sent);
        assertEquals(0, preparedElapsed.size());
    }

    @Test
    void ageOverflowSendsSnapshotFailure() {
        state.onJoin();
        hostReady();
        feedAll(threeChunks(Long.MAX_VALUE), 1000);
        clock = 1700;
        drainGameThread();
        assertEquals(List.of("HANDOFF2:N:snapshot"), sent);
        assertEquals(0, preparedElapsed.size());
    }

    @Test
    void otherPrepareExceptionSendsSnapshotFailure() {
        preparer = (snapshot, elapsed) -> {
            throw new IllegalStateException("boom");
        };
        state.onJoin();
        hostReady();
        feedAll(threeChunks(0), 0);
        drainGameThread();
        assertEquals(List.of("HANDOFF2:N:snapshot"), sent);
    }

    @Test
    void applyExceptionSendsSnapshotFailureWithoutAck() {
        preparer = (snapshot, elapsed) -> () -> {
            throw new IllegalStateException("boom");
        };
        state.onJoin();
        hostReady();
        feedAll(threeChunks(0), 0);
        drainGameThread();
        assertEquals(List.of("HANDOFF2:N:snapshot"), sent);
    }

    @Test
    void snapshotQueuedBeforeReconnectIsDropped() {
        state.onJoin();
        feedAll(threeChunks(0), 0);
        state.onDisconnect();
        state.onJoin();
        drainGameThread();
        assertEquals(List.of(), sent);
        assertEquals(0, preparedElapsed.size());
        assertEquals(0, applied);
    }

    @Test
    void gapOrWrongTotalSendsSnapshotFailureAndAppliesNothing() {
        state.onJoin();
        hostReady();
        var chunks = threeChunks(0);
        state.receive(chunks.get(0), 0);
        state.receive(chunks.get(2), 0);
        drainGameThread();
        assertEquals(List.of("HANDOFF2:N:snapshot"), sent);
        assertEquals(0, preparedElapsed.size());
        assertEquals(0, applied);
    }
}
