package tw.handoff.agent;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
final class ObserverGuard {
    private final Consumer<String> warning;
    private final AtomicBoolean disabled = new AtomicBoolean();
    ObserverGuard(Consumer<String> warning) { this.warning = warning; }
    void run(Runnable diagnostic) {
        if (disabled.get()) return;
        try { diagnostic.run(); }
        catch (RuntimeException | LinkageError failure) {
            if (disabled.compareAndSet(false, true)) {
                try { warning.accept(failure.getClass().getSimpleName()); }
                catch (RuntimeException | LinkageError unavailable) { /* Never propagate a diagnostic reporting failure. */ }
            }
        }
    }
}
