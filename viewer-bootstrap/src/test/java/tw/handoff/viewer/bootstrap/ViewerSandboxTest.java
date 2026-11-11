package tw.handoff.viewer.bootstrap;
import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ViewerSandboxTest {
    @TempDir Path temp;
    @Test void sourceModUpgradeRemovesOldManagedJar()throws Exception{
        Path install=Files.createDirectory(temp.resolve("install")),source=install.resolve("instances/Sword Art Online"),mods=Files.createDirectories(source.resolve("mods")),viewer=temp.resolve("viewer");
        Files.writeString(mods.resolve("old.jar"),"v1");ViewerSandbox.prepare(install,source,viewer,false);
        Files.delete(mods.resolve("old.jar"));Files.writeString(mods.resolve("new.jar"),"v2");ViewerSandbox.prepare(install,source,viewer,false);
        assertFalse(Files.exists(viewer.resolve("mods/old.jar")));assertEquals("v2",Files.readString(viewer.resolve("mods/new.jar")));
    }
    @Test void parentDirectoryLinkCannotRedirectViewerWrites()throws Exception{
        Path install=Files.createDirectory(temp.resolve("install")),source=install.resolve("instances/Sword Art Online"),viewer=temp.resolve("viewer");Files.createDirectories(source.resolve("mods"));Files.createDirectories(source.resolve("resourcepacks/sao"));Files.writeString(source.resolve("resourcepacks/sao/test.txt"),"ORIGINAL");
        ViewerSandbox.prepare(install,source,viewer,false);Files.delete(viewer.resolve("resourcepacks/sao/test.txt"));Files.delete(viewer.resolve("resourcepacks/sao"));Files.delete(viewer.resolve("resourcepacks"));
        try{Files.createSymbolicLink(viewer.resolve("resourcepacks"),source.resolve("resourcepacks"));}catch(java.io.IOException|UnsupportedOperationException denied){org.junit.jupiter.api.Assumptions.abort("Symlink creation unavailable on this Windows account");}
        assertThrows(java.io.IOException.class,()->ViewerSandbox.prepare(install,source,viewer,false));assertEquals("ORIGINAL",Files.readString(source.resolve("resourcepacks/sao/test.txt")));
    }
    @Test void guardIsStagedAsImmutableCopySoGameDoesNotLockDistribution()throws Exception{
        Path game=Files.createDirectory(temp.resolve("viewer")),guard=temp.resolve("guard.jar");Files.writeString(guard,"version1");
        Path one=ViewerSandbox.stageGuard(guard,game);assertNotEquals(guard,one);assertEquals("version1",Files.readString(one));
        Files.writeString(guard,"version2");Path two=ViewerSandbox.stageGuard(guard,game);assertNotEquals(one,two);assertEquals("version1",Files.readString(one));assertEquals("version2",Files.readString(two));
    }
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
