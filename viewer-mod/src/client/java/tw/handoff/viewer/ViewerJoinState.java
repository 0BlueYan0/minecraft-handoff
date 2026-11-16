package tw.handoff.viewer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import tw.handoff.relay.core.ControlWire;
import tw.handoff.relay.core.StateChunks;

/**
 * Viewer side of the join handshake: answers probes, shows missing pieces, rebuilds the chunked snapshot and
 * applies it on the game thread after the world JOIN. Replies go only to the resident host, never to the server.
 * receive() runs on the connection thread; only prepare and apply are handed to the game executor.
 */
final class ViewerJoinState {
    /** One-shot restore step produced by prepare. */
    interface Plan {
        void apply() throws Exception;
    }

    interface Preparer {
        Plan prepare(JsonObject snapshot, long elapsedNanos) throws Exception;
    }

    static final int MAX_SNAPSHOT_BYTES = 2 * 1024 * 1024 + 64 * 1024;
    private static final Map<String, String> LABELS = Map.of(
            "version", "SAO 版本不符",
            "saoblessing", "saoblessing 紀錄超過上限",
            "replay-budget", "重播紀錄超過上限",
            "replay-skipped", "重播紀錄有缺",
            "latency", "延遲過大",
            "snapshot", "狀態快照失敗",
            "pending", "等待狀態同步");

    private final Consumer<String> send;
    private final Preparer preparer;
    private final Executor gameThread;
    private final LongSupplier clock;
    private final Consumer<String> overlay;
    // joined, generation, reassembler and failed are only touched under this monitor: the connection thread
    // (chunk) and the game thread (onJoin, onDisconnect, apply) both use them.
    private boolean joined;
    private long generation;
    private StateChunks.Reassembler reassembler;
    private boolean failed;
    private record Pending(byte[] document, long receivedAt) {
    }
    private Pending pending;
    // Replies to the host (ACK or failure) wait here until the host's first R: message. The host ignores control messages from a viewer it has not yet marked active.
    private boolean hostReady;
    private final java.util.List<String> held = new java.util.ArrayList<>();

    ViewerJoinState(Consumer<String> send, Preparer preparer, Executor gameThread, LongSupplier clock, Consumer<String> overlay) {
        this.send = send;
        this.preparer = preparer;
        this.gameThread = gameThread;
        this.clock = clock;
        this.overlay = overlay;
    }

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("handoff-viewer");

    /** Shape of each restored field (null, value, array or object size) for the log; values are game state and are never written. */
    static String summary(JsonObject snapshot) {
        JsonElement sections = snapshot.get("sections");
        if (sections == null || !sections.isJsonObject()) return "no sections";
        var out = new java.util.StringJoiner(" ");
        for (var section : sections.getAsJsonObject().entrySet()) {
            if (!section.getValue().isJsonObject()) {
                out.add(section.getKey() + "=" + shape(section.getValue()));
                continue;
            }
            for (var field : section.getValue().getAsJsonObject().entrySet()) out.add(section.getKey() + "." + field.getKey() + "=" + shape(field.getValue()));
        }
        JsonElement quests = snapshot.get("quests");
        if (quests != null) out.add("quests=" + shape(quests));
        return out.toString();
    }

    private static String shape(JsonElement value) {
        if (value == null || value.isJsonNull()) return "null";
        if (value.isJsonArray()) return "[" + value.getAsJsonArray().size() + "]";
        if (value.isJsonObject()) return "{" + value.getAsJsonObject().size() + "}";
        return "value";
    }

    static String label(String piece) {
        return LABELS.getOrDefault(piece, piece);
    }

    /**
     * The host sends the snapshot chunks right after the world snapshot, and the connection thread reads them before the game thread has
     * handled the login packet, so the JOIN event can come after the last chunk. Chunks, a finished document and held replies from this
     * connection therefore survive JOIN; only a disconnect clears them.
     */
    synchronized void onJoin() {
        joined = true;
        generation++;
        if (pending != null) {
            Pending ready = pending;
            pending = null;
            long queuedIn = generation;
            gameThread.execute(() -> apply(ready.document(), ready.receivedAt(), queuedIn));
        }
    }

    synchronized void onDisconnect() {
        joined = false;
        generation++;
        discard();
    }

    private void discard() {
        reassembler = null;
        failed = false;
        hostReady = false;
        held.clear();
        pending = null;
    }

    /** Sends a reply now when the host is ready for it, otherwise keeps it for the host's first R: message. */
    private synchronized void reply(String message) {
        if (hostReady) send.accept(message);
        else held.add(message);
    }

    private synchronized void hostReady() {
        hostReady = true;
        for (String message : held) send.accept(message);
        held.clear();
    }

    /** Returns true when the message was handled here. */
    boolean receive(String message, long nowNanos) {
        if (message == null || !message.startsWith(ControlWire.PREFIX)) return false;
        String body = message.substring(ControlWire.PREFIX.length());
        try {
            if (body.startsWith("Q:")) {
                send.accept(ControlWire.pong(ControlWire.parsePing(message)));
                return true;
            }
            if (body.startsWith("M:")) {
                overlay.accept("觀察中 · 缺：" + label(ControlWire.parseMissing(message)));
                return true;
            }
            if (body.startsWith("E:")) {
                String piece = body.substring(2);
                if (!LABELS.containsKey(piece)) return false;
                overlay.accept(label(piece));
                return true;
            }
            if (body.startsWith("S:")) {
                chunk(message, nowNanos);
                return true;
            }
            if (body.startsWith("R:")) {
                hostReady();
                return false; // ViewerClient still applies the control state itself.
            }
        } catch (IllegalArgumentException invalid) {
            return true;
        }
        return false;
    }

    private synchronized void chunk(String message, long nowNanos) {
        ControlWire.Chunk parsed;
        try {
            parsed = ControlWire.parseChunk(message);
        } catch (IllegalArgumentException invalid) {
            reject("snapshot");
            return;
        }
        if (parsed.index() == 0) {
            failed = false;
            reassembler = new StateChunks.Reassembler(MAX_SNAPSHOT_BYTES);
        }
        if (failed) return;
        if (reassembler == null) {
            reject("snapshot");
            return;
        }
        byte[] document;
        try {
            var done = reassembler.accept(message);
            if (done.isEmpty()) return;
            document = done.get();
        } catch (IllegalArgumentException invalid) {
            reject("snapshot");
            return;
        }
        reassembler = null;
        if (!joined) {
            pending = new Pending(document, nowNanos);
            return;
        }
        long queuedIn = generation;
        gameThread.execute(() -> apply(document, nowNanos, queuedIn));
    }

    private void reject(String piece) {
        reassembler = null;
        if (failed) return;
        failed = true;
        reply(ControlWire.failed(piece));
    }

    /** Runs on the game thread. */
    private void apply(byte[] document, long receivedAt, long queuedIn) {
        synchronized (this) {
            if (!joined || generation != queuedIn) return;
        }
        Plan plan;
        JsonObject restored = null;
        try {
            JsonObject root = JsonParser.parseString(new String(document, StandardCharsets.UTF_8)).getAsJsonObject();
            long age = root.get("ageNanos").getAsLong();
            JsonElement snapshot = root.get("snapshot");
            long waited = clock.getAsLong() - receivedAt;
            if (age < 0 || waited < 0 || snapshot == null || !snapshot.isJsonObject()) throw new IllegalArgumentException("Invalid snapshot document");
            restored = snapshot.getAsJsonObject();
            plan = preparer.prepare(restored, Math.addExact(age, waited));
        } catch (IllegalArgumentException contract) {
            String piece = contract.getMessage() != null && contract.getMessage().contains("contract") ? "version" : "snapshot";
            LOG.warn("Join state not applied: {} ({})", piece, contract.getClass().getSimpleName());
            reply(ControlWire.failed(piece));
            return;
        } catch (Exception other) {
            LOG.warn("Join state not applied: snapshot ({})", other.getClass().getSimpleName());
            reply(ControlWire.failed("snapshot"));
            return;
        }
        try {
            plan.apply();
        } catch (Exception failedApply) {
            LOG.warn("Join state not applied: restore failed ({})", failedApply.getClass().getSimpleName());
            reply(ControlWire.failed("snapshot"));
            return;
        }
        LOG.info("Join state applied: {}", summary(restored));
        reply(ControlWire.ack());
    }
}
