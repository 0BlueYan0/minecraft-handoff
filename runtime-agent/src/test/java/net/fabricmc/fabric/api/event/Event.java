package net.fabricmc.fabric.api.event;
import java.util.concurrent.CopyOnWriteArrayList;
public final class Event<T> {
    public final CopyOnWriteArrayList<T> listeners = new CopyOnWriteArrayList<>();
    public void register(T listener) { listeners.add(listener); }
}
