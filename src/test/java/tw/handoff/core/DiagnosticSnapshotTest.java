package tw.handoff.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class DiagnosticSnapshotTest {
    private DiagnosticSnapshot snapshot(Map<String, String> mods) {
        return new DiagnosticSnapshot("0.1.0", "1.21.4", "0.20.1", mods,
                123, 456, DiagnosticRecorder.Lifecycle.INITIALIZED, 0, 0);
    }

    @Test void diagnosticsContainOnlyExplicitFields() {
        var json = JsonParser.parseString(snapshot(Map.of("saoui", "2.51.8", "launcher.token", "DO_NOT_LOG", "other-mod", "private-data")).toJson()).getAsJsonObject();
        assertEquals(Set.of("schemaVersion", "applicationVersion", "minecraftVersion", "fabricLoaderVersion",
                "modVersions", "heapUsedBytes", "heapMaxBytes", "lifecycle", "joinCount", "disconnectCount"), json.keySet());
        assertEquals(1, json.get("schemaVersion").getAsInt());
        assertEquals(Set.of("saoui"), json.getAsJsonObject("modVersions").keySet());
        assertFalse(json.toString().contains("DO_NOT_LOG"));
        assertFalse(json.toString().contains("private-data"));
    }

    @Test void serializesNumbersAndEscapesVersionStrings() {
        var json = JsonParser.parseString(snapshot(Map.of("saoui", "a\"b\\c\n繁體")).toJson()).getAsJsonObject();
        assertEquals("a\"b\\c\n繁體", json.getAsJsonObject("modVersions").get("saoui").getAsString());
        assertEquals(123, json.get("heapUsedBytes").getAsLong());
        assertEquals(456, json.get("heapMaxBytes").getAsLong());
        assertEquals("INITIALIZED", json.get("lifecycle").getAsString());
    }

    @Test void missingModVersionsAreOmitted() {
        Map<String, String> mods = new HashMap<>();
        mods.put("saoui", null);
        mods.put("mcef", "2.1.6");
        var json = JsonParser.parseString(snapshot(mods).toJson()).getAsJsonObject();
        assertEquals(Set.of("mcef"), json.getAsJsonObject("modVersions").keySet());
        assertTrue(JsonParser.parseString(snapshot(Map.of()).toJson()).getAsJsonObject().getAsJsonObject("modVersions").isEmpty());
    }

    @Test void snapshotDoesNotChangeWhenInputMapChanges() {
        Map<String, String> mods = new HashMap<>(Map.of("saoui", "2.51.8"));
        var snapshot = snapshot(mods);
        mods.put("saoui", "changed");
        assertEquals("2.51.8", JsonParser.parseString(snapshot.toJson()).getAsJsonObject().getAsJsonObject("modVersions").get("saoui").getAsString());
    }

    @Test void rejectsInvalidMemoryOrCounts() {
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticSnapshot("v", "mc", "fabric", Map.of(), -1, 10, DiagnosticRecorder.Lifecycle.INITIALIZED, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticSnapshot("v", "mc", "fabric", Map.of(), 0, -1, DiagnosticRecorder.Lifecycle.INITIALIZED, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticSnapshot("v", "mc", "fabric", Map.of(), 11, 10, DiagnosticRecorder.Lifecycle.INITIALIZED, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticSnapshot("v", "mc", "fabric", Map.of(), 0, 10, DiagnosticRecorder.Lifecycle.JOINED, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosticSnapshot("v", "mc", "fabric", Map.of(), 0, 10, DiagnosticRecorder.Lifecycle.JOINED, 0, -1));
    }
}
