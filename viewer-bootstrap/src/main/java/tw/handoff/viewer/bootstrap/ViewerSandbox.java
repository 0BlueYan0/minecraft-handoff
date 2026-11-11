package tw.handoff.viewer.bootstrap;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.zip.ZipFile;
import com.google.gson.JsonParser;

final class ViewerSandbox {
    private static final String MARKER="MinecraftHandoffViewer:1";
    static Path output(Path root,Path target)throws IOException{
        Path base=root.toRealPath(),file=target.toAbsolutePath().normalize();
        if(!file.startsWith(base))throw new IOException("Escaping viewer output refused");
        Path cursor=base;for(Path part:base.relativize(file)){
            cursor=cursor.resolve(part);if(Files.exists(cursor,LinkOption.NOFOLLOW_LINKS)&&(Files.isSymbolicLink(cursor)||!cursor.toRealPath().startsWith(base)))throw new IOException("Linked viewer output refused");
        }return file;
    }
    static Path stageGuard(Path source,Path viewer)throws Exception{
        byte[] bytes=Files.readAllBytes(source);String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        Path code=output(viewer,viewer.resolve("handoff-code"));Files.createDirectories(code);
        Path target=code.resolve("viewer-"+hash+".jar");if(Files.isSymbolicLink(target))throw new IOException("Symbolic code file refused");
        if(!Files.exists(target))Files.write(target,bytes,java.nio.file.StandardOpenOption.CREATE_NEW);
        else if(!java.util.Arrays.equals(bytes,Files.readAllBytes(target)))throw new IOException("Staged viewer code checksum mismatch");return target;
    }
    static void claim(Path install,Path viewer)throws IOException{
        Path input=install.toRealPath(),out=viewer.toAbsolutePath().normalize();
        if(out.startsWith(input)||input.startsWith(out))throw new IOException("Viewer data must be separate from the managed installation");
        Path marker=out.resolve(".handoff-viewer");
        if(Files.isDirectory(out)&&!Files.isRegularFile(marker))try(var entries=Files.list(out)){if(entries.findAny().isPresent())throw new IOException("Refusing an unowned viewer directory");}
        Files.createDirectories(out);out=out.toRealPath();if(out.startsWith(input)||input.startsWith(out))throw new IOException("Viewer directory resolves into managed data");
        marker=out.resolve(".handoff-viewer");if(Files.isSymbolicLink(marker))throw new IOException("Symbolic marker refused");
        if(Files.exists(marker)&&!Files.readString(marker).equals(MARKER))throw new IOException("Invalid viewer directory marker");Files.writeString(marker,MARKER);
    }
    static void prepare(Path install,Path source,Path viewer,boolean vanilla)throws IOException{
        claim(install,viewer);
        Path input=install.toRealPath(),out=viewer.toAbsolutePath().normalize();
        if(out.startsWith(input)||input.startsWith(out))throw new IOException("Viewer data must be separate from the managed installation");
        if(!Files.isDirectory(source.resolve("mods")))throw new IOException("Installed instance mods are missing");
        Path marker=out.resolve(".handoff-viewer");
        if(Files.isDirectory(out)&&!Files.isRegularFile(marker))try(var entries=Files.list(out)){if(entries.findAny().isPresent())throw new IOException("Refusing an unowned viewer directory");}
        Files.createDirectories(out);out=out.toRealPath();
        if(out.startsWith(input)||input.startsWith(out))throw new IOException("Viewer directory resolves into managed data");
        marker=out.resolve(".handoff-viewer");
        if(Files.exists(marker)&&!Files.readString(marker).equals(MARKER))throw new IOException("Invalid viewer directory marker");
        Files.writeString(marker,MARKER);Files.createDirectories(output(out,out.resolve("mods")));Files.createDirectories(output(out,out.resolve("config")));
        java.util.Set<String> expected=new java.util.HashSet<>();
        try(var jars=Files.list(source.resolve("mods"))){
            for(Path jar:jars.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.getFileName().toString().endsWith(".jar")).toList()){
                if(vanilla&&!isFabricApi(jar))continue;
                expected.add(jar.getFileName().toString());copy(jar,output(out,out.resolve("mods").resolve(jar.getFileName())));
            }
        }
        // This marked profile owns its top-level mods mirror. Remove stale versions after a source update.
        try(var old=Files.list(out.resolve("mods"))){for(Path file:old.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.getFileName().toString().endsWith(".jar")).toList())if(!expected.contains(file.getFileName().toString()))Files.delete(output(out,file));}
        if(!vanilla){
            copyTree(source.resolve("resourcepacks"),out,out.resolve("resourcepacks"),false);
            copyTree(source.resolve("mods/mcef-libraries"),out,out.resolve("mods/mcef-libraries"),true);
        }
        Path options=output(out,out.resolve("options.txt"));
        if(!Files.exists(options))Files.writeString(options,"lang:zh_tw\nrenderDistance:6\nsimulationDistance:5\nmaxFps:60\nenableVsync:false\nresourcePacks:"+(vanilla?"[\"vanilla\",\"fabric\"]":"[\"vanilla\",\"fabric\",\"file/sao\"]")+"\n");
        Path iris=output(out,out.resolve("config/iris.properties"));if(!Files.exists(iris))Files.writeString(iris,"enableShaders=false\n");
    }
    private static boolean isFabricApi(Path jar)throws IOException{
        try(ZipFile zip=new ZipFile(jar.toFile())){
            var entry=zip.getEntry("fabric.mod.json");if(entry==null)return false;
            try(var reader=new java.io.InputStreamReader(zip.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)){
                return JsonParser.parseReader(reader).getAsJsonObject().get("id").getAsString().equals("fabric-api");
            }
        }
    }
    private static void copy(Path from,Path to)throws IOException{
        Files.createDirectories(to.getParent());
        if(Files.isSymbolicLink(to)||Files.isSymbolicLink(to.getParent()))throw new IOException("Symbolic viewer file refused");
        if(Files.isRegularFile(to)&&Files.size(to)==Files.size(from)&&Files.getLastModifiedTime(to).equals(Files.getLastModifiedTime(from)))return;
        Files.copy(from,to,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.COPY_ATTRIBUTES);
    }
    private static void copyTree(Path source,Path root,Path target,boolean nativeOnly)throws IOException{
        if(!Files.isDirectory(source,LinkOption.NOFOLLOW_LINKS))return;
        Files.walkFileTree(source,new SimpleFileVisitor<>(){
            public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attributes){
                String n=path.getFileName().toString().toLowerCase(Locale.ROOT);
                return nativeOnly&&(n.contains("cache")||n.contains("profile")||n.equals("user data")||n.equals("default"))?FileVisitResult.SKIP_SUBTREE:FileVisitResult.CONTINUE;
            }
            public FileVisitResult visitFile(Path file,BasicFileAttributes attributes)throws IOException{
                if(!attributes.isRegularFile()||Files.isSymbolicLink(file))return FileVisitResult.CONTINUE;
                String n=file.getFileName().toString().toLowerCase(Locale.ROOT);
                if(nativeOnly&&!n.matches(".*\\.(dll|exe|pak|dat|bin|json|txt|plist|dylib|so|icns|sig)"))return FileVisitResult.CONTINUE;
                copy(file,output(root,target.resolve(source.relativize(file))));return FileVisitResult.CONTINUE;
            }
        });
    }
    static void extractNatives(GameInstall install,Path viewer)throws IOException{
        Path target=output(viewer,viewer.resolve("natives"));Files.createDirectories(target);
        for(Path jar:install.libraries()){
            if(!jar.getFileName().toString().contains("natives"))continue;
            try(ZipFile zip=new ZipFile(jar.toFile())){
                for(var entry:java.util.Collections.list(zip.entries())){
                    String n=entry.getName().toLowerCase(Locale.ROOT);
                    if(entry.isDirectory()||!(n.endsWith(".dll")||n.endsWith(".so")||n.endsWith(".dylib")))continue;
                    Path file=output(viewer,target.resolve(Path.of(entry.getName()).getFileName().toString()));
                    try(var in=zip.getInputStream(entry)){Files.copy(in,file,StandardCopyOption.REPLACE_EXISTING);}
                }
            }
        }
    }
}
