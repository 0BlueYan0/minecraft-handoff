package tw.handoff.relay.core;
import java.util.concurrent.atomic.AtomicBoolean;
public final class ReadPause implements AutoCloseable {
    private final Runnable restore;private final AtomicBoolean closed=new AtomicBoolean();
    public ReadPause(Runnable restore){this.restore=restore;}
    public boolean active(){return !closed.get();}
    public void close(){if(closed.compareAndSet(false,true))restore.run();}
}
