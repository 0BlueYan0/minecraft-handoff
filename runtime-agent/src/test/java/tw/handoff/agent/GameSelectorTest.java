package tw.handoff.agent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class GameSelectorTest {
    @TempDir Path temp;
    @Test void matchesOnlyTheRequestedInstanceWithoutExposingArguments() throws Exception {
        Path game = Files.createDirectory(temp.resolve("Sword Art Online"));
        List<String> args = List.of("-Dlauncher.token=TEST_SECRET", "net.fabricmc.loader.impl.launch.knot.KnotClient", "--gameDir", game.toString());
        assertTrue(GameSelector.matches(args, game));
        assertFalse(GameSelector.matches(args, Files.createDirectory(temp.resolve("another"))));
        assertFalse(GameSelector.matches(List.of("OtherMain", "--gameDir", game.toString()), game));
        assertFalse(GameSelector.matches(List.of("net.fabricmc.loader.impl.launch.knot.KnotClient", "--gameDir"), game));
        assertFalse(GameSelector.matches(List.of("net.fabricmc.loader.impl.launch.knot.KnotClient", "--gameDir", game.toString(), "--gameDir", game.toString()), game));
    }
}
