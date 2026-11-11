package net.fabricmc.fabric.api.client.event.lifecycle.v1;
import net.fabricmc.fabric.api.event.Event;
public final class ClientTickEvents {
    public interface EndTick { void onEndTick(Object client); }
    public static final Event<EndTick> END_CLIENT_TICK = new Event<>();
}
