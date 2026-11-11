package tw.handoff.viewer.bootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
public record GameInstall(Path root,Path client,Path assets,String assetIndex,List<Path> libraries){
    public GameInstall{libraries=List.copyOf(libraries);}
    public record ViewerLaunch(List<String> command,Path directory){
        public ViewerLaunch{command=List.copyOf(command);}
        public String toString(){return "ViewerLaunch[arguments redacted]";}
    }
    public static GameInstall resolve(Path root,Platform platform)throws IOException{
        root=root.toRealPath();JsonObject game=json(root.resolve("versions/1.21.4/1.21.4.json"));
        JsonObject fabric=json(root.resolve("loader/versions/fabric-loader-0.20.1-1.21.4/fabric-loader-0.20.1-1.21.4.json"));
        if(!game.get("id").getAsString().equals("1.21.4")||!fabric.get("mainClass").getAsString().equals("net.fabricmc.loader.impl.launch.knot.KnotClient"))throw new IOException("Unsupported game profile");
        Path client=root.resolve("versions/1.21.4/1.21.4.jar");
        verify(client,game.getAsJsonObject("downloads").getAsJsonObject("client").get("sha1").getAsString());
        Map<String,JsonObject> merged=new LinkedHashMap<>();
        for(JsonObject profile:List.of(game,fabric))for(var item:profile.getAsJsonArray("libraries")){
            JsonObject library=item.getAsJsonObject();if(!platform.allows(library.getAsJsonArray("rules")))continue;
            String[] name=library.get("name").getAsString().split(":");if(name.length<3||name.length>4)throw new IOException("Unsupported library coordinate");
            merged.put(name[0]+":"+name[1]+":"+(name.length==4?name[3]:""),library);
        }
        List<Path> libraries=new ArrayList<>();
        for(JsonObject library:merged.values()){
            JsonObject downloads=library.getAsJsonObject("downloads");
            JsonObject artifact=downloads==null?null:downloads.getAsJsonObject("artifact");
            String name=library.get("name").getAsString();
            String path=artifact!=null&&artifact.has("path")?artifact.get("path").getAsString():maven(name);
            libraries.add(locate(root,path,artifact));
            if(library.has("natives")&&downloads!=null){
                JsonObject natives=library.getAsJsonObject("natives");
                if(natives.has(platform.os())){
                    String classifier=natives.get(platform.os()).getAsString().replace("${arch}",platform.arch().contains("64")?"64":"32");
                    JsonObject nativeArtifact=downloads.getAsJsonObject("classifiers").getAsJsonObject(classifier);
                    if(nativeArtifact==null)throw new IOException("Native library metadata is missing");
                    libraries.add(locate(root,nativeArtifact.get("path").getAsString(),nativeArtifact));
                }
            }
        }
        String index=game.getAsJsonObject("assetIndex").get("id").getAsString();
        Path assets=root.resolve("assets");if(!Files.isRegularFile(safe(assets.resolve("indexes"),index+".json")))throw new IOException("Game assets are missing");
        return new GameInstall(root,client,assets,index,libraries);
    }
    private static JsonObject json(Path file)throws IOException{try(var reader=Files.newBufferedReader(file)){return JsonParser.parseReader(reader).getAsJsonObject();}catch(RuntimeException malformed){throw new IOException("Invalid installed version metadata",malformed);}}
    private static Path locate(Path root,String value,JsonObject artifact)throws IOException{
        Path file=safe(root.resolve("loader/libraries"),value);
        if(!Files.isRegularFile(file))file=safe(root.resolve("libraries"),value);
        if(!Files.isRegularFile(file))throw new IOException("Installed library is missing: "+value);
        verify(file,artifact!=null&&artifact.has("sha1")?artifact.get("sha1").getAsString():null);return file;
    }
    private static String maven(String coordinate)throws IOException{
        String[] n=coordinate.split(":");if(n.length<3||n.length>4)throw new IOException("Invalid Maven coordinate");
        return n[0].replace('.','/')+"/"+n[1]+"/"+n[2]+"/"+n[1]+"-"+n[2]+(n.length==4?"-"+n[3]:"")+".jar";
    }
    public static Path safe(Path root,String value)throws IOException{
        Path relative=Path.of(value);if(relative.isAbsolute())throw new IOException("Absolute metadata path refused");
        Path base=root.toAbsolutePath().normalize(),file=base.resolve(relative).normalize();if(!file.startsWith(base))throw new IOException("Escaping metadata path refused");return file;
    }
    private static void verify(Path file,String expected)throws IOException{
        if(!Files.isRegularFile(file))throw new IOException("Installed game file is missing");if(expected==null)return;
        try(var in=Files.newInputStream(file)){
            MessageDigest hash=MessageDigest.getInstance("SHA-1");byte[] buffer=new byte[64*1024];int n;while((n=in.read(buffer))>=0)if(n>0)hash.update(buffer,0,n);
            if(!HexFormat.of().formatHex(hash.digest()).equalsIgnoreCase(expected))throw new IOException("Installed game checksum mismatch: "+file.getFileName());
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IOException(impossible);}
    }
    public ViewerLaunch launch(Path javaBinary,Path game,Path guard,int port,String name,UUID id)throws IOException{
        if(port<1||port>65535||name==null||!name.matches("[A-Za-z0-9_]{1,16}")||id==null)throw new IllegalArgumentException("Invalid local viewer profile");
        if(!Files.isRegularFile(guard))throw new IOException("Viewer guard JAR is missing");
        Path nativeDir=game.resolve("natives").toAbsolutePath();Files.createDirectories(nativeDir);
        List<String> cp=new ArrayList<>();for(Path lib:libraries)cp.add(lib.toString());cp.add(client.toString());
        List<String> command=new ArrayList<>(List.of(javaBinary.toString(),"-Xms256M","-Xmx4G","-Djava.library.path="+nativeDir,"-Djna.tmpdir="+nativeDir,
            "-Dorg.lwjgl.system.SharedLibraryExtractPath="+nativeDir,"-Dio.netty.native.workdir="+nativeDir,
            "-Dminecraft.launcher.brand=MinecraftHandoff","-Dminecraft.launcher.version=0.2.0","-Dfabric.addMods="+guard.toAbsolutePath(),"-Dhandoff.viewerPort="+port,
            "-cp",String.join(java.io.File.pathSeparator,cp),"net.fabricmc.loader.impl.launch.knot.KnotClient",
            "--username",name,"--uuid",id.toString().replace("-",""),"--accessToken","0","--userType","legacy","--version","1.21.4","--versionType","release",
            "--gameDir",game.toAbsolutePath().toString(),"--assetsDir",assets.toString(),"--assetIndex",assetIndex,"--quickPlayMultiplayer","127.0.0.1:"+port,
            "--width","960","--height","540"));
        return new ViewerLaunch(command,game);
    }
}
