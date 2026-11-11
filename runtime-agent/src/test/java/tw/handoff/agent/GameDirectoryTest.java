package tw.handoff.agent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.io.File;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;
class GameDirectoryTest {
    public static final class Client { public final File field_1697; Client(File file) { field_1697 = file; } }
    @TempDir Path temp;
    @Test void onlyAcceptsTheActualRunDirectory() throws Exception {
        Path game = Files.createDirectory(temp.resolve("Sword Art Online"));
        var client = new Client(game.toFile());
        assertTrue(FabricBridge.isExpectedDirectory(client, game));
        assertFalse(FabricBridge.isExpectedDirectory(client, Files.createDirectory(temp.resolve("another"))));
        assertFalse(FabricBridge.isExpectedDirectory(new Object(), game));
    }
}
