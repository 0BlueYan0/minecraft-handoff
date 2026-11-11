package tw.handoff.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LaunchPlanTest {
    @TempDir Path temp;
    private List<String> arguments(Path game) {
        return List.of("-Xmx4G", "-Dlauncher.token=TEST_SECRET", "-Dlauncher.instance=TEST_INSTANCE",
                "-cp", "/a path/libraries/*", "net.fabricmc.loader.impl.launch.knot.KnotClient",
                "--gameDir", game.toString(), "--accessToken", "TEST_MC_SECRET");
    }

    @Test void preparedCommandStartsARealJvmWithSpacesAndTheExternalModProperty() throws Exception {
        Path game = Files.createDirectory(temp.resolve("Sword Art Online"));
        Path mod = Files.writeString(temp.resolve("external module.jar"), "fixture");
        String javaBinary = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        String classes = Path.of(net.fabricmc.loader.impl.launch.knot.KnotClient.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        List<String> original = List.of("-Dlauncher.token=TEST_SECRET WITH SPACE", "-Dlauncher.instance=TEST_INSTANCE",
                "-cp", classes, "net.fabricmc.loader.impl.launch.knot.KnotClient", "--gameDir", game.toString());
        var plan = LaunchPlan.create(javaBinary, original, game, mod);
        Process replacement = new ProcessBuilder(plan.command()).redirectErrorStream(true).start();
        try {
            var reader = replacement.inputReader();
            assertEquals("READY", reader.readLine());
            assertEquals("TOKEN_PRESERVED=true", reader.readLine());
            assertEquals("MOD_PROPERTY=true", reader.readLine());
        } finally {
            replacement.getOutputStream().close();
            if (!replacement.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) replacement.destroyForcibly().waitFor();
        }
    }

    @Test void matchesOnlyTheExactGameDirectoryAndKnotClient() throws Exception {
        Path game = Files.createDirectory(temp.resolve("Sword Art Online"));
        assertTrue(LaunchPlan.matches(arguments(game), game));
        assertFalse(LaunchPlan.matches(arguments(game), temp.resolve("another")));
        assertFalse(LaunchPlan.matches(List.of("OtherMain", "--gameDir", game.toString()), game));
        assertFalse(LaunchPlan.matches(List.of("net.fabricmc.loader.impl.launch.knot.KnotClient", "--gameDir"), game));
    }

    @Test void preservesArgumentBoundariesAndUsesAnExternalMod() throws Exception {
        Path game = Files.createDirectory(temp.resolve("Sword Art Online"));
        Path mod = Files.writeString(temp.resolve("handoff module.jar"), "fixture");
        List<String> original = arguments(game);
        var plan = LaunchPlan.create("/Java Home/bin/java", original, game, mod);
        assertEquals("/Java Home/bin/java", plan.command().getFirst());
        assertEquals("-Dfabric.addMods=" + mod.toRealPath(), plan.command().get(1));
        assertEquals(original, plan.command().subList(2, plan.command().size()));
        assertEquals(game.toRealPath(), plan.workingDirectory());
        assertThrows(UnsupportedOperationException.class, () -> plan.command().add("other"));
    }

    @Test void refusesModInsideTheManagedInstance() throws Exception {
        Path game = Files.createDirectory(temp.resolve("Sword Art Online"));
        Path mod = Files.writeString(game.resolve("handoff.jar"), "fixture");
        assertThrows(IllegalArgumentException.class, () -> LaunchPlan.create("java", arguments(game), game, mod));
    }

    @Test void rejectsMissingModOrOverridingAdditionalModProperty() throws Exception {
        Path game = Files.createDirectory(temp.resolve("Sword Art Online"));
        Path mod = Files.writeString(temp.resolve("handoff.jar"), "fixture");
        assertThrows(IllegalArgumentException.class, () -> LaunchPlan.create("java", arguments(game), game, temp.resolve("missing.jar")));
        var overridden = new java.util.ArrayList<>(arguments(game));
        overridden.add(0, "-Dfabric.addMods=other");
        assertThrows(IllegalArgumentException.class, () -> LaunchPlan.create("java", overridden, game, mod));
    }

    @Test void refusesAmbiguousOrArgumentFileBasedGameArguments() throws Exception {
        Path game = Files.createDirectory(temp.resolve("Sword Art Online"));
        Path mod = Files.writeString(temp.resolve("handoff.jar"), "fixture");
        var ambiguous = new java.util.ArrayList<>(arguments(game));
        ambiguous.addAll(List.of("--gameDir", game.toString()));
        assertFalse(LaunchPlan.matches(ambiguous, game));
        var atFile = new java.util.ArrayList<>(arguments(game));
        atFile.add(0, "@private-arguments.txt");
        assertThrows(IllegalArgumentException.class, () -> LaunchPlan.create("java", atFile, game, mod));
    }

    @Test void clearsJvmOptionEnvironmentWithoutModifyingTheOriginalMap() {
        var env = new java.util.HashMap<>(Map.of("HOME", "/Users/test", "JAVA_TOOL_OPTIONS", "TEST_SECRET",
                "JDK_JAVA_OPTIONS", "other", "_JAVA_OPTIONS", "other"));
        var sanitized = LaunchPlan.childEnvironment(env);
        assertEquals(Map.of("HOME", "/Users/test"), sanitized);
        assertTrue(env.containsKey("JAVA_TOOL_OPTIONS"));
    }

    @Test void stringRepresentationCannotExposeArgumentsOrTokens() throws Exception {
        Path game = Files.createDirectory(temp.resolve("Sword Art Online"));
        Path mod = Files.writeString(temp.resolve("handoff.jar"), "fixture");
        var plan = LaunchPlan.create("java", arguments(game), game, mod);
        assertEquals("LaunchPlan[arguments redacted]", plan.toString());
        assertFalse(plan.toString().contains("TEST_SECRET"));
        assertFalse(plan.toString().contains("TEST_MC_SECRET"));
    }
}
