package tw.handoff.viewer.bootstrap;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.UUID;
import java.util.List;

/** Local render profile only. Never contacts Triggers to obtain a launch session. */
public final class ViewerMain {
    public static void main(String[] args){
        try{System.exit(run(args));}catch(Exception failure){System.out.println("VIEWER_FAILED: "+failure.getClass().getSimpleName()+": "+safeMessage(failure));System.exit(1);}
    }
    private static String safeMessage(Exception error){
        String value=error.getMessage();if(value==null)return "See the viewer log";
        return value.matches("[A-Za-z0-9 _.,:/\\\\()@+=$;-]{1,250}")?value:"See the viewer log";
    }
    public static int run(String[] args)throws Exception{
        var values=new HashMap<String,String>();boolean vanilla=false,fixture=false,lab=false,generic=false,noFixturePacks=false;
        for(int i=0;i<args.length;i++){
            if(args[i].equals("--vanilla")){vanilla=true;continue;}
            if(args[i].equals("--host-fixture")){fixture=true;continue;}
            if(args[i].equals("--packet-lab")){lab=true;continue;}
            if(args[i].equals("--generic")){generic=true;continue;}
            if(args[i].equals("--fixture-no-resourcepacks")){noFixturePacks=true;continue;}
            if(!args[i].startsWith("--")||i+1==args.length)throw new IllegalArgumentException("Invalid viewer options");values.put(args[i],args[++i]);
        }
        if(lab&&generic)throw new IllegalArgumentException("--generic and --packet-lab are mutually exclusive");
        for(String key:List.of("--install","--viewer-dir","--guard","--port"))if(!values.containsKey(key))throw new IllegalArgumentException("Missing viewer option "+key);
        String xmx=values.get("--fixture-xmx");
        // A fixture runs next to other JVMs on one machine, so its heap can be capped below the 4G launch default.
        if(xmx!=null&&((!fixture&&!lab&&!generic)||!xmx.matches("[1-9][0-9]{0,3}[MG]")))throw new IllegalArgumentException("Heap override is for private fixtures only");
        if(noFixturePacks&&!fixture)throw new IllegalArgumentException("Optional pack suppression is for private model fixtures only");
        Path install=Path.of(values.get("--install")).toRealPath();Path viewer=Path.of(values.get("--viewer-dir"));Path guard=Path.of(values.get("--guard")).toRealPath();
        int port=Integer.parseInt(values.get("--port"));
        String name=values.getOrDefault("--name","HandoffViewer");UUID id=values.containsKey("--uuid")?UUID.fromString(values.get("--uuid")):UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        GameInstall profile=GameInstall.resolve(install,Platform.current());
        System.out.println("Preparing isolated viewer; no launcher session or login token is copied.");
        ViewerSandbox.claim(install,viewer);
        try(FileChannel lockFile=FileChannel.open(ViewerSandbox.output(viewer,viewer.resolve(".launch-lock")),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=lockFile.tryLock()){
            if(lock==null)throw new IllegalStateException("Viewer profile is already running");
            try(var childFile=FileChannel.open(ViewerSandbox.output(viewer,viewer.resolve(".game-lock")),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var childLock=childFile.tryLock()){
                if(childLock==null)throw new IllegalStateException("Viewer game is already running; close its window first");
            }
            ViewerSandbox.prepare(install,install.resolve("instances/Sword Art Online"),viewer,vanilla,!noFixturePacks);
            ViewerSandbox.extractNatives(profile,viewer);
            String binary=System.getProperty("os.name").startsWith("Windows")?"javaw.exe":"java";
            Path javaBinary=values.containsKey("--java")?Path.of(values.get("--java")):Path.of(System.getProperty("java.home"),"bin",binary);
            var launch=profile.launch(javaBinary,viewer,ViewerSandbox.stageGuard(guard,viewer),port,name,id,!fixture&&!lab&&!generic);
            var command=new java.util.ArrayList<>(launch.command());
            if(lab||generic){command.add(1,lab?"-Dhandoff.packetLab=true":"-Dhandoff.generic=true");if(fixture)command.add(1,"-Dhandoff.labHost=true");}
            if(xmx!=null)for(int i=0;i<command.size();i++)if(command.get(i).startsWith("-Xmx"))command.set(i,"-Xmx"+xmx);
            // --fixture-mod takes one or more jars separated by the platform path separator (';' on Windows).
            String extra=values.get("--fixture-mod");if(extra!=null){if(!lab&&!generic)throw new IllegalArgumentException("Fixture mod requires lab or generic mode");for(int i=0;i<command.size();i++)if(command.get(i).startsWith("-Dfabric.addMods=")){String joined=command.get(i);for(String jar:extra.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))if(!jar.isBlank())joined+=java.io.File.pathSeparator+Path.of(jar).toRealPath();command.set(i,joined);}}
            ProcessBuilder child=new ProcessBuilder(command).directory(launch.directory().toFile());
            child.environment().remove("JAVA_TOOL_OPTIONS");child.environment().remove("JDK_JAVA_OPTIONS");child.environment().remove("_JAVA_OPTIONS");
            child.redirectErrorStream(true).redirectOutput(ViewerSandbox.output(viewer,viewer.resolve("launch-console.log")).toFile());
            Process process=child.start();System.out.println("Viewer PID "+process.pid()+" started. Minecraft destination is restricted to loopback port "+port+".");
            LaunchProgress.start(ViewerSandbox.output(viewer,viewer.resolve("launch-console.log")),process::isAlive,System.out,10_000L,500L);
            int result=process.waitFor();System.out.println("Viewer exited: "+result);return result;
        }
    }
}
