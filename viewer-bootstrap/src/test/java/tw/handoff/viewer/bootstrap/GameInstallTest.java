package tw.handoff.viewer.bootstrap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GameInstallTest {
    @TempDir Path temp;
    private String hash(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));}
    private JsonObject artifact(String name,Path root,String path,String content)throws Exception{
        Path file=root.resolve(path);Files.createDirectories(file.getParent());byte[] data=content.getBytes(java.nio.charset.StandardCharsets.UTF_8);Files.write(file,data);
        JsonObject item=new JsonObject();item.addProperty("name",name);JsonObject download=new JsonObject();download.addProperty("path",path);download.addProperty("sha1",hash(data));JsonObject downloads=new JsonObject();downloads.add("artifact",download);item.add("downloads",downloads);return item;
    }
    private Path install()throws Exception{
        Path root=temp.resolve("install");Files.createDirectories(root.resolve("versions/1.21.4"));
        Files.createDirectories(root.resolve("assets/indexes"));Files.writeString(root.resolve("assets/indexes/19.json"),"{}");
        JsonObject game=new JsonObject();game.addProperty("id","1.21.4");game.addProperty("mainClass","net.minecraft.client.main.Main");
        JsonObject assets=new JsonObject();assets.addProperty("id","19");game.add("assetIndex",assets);
        byte[] jar="client-fixture".getBytes();Files.write(root.resolve("versions/1.21.4/1.21.4.jar"),jar);
        JsonObject client=new JsonObject();client.addProperty("sha1",hash(jar));JsonObject downloads=new JsonObject();downloads.add("client",client);game.add("downloads",downloads);
        JsonArray libs=new JsonArray();libs.add(artifact("example:codec:1",root.resolve("libraries"),"example/codec/1/codec-1.jar","old"));game.add("libraries",libs);
        Files.writeString(root.resolve("versions/1.21.4/1.21.4.json"),game.toString());
        JsonObject fabric=new JsonObject();fabric.addProperty("id","fabric-loader-0.20.1-1.21.4");fabric.addProperty("inheritsFrom","1.21.4");fabric.addProperty("mainClass","net.fabricmc.loader.impl.launch.knot.KnotClient");
        JsonArray loaderLibs=new JsonArray();loaderLibs.add(artifact("example:codec:2",root.resolve("loader/libraries"),"example/codec/2/codec-2.jar","new"));fabric.add("libraries",loaderLibs);
        Path profile=root.resolve("loader/versions/fabric-loader-0.20.1-1.21.4/fabric-loader-0.20.1-1.21.4.json");Files.createDirectories(profile.getParent());Files.writeString(profile,fabric.toString());return root;
    }
    @Test void childLibrariesOverrideParentVersionsAndNoLoginSecretsAreReused()throws Exception{
        Path root=install();var profile=GameInstall.resolve(root,new Platform("windows","amd64","10.0"));
        assertEquals(1,profile.libraries().size());assertTrue(profile.libraries().getFirst().toString().contains("codec-2.jar"));
        Path game=Files.createDirectory(temp.resolve("viewer with spaces")),guard=Files.writeString(temp.resolve("guard.jar"),"guard");
        var launch=profile.launch(Path.of("java.exe"),game,guard,25587,"HandoffViewer",java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"));
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient",launch.command().get(launch.command().indexOf("-cp")+2));
        assertTrue(launch.command().contains("127.0.0.1:25587"));
        assertEquals("0",launch.command().get(launch.command().indexOf("--accessToken")+1));
        assertFalse(launch.command().stream().anyMatch(s->s.contains("launcher.token")||s.contains("launcher.instance")));
        assertEquals("ViewerLaunch[arguments redacted]",launch.toString());
    }
    @Test void corruptedInstallationIsRejectedBeforeLaunching()throws Exception{
        Path root=install();Files.writeString(root.resolve("versions/1.21.4/1.21.4.jar"),"changed");
        assertThrows(java.io.IOException.class,()->GameInstall.resolve(root,new Platform("windows","amd64","10.0")));
    }
    @Test void metadataPathsCannotEscapeLibraryRoots()throws Exception{
        assertThrows(java.io.IOException.class,()->GameInstall.safe(temp,"../private.jar"));
        assertThrows(java.io.IOException.class,()->GameInstall.safe(temp,temp.resolve("absolute.jar").toString()));
    }
}
