package net.minecraft;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
public final class class_310 implements Executor {
    public final java.io.File field_1697 = new java.io.File(System.getProperty("fixture.gameDir", "."));
    private static final class_310 INSTANCE = new class_310();
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private boolean connected = true;
    private boolean rejectNext = Boolean.getBoolean("fixture.rejectNextExecute");
    public static class_310 method_1551() { return INSTANCE; }
    public Object method_1562() { return connected ? this : null; }
    public void execute(Runnable action) {
        if (rejectNext) { rejectNext = false; throw new java.util.concurrent.RejectedExecutionException("fixture rejection"); }
        queue.add(action);
    }
    public void tick() {
        Runnable r;
        while ((r = queue.poll()) != null) r.run();
        for (var listener : ClientTickEvents.END_CLIENT_TICK.listeners) listener.onEndTick(this);
    }
    public void disconnected() {
        connected = false;
        for (var listener : ClientPlayConnectionEvents.DISCONNECT.listeners) listener.onPlayDisconnect(null, this);
    }
    public String counts() {
        return ClientTickEvents.END_CLIENT_TICK.listeners.size() + "," + ClientPlayConnectionEvents.JOIN.listeners.size()
                + "," + ClientPlayConnectionEvents.DISCONNECT.listeners.size();
    }
}
