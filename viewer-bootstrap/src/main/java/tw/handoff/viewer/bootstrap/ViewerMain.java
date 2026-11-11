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
    private static int run(String[] args)throws Exception{
        var values=new HashMap<String,String>();boolean vanilla=false;
        for(int i=0;i<args.length;i++){
            if(args[i].equals("--vanilla")){vanilla=true;continue;}
            if(!args[i].startsWith("--")||i+1==args.length)throw new IllegalArgumentException("Invalid viewer options");values.put(args[i],args[++i]);
        }
        for(String key:List.of("--install","--viewer-dir","--guard","--port"))if(!values.containsKey(key))throw new IllegalArgumentException("Missing viewer option "+key);
        Path install=Path.of(values.get("--install")).toRealPath();Path viewer=Path.of(values.get("--viewer-dir"));Path guard=Path.of(values.get("--guard")).toRealPath();
        int port=Integer.parseInt(values.get("--port"));
        String name=values.getOrDefault("--name","HandoffViewer");UUID id=values.containsKey("--uuid")?UUID.fromString(values.get("--uuid")):UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        GameInstall profile=GameInstall.resolve(install,Platform.current());
        System.out.println("Preparing isolated viewer; no launcher session or login token is copied.");
        ViewerSandbox.prepare(install,install.resolve("instances/Sword Art Online"),viewer,vanilla);
        try(FileChannel lockFile=FileChannel.open(viewer.resolve(".launch-lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=lockFile.tryLock()){
            if(lock==null)throw new IllegalStateException("Viewer profile is already running");
            ViewerSandbox.extractNatives(profile,viewer);
            String binary=System.getProperty("os.name").startsWith("Windows")?"javaw.exe":"java";
            Path javaBinary=values.containsKey("--java")?Path.of(values.get("--java")):Path.of(System.getProperty("java.home"),"bin",binary);
            var launch=profile.launch(javaBinary,viewer,guard,port,name,id);
            ProcessBuilder child=new ProcessBuilder(launch.command()).directory(launch.directory().toFile());
            child.environment().remove("JAVA_TOOL_OPTIONS");child.environment().remove("JDK_JAVA_OPTIONS");child.environment().remove("_JAVA_OPTIONS");
            child.redirectErrorStream(true).redirectOutput(viewer.resolve("launch-console.log").toFile());
            Process process=child.start();System.out.println("Viewer PID "+process.pid()+" started. Minecraft destination is restricted to loopback port "+port+".");
            int result=process.waitFor();System.out.println("Viewer exited: "+result);return result;
        }
    }
}
