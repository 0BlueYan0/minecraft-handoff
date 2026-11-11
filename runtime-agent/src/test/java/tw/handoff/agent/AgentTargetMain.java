package tw.handoff.agent;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Files;
public final class AgentTargetMain {
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setName("FixtureClientThread");
        try (var loader = new URLClassLoader(new java.net.URL[]{Path.of(args[0]).toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> type = Class.forName("net.minecraft.class_310", true, loader);
            Object client = type.getMethod("method_1551").invoke(null);
            Path status = Path.of(args[1]), stop = Path.of(args[2]), action = Path.of(args[3]);
            String previous = "";
            while (!Files.exists(stop)) {
                type.getMethod("tick").invoke(client);
                if (Files.exists(action)) {
                    String current = Files.readString(action);
                    if (!current.equals(previous)) {
                        Thread background = new Thread(() -> {
                            try { type.getMethod("disconnected").invoke(client); }
                            catch (Exception e) { throw new IllegalStateException(); }
                        }, "FixtureNetty");
                        background.start(); background.join(); previous = current;
                    }
                }
                Files.writeString(status, ProcessHandle.current().pid() + ":" + type.getMethod("counts").invoke(client));
                Thread.sleep(20);
            }
        }
    }
}
