package tw.handoff.viewer.bootstrap;
import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ViewerSandboxTest {
    @TempDir Path temp;
    @Test void copiesGameCodeAndAssetsButNotPrivateGameData()throws Exception{
        Path install=Files.createDirectory(temp.resolve("install"));Path source=install.resolve("instances/Sword Art Online");
        Files.createDirectories(source.resolve("mods"));Files.writeString(source.resolve("mods/mod.jar"),"code");
        Files.createDirectories(source.resolve("resourcepacks/sao"));Files.writeString(source.resolve("resourcepacks/sao/pack.mcmeta"),"{}");
        Files.createDirectories(source.resolve("config"));Files.writeString(source.resolve("config/account.json"),"TEST_PRIVATE");
        Files.createDirectories(source.resolve("saves"));Files.writeString(source.resolve("saves/private.txt"),"TEST_PRIVATE");
        Path viewer=temp.resolve("viewer");ViewerSandbox.prepare(install,source,viewer,false);
        assertEquals("code",Files.readString(viewer.resolve("mods/mod.jar")));
        assertTrue(Files.isRegularFile(viewer.resolve("resourcepacks/sao/pack.mcmeta")));
        assertFalse(Files.exists(viewer.resolve("saves/private.txt")));assertFalse(Files.exists(viewer.resolve("config/account.json")));
        Files.writeString(viewer.resolve("options.txt"),"user settings");ViewerSandbox.prepare(install,source,viewer,false);
        assertEquals("user settings",Files.readString(viewer.resolve("options.txt")));
        assertEquals("code",Files.readString(source.resolve("mods/mod.jar")));
    }
    @Test void refusesManagedOrUnownedWritableDirectories()throws Exception{
        Path install=Files.createDirectory(temp.resolve("install"));Path source=Files.createDirectories(install.resolve("instances/Sword Art Online/mods")).getParent();
        assertThrows(java.io.IOException.class,()->ViewerSandbox.prepare(install,source,source,false));
        Path occupied=Files.createDirectory(temp.resolve("occupied"));Files.writeString(occupied.resolve("private.txt"),"unchanged");
        assertThrows(java.io.IOException.class,()->ViewerSandbox.prepare(install,source,occupied,false));
        assertEquals("unchanged",Files.readString(occupied.resolve("private.txt")));
    }
}
