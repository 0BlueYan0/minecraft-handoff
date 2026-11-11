package tw.handoff.core;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DiagnosticSnapshot(String applicationVersion, String minecraftVersion, String fabricLoaderVersion,
        Map<String, String> modVersions, long heapUsedBytes, long heapMaxBytes,
        DiagnosticRecorder.Lifecycle lifecycle, long joinCount, long disconnectCount) {
    public static final List<String> MOD_IDS = List.of("fabricloader", "fabric-api", "saoui", "mcef", "minecraft_handoff");

    public DiagnosticSnapshot {
        Objects.requireNonNull(applicationVersion);
        Objects.requireNonNull(minecraftVersion);
        Objects.requireNonNull(fabricLoaderVersion);
        Objects.requireNonNull(lifecycle);
        Objects.requireNonNull(modVersions);
        if (heapUsedBytes < 0 || heapMaxBytes < 0 || heapUsedBytes > heapMaxBytes || joinCount < 0 || disconnectCount < 0) {
            throw new IllegalArgumentException("Memory and counts must be nonnegative, and heap used must not exceed max");
        }
        Map<String, String> filtered = new LinkedHashMap<>();
        for (String id : MOD_IDS) {
            String value = modVersions.get(id);
            if (value != null) filtered.put(id, value);
        }
        modVersions = Collections.unmodifiableMap(filtered);
    }

    public String toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", 1);
        json.addProperty("applicationVersion", applicationVersion);
        json.addProperty("minecraftVersion", minecraftVersion);
        json.addProperty("fabricLoaderVersion", fabricLoaderVersion);
        JsonObject mods = new JsonObject();
        modVersions.forEach(mods::addProperty);
        json.add("modVersions", mods);
        json.addProperty("heapUsedBytes", heapUsedBytes);
        json.addProperty("heapMaxBytes", heapMaxBytes);
        json.addProperty("lifecycle", lifecycle.name());
        json.addProperty("joinCount", joinCount);
        json.addProperty("disconnectCount", disconnectCount);
        return json.toString();
    }
}
