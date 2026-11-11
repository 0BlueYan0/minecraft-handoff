package tw.handoff.agent;
import java.util.function.LongSupplier;
import java.util.function.Consumer;
import java.lang.management.ManagementFactory;
final class AgentState {
    private final LongSupplier clock;
    private final Consumer<String> sink;
    private String state;
    private long joins, disconnects, last;
    AgentState(LongSupplier clock, Consumer<String> sink) { this.clock = clock; this.sink = sink; }
    void start(boolean connected) { state = connected ? "ATTACHED_IN_GAME" : "ATTACHED_IN_MENU"; report(); }
    void joined() { joins++; state = "JOINED"; report(); }
    void disconnected() { disconnects++; state = "DISCONNECTED"; report(); }
    void tick() { if (state != null && clock.getAsLong() - last >= 60_000_000_000L) report(); }
    private void report() {
        last = clock.getAsLong();
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        sink.accept("{\"schemaVersion\":2,\"version\":\"0.1.2\",\"mode\":\"runtime-agent\",\"pid\":"
                + ProcessHandle.current().pid() + ",\"state\":\"" + state + "\",\"joins\":" + joins
                + ",\"disconnects\":" + disconnects + ",\"heapUsedBytes\":" + heap.getUsed()
                + ",\"heapMaxBytes\":" + heap.getMax() + "}");
    }
}
