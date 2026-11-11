package tw.handoff.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class OriginalGameFinderTest {
    @TempDir Path temp;
    private String currentOwner() { return ProcessHandle.current().info().user().orElseThrow(); }
    @BeforeEach void requireNativeArgumentSupport() {
        Assumptions.assumeTrue(ProcessHandle.current().info().arguments().isPresent(),
                "This runtime does not expose native argument arrays; Unix/Mac acceptance remains required");
    }
    private Process startGame(Path game) throws Exception {
        String executable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        String classes = Path.of(net.fabricmc.loader.impl.launch.knot.KnotClient.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        Process child = new ProcessBuilder(executable, "-Dlauncher.token=TEST_SECRET WITH SPACE",
                "-Dlauncher.instance=TEST_INSTANCE", "-cp", classes,
                "net.fabricmc.loader.impl.launch.knot.KnotClient", "--gameDir", game.toString()).redirectErrorStream(true).start();
        assertEquals("READY", child.inputReader().readLine());
        return child;
    }
    private static void close(Process process) throws Exception {
        process.getOutputStream().close();
        if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly().waitFor();
    }

    @Test void realProcessArgumentsCanBeCapturedAndRelaunchedWithoutLosingBoundaries() throws Exception {
        Path game = Files.createDirectory(temp.resolve("Sword Art Online"));
        Path mod = Files.writeString(temp.resolve("external module.jar"), "fixture");
        Process original = startGame(game);
        try {
            var match = OriginalGameFinder.find(List.of(original.toHandle()), game, mod, currentOwner()).orElseThrow();
            assertEquals(original.pid(), match.process().pid());
            close(original);
            assertFalse(original.isAlive());
            Process replacement = new ProcessBuilder(match.plan().command()).redirectErrorStream(true).start();
            try {
                var reader = replacement.inputReader();
                assertEquals("READY", reader.readLine());
                assertEquals("TOKEN_PRESERVED=true", reader.readLine());
                assertEquals("MOD_PROPERTY=true", reader.readLine());
            } finally { close(replacement); }
        } finally { if (original.isAlive()) close(original); }
    }

    @Test void moreThanOneMatchingGameFailsClosed() throws Exception {
        Path game = Files.createDirectory(temp.resolve("Sword Art Online"));
        Path mod = Files.writeString(temp.resolve("external.jar"), "fixture");
        Process first = startGame(game);
        Process second = startGame(game);
        try {
            assertThrows(IllegalStateException.class, () -> OriginalGameFinder.find(
                    List.of(first.toHandle(), second.toHandle()), game, mod, currentOwner()));
        } finally { close(first); close(second); }
    }

    @Test void wrongOwnerOrGameDirectoryDoesNotSelectAProcess() throws Exception {
        Path game = Files.createDirectory(temp.resolve("Sword Art Online"));
        Path other = Files.createDirectory(temp.resolve("Other Instance"));
        Path mod = Files.writeString(temp.resolve("external.jar"), "fixture");
        Process original = startGame(game);
        try {
            assertTrue(OriginalGameFinder.find(List.of(original.toHandle()), game, mod, "another-user").isEmpty());
            assertTrue(OriginalGameFinder.find(List.of(original.toHandle()), other, mod, currentOwner()).isEmpty());
        } finally { close(original); }
    }
}
