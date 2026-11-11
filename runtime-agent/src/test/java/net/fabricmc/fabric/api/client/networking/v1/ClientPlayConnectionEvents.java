package net.fabricmc.fabric.api.client.networking.v1;
import net.fabricmc.fabric.api.event.Event;
public final class ClientPlayConnectionEvents {
    public interface Join { void onPlayReady(Object handler, Object sender, Object client); }
    public interface Disconnect { void onPlayDisconnect(Object handler, Object client); }
    public static final Event<Join> JOIN = new Event<>();
    public static final Event<Disconnect> DISCONNECT = new Event<>();
}
