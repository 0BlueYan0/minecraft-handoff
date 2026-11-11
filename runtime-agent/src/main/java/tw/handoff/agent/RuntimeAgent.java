package tw.handoff.agent;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RuntimeAgent {
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile String status = "PENDING";

    public static void agentmain(String options, Instrumentation instrumentation) {
        Path channel, expectedGame;
        try {
            String[] parts = options.split("\\.", -1);
            if (parts.length != 2) return;
            channel = Path.of(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
            expectedGame = Path.of(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            if (!channel.isAbsolute() || !Files.isRegularFile(channel, LinkOption.NOFOLLOW_LINKS)) return;
            if (!expectedGame.isAbsolute() || !Files.isDirectory(expectedGame)) return;
        } catch (RuntimeException badOptions) { return; }
        boolean reserved = false;
        try {
            Class<?> minecraft = null;
            for (Class<?> type : instrumentation.getAllLoadedClasses()) {
                if (type.getName().equals("net.minecraft.class_310") || type.getName().equals("net.minecraft.client.Minecraft")) {
                    if (minecraft != null) throw new IllegalStateException("Ambiguous Minecraft client");
                    minecraft = type;
                }
            }
            if (minecraft == null) throw new ClassNotFoundException("Minecraft client not ready");
            ClassLoader loader = minecraft.getClassLoader();
            Object client = FabricBridge.method(minecraft, "method_1551", "getInstance").invoke(null);
            if (client == null) throw new IllegalStateException("Minecraft client not ready");
            if (!FabricBridge.isExpectedDirectory(client, expectedGame)) {
                write(channel, "FAILED:WRONG_GAME_DIRECTORY");
                return;
            }
            if (!STARTED.compareAndSet(false, true)) {
                write(channel, status.equals("READY") ? "ALREADY_READY" : status);
                return;
            }
            reserved = true;
            write(channel, "PENDING");
            ((Executor) client).execute(() -> {
                try {
                    FabricBridge.install(client, loader);
                    status = "READY";
                    write(channel, status);
                } catch (Exception | LinkageError failure) {
                    status = "FAILED:CLIENT_CALLBACK:" + failure.getClass().getSimpleName();
                    write(channel, status);
                }
            });
        } catch (Exception | LinkageError failure) {
            String failed = "FAILED:CLIENT_LOOKUP:" + failure.getClass().getSimpleName();
            if (reserved) status = failed;
            write(channel, failed);
        }
    }

    static void observerDisabled(String type) { status = "FAILED:OBSERVER_DISABLED:" + type; }

    private static void write(Path channel, String value) {
        try { Files.writeString(channel, value, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE); }
        catch (Exception unavailable) { System.err.println("[minecraft-handoff-agent] Status channel unavailable"); }
    }
}
