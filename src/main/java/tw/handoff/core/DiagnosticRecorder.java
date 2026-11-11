package tw.handoff.core;

import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.Objects;

/** Called from the client thread. Reports state changes and a monotonic-clock heartbeat. */
public final class DiagnosticRecorder {
    public enum Lifecycle { INITIALIZED, JOINED, DISCONNECTED }
    public record Status(Lifecycle lifecycle, long joinCount, long disconnectCount) {}

    private static final long INTERVAL_NANOS = 60_000_000_000L;
    private final LongSupplier clock;
    private final Consumer<Status> sink;
    private boolean initialized;
    private long lastReportNanos;
    private long joinCount;
    private long disconnectCount;
    private Lifecycle lifecycle = Lifecycle.INITIALIZED;

    public DiagnosticRecorder(LongSupplier clock, Consumer<Status> sink) {
        this.clock = Objects.requireNonNull(clock);
        this.sink = Objects.requireNonNull(sink);
    }

    public void initialize() {
        if (initialized) return;
        initialized = true;
        report();
    }

    public void joined() {
        lifecycle = Lifecycle.JOINED;
        joinCount++;
        initialized = true;
        report();
    }

    public void disconnected() {
        lifecycle = Lifecycle.DISCONNECTED;
        disconnectCount++;
        initialized = true;
        report();
    }

    public void tick() {
        if (initialized && clock.getAsLong() - lastReportNanos >= INTERVAL_NANOS) report();
    }

    private void report() {
        lastReportNanos = clock.getAsLong();
        sink.accept(new Status(lifecycle, joinCount, disconnectCount));
    }
}
