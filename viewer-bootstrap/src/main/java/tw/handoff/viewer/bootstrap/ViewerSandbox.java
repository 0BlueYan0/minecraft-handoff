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
        prepare(install,source,viewer,vanilla,true);
    }
    static void prepare(Path install,Path source,Path viewer,boolean vanilla,boolean resourcePacks)throws IOException{
        prepare(install,source,viewer,vanilla,resourcePacks,System.out,1000L);
    }
    private record Item(Path from,Path to,long size){}
    private static String megabytes(long bytes){return String.format(Locale.ROOT,"%.1f",bytes/1048576.0);}
    static void prepare(Path install,Path source,Path viewer,boolean vanilla,boolean resourcePacks,java.io.PrintStream console,long intervalMillis)throws IOException{
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
        OutputGuard guard=new OutputGuard(out);
        Planner plan=new Planner(guard,console,intervalMillis);
        try(var jars=Files.list(source.resolve("mods"))){
            for(Path jar:jars.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.getFileName().toString().endsWith(".jar")).toList()){
                if(vanilla&&!isFabricApi(jar))continue;
                expected.add(jar.getFileName().toString());plan.add(jar,out.resolve("mods").resolve(jar.getFileName()),Files.readAttributes(jar,BasicFileAttributes.class));
            }
        }
        if(!vanilla){
            if(resourcePacks)collectTree(source.resolve("resourcepacks"),out.resolve("resourcepacks"),false,plan);
            collectTree(source.resolve("mods/mcef-libraries"),out.resolve("mods/mcef-libraries"),true,plan);
        }
        java.util.List<Item> pending=plan.pending;long totalBytes=plan.pendingBytes;
        if(pending.isEmpty())console.println("Game files up to date ("+plan.checked+" files)");
        else copyAll(guard,pending,totalBytes,console,intervalMillis,copyThreads(Runtime.getRuntime().availableProcessors()));
        // This marked profile owns its top-level mods mirror. Remove stale versions after a source update.
        try(var old=Files.list(out.resolve("mods"))){for(Path file:old.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.getFileName().toString().endsWith(".jar")).toList())if(!expected.contains(file.getFileName().toString()))Files.delete(output(out,file));}
        Path options=output(out,out.resolve("options.txt"));
        if(!Files.exists(options))Files.writeString(options,"lang:zh_tw\nrenderDistance:6\nsimulationDistance:5\nmaxFps:60\nenableVsync:false\nresourcePacks:"+(vanilla||!resourcePacks?"[\"vanilla\",\"fabric\"]":"[\"vanilla\",\"fabric\",\"file/sao\"]")+"\n");
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
    /** Decides which files need copying with one file-system call per target, and prints progress while doing so. */
    private static final class Planner{
        final OutputGuard guard;final java.io.PrintStream console;final long intervalMillis;
        final java.util.List<Item> pending=new java.util.ArrayList<>();long pendingBytes;int checked;long lastPrint=System.nanoTime();
        Planner(OutputGuard guard,java.io.PrintStream console,long intervalMillis){this.guard=guard;this.console=console;this.intervalMillis=intervalMillis;}
        void add(Path from,Path to,BasicFileAttributes source)throws IOException{
            BasicFileAttributes found=guard.check(to);checked++;
            boolean same=found!=null&&found.isRegularFile()&&found.size()==source.size()&&found.lastModifiedTime().equals(source.lastModifiedTime());
            if(!same){pending.add(new Item(from,to,source.size()));pendingBytes+=source.size();}
            if(System.nanoTime()-lastPrint>=intervalMillis*1_000_000L){lastPrint=System.nanoTime();console.println("Checking game files: "+checked);}
        }
    }
    /**
     * 2026-11-13 實測 12 核心 Windows 複製資源包小檔：1 執行緒 537 檔/秒，8 個 2051，16 個 1971，32 個 1592。
     * 瓶頸在磁碟與防毒掃描，8 個以上不會更快，所以上限 8。
     */
    static int copyThreads(int cores){return Math.max(2,Math.min(8,cores));}

    /**
     * 工作執行緒各自從同一個索引取下一個檔案。進度只由呼叫的執行緒印，數字不會倒退，全部結束後一定印最後一行。
     * 有一個檔案失敗就不再發新的檔案，已經在複製的讓它做完，再把第一個錯誤原樣丟出。沒複製完的檔案大小或修改時間跟來源不同，下次啟動會重新複製。
     */
    private static void copyAll(OutputGuard guard,java.util.List<Item> pending,long totalBytes,java.io.PrintStream console,long intervalMillis,int threads)throws IOException{
        int count=pending.size();String total=megabytes(totalBytes);
        console.println("Copying game files: 0/"+count+" (0.0/"+total+" MB)");
        var next=new java.util.concurrent.atomic.AtomicInteger();
        var done=new java.util.concurrent.atomic.AtomicInteger();
        var doneBytes=new java.util.concurrent.atomic.AtomicLong();
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Runnable work=()->{
            while(failure.get()==null){
                int index=next.getAndIncrement();if(index>=count)return;
                Item item=pending.get(index);
                try{copy(guard,item.from(),item.to());}
                catch(IOException|RuntimeException|Error failed){failure.compareAndSet(null,failed);return;}
                doneBytes.addAndGet(item.size());done.incrementAndGet();
            }
        };
        var workers=new java.util.ArrayList<Thread>();
        for(int i=0;i<Math.min(threads,count);i++){Thread t=new Thread(work,"viewer-copy-"+i);t.setDaemon(true);workers.add(t);t.start();}
        long lastPrint=System.nanoTime(),wait=Math.max(1,Math.min(intervalMillis,200));
        try{
            for(Thread worker:workers){
                while(worker.isAlive()){
                    worker.join(wait);
                    if(worker.isAlive()&&System.nanoTime()-lastPrint>=intervalMillis*1_000_000L){
                        lastPrint=System.nanoTime();
                        long bytes=doneBytes.get();int files=done.get();
                        console.println("Copying game files: "+files+"/"+count+" ("+megabytes(bytes)+"/"+total+" MB)");
                    }
                }
            }
        }catch(InterruptedException stop){
            failure.compareAndSet(null,new java.io.InterruptedIOException("Copying game files interrupted"));
            for(Thread worker:workers)try{worker.join();}catch(InterruptedException again){/* keep waiting so no thread writes after we return */}
            Thread.currentThread().interrupt();
        }
        Throwable failed=failure.get();
        if(failed instanceof IOException io)throw io;
        if(failed instanceof RuntimeException runtime)throw runtime;
        if(failed instanceof Error error)throw error;
        console.println("Copying game files: "+count+"/"+count+" ("+total+"/"+total+" MB)");
    }
    static void copy(OutputGuard guard,Path from,Path to)throws IOException{
        guard.prepareParent(to);
        guard.forWrite(to);
        Files.copy(from,to,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.COPY_ATTRIBUTES);
    }
    private static void collectTree(Path source,Path target,boolean nativeOnly,Planner plan)throws IOException{
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
                plan.add(file,target.resolve(source.relativize(file)),attributes);return FileVisitResult.CONTINUE;
            }
        });
    }
    static void extractNatives(GameInstall install,Path viewer)throws IOException{extractNatives(install,viewer,System.out);}
    static void extractNatives(GameInstall install,Path viewer,java.io.PrintStream console)throws IOException{
        Path target=output(viewer,viewer.resolve("natives"));Files.createDirectories(target);
        OutputGuard guard=new OutputGuard(viewer);
        for(Path jar:install.libraries()){
            if(!jar.getFileName().toString().contains("natives"))continue;
            try(ZipFile zip=new ZipFile(jar.toFile())){
                for(var entry:java.util.Collections.list(zip.entries())){
                    String n=entry.getName().toLowerCase(Locale.ROOT);
                    if(entry.isDirectory()||!(n.endsWith(".dll")||n.endsWith(".so")||n.endsWith(".dylib")))continue;
                    Path file=target.resolve(Path.of(entry.getName()).getFileName().toString());
                    var found=guard.check(file);
                    if(entry.getSize()>=0&&found!=null&&found.isRegularFile()&&found.size()==entry.getSize())continue;
                    guard.forWrite(file);
                    try(var in=zip.getInputStream(entry)){Files.copy(in,file,StandardCopyOption.REPLACE_EXISTING);}
                }
            }
        }
        console.println("Natives ready");
    }
}
