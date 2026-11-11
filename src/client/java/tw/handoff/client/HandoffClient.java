package tw.handoff.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.handoff.core.DiagnosticRecorder;
import tw.handoff.core.DiagnosticSnapshot;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/** Additive, observational adapter. P0 neither intercepts gameplay nor opens a network listener. */
public final class HandoffClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft-handoff");

    @Override
    public void onInitializeClient() {
        DiagnosticRecorder recorder = new DiagnosticRecorder(System::nanoTime, this::report);
        recorder.initialize();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> recorder.joined());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> recorder.disconnected());
        ClientTickEvents.END_CLIENT_TICK.register(client -> recorder.tick());
    }

    private void report(DiagnosticRecorder.Status status) {
        try {
            Map<String, String> versions = new LinkedHashMap<>();
            for (String id : DiagnosticSnapshot.MOD_IDS) {
                FabricLoader.getInstance().getModContainer(id).ifPresent(container ->
                        versions.put(id, container.getMetadata().getVersion().getFriendlyString()));
            }
            var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            long maximum = heap.getMax() >= 0 ? heap.getMax() : Runtime.getRuntime().maxMemory();
            DiagnosticSnapshot snapshot = new DiagnosticSnapshot(
                    version("minecraft_handoff"), version("minecraft"), version("fabricloader"), versions,
                    heap.getUsed(), maximum, status.lifecycle(), status.joinCount(), status.disconnectCount());
            LOGGER.info("[minecraft-handoff] {}", snapshot.toJson());
        } catch (RuntimeException exception) {
            // Diagnostics must not disrupt gameplay or copy arbitrary exception messages into the log.
            LOGGER.warn("[minecraft-handoff] Diagnostics unavailable ({})", exception.getClass().getSimpleName());
        }
    }

    private static String version(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }
}
