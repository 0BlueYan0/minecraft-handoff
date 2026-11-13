package tw.handoff.relay.agent;

import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.Executor;
import java.util.jar.JarFile;

public final class EntryAgent {
    /**
     * Version of this package (tw.handoff.relay.agent). A game JVM keeps the first EntryAgent it was attached with, so a newer jar cannot replace it.
     * Raise this number, and Handoff-Agent-Version in relay-bridge/build.gradle with it, whenever anything under tw.handoff.relay.agent changes.
     */
    static final int AGENT_VERSION=1;
    static final String VERSION_ATTRIBUTE="Handoff-Agent-Version";
    private static volatile String state="PENDING";
    // What the latest attach asked for. runningSha is null while nothing is running or the last start failed, so the next attach starts again.
    private static String runningSha;
    private static boolean runningGeneric,runningIdleWalk;
    public static void agentmain(String options,Instrumentation instrumentation){
        Options parsed=parse(options);if(parsed==null)return;
        Path channel=parsed.channel(),game=parsed.game();int port=parsed.port();boolean generic=parsed.generic(),idleWalk=parsed.idleWalk();
        write(channel,"PENDING");
        try{
            Class<?> minecraft=null;
            for(Class<?> type:instrumentation.getAllLoadedClasses())if(type.getName().equals("net.minecraft.class_310")||type.getName().equals("net.minecraft.client.Minecraft")){
                if(minecraft!=null)throw new IllegalStateException("Ambiguous game client");minecraft=type;
            }
            if(minecraft==null)throw new IllegalStateException("Game not initialized");
            Object client=null;
            for(String accessor:new String[]{"method_1551","getInstance"}){
                try{client=minecraft.getMethod(accessor).invoke(null);break;}catch(NoSuchMethodException absent){}
            }
            if(client==null)throw new IllegalStateException("Game not initialized");
            // Validate on every attach, including retries and an already-installed bridge.
            GameIdentity.require(client,game);
            // This class is the one from the first attach, so the jar that was just attached is the one named in the options, not our own code source.
            Path jar=parsed.jar()!=null?parsed.jar():codeSourceJar();
            String sha=sha256(jar);int agentVersion=jarAgentVersion(jar);
            Action action=claim(sha,agentVersion,generic,idleWalk);
            if(action==Action.RESTART_GAME){System.err.println("[handoff-relay] The attached jar needs a newer agent than the one in this game; restart the game once");write(channel,RESTART_GAME_STATUS);return;}
            if(action==Action.KEEP){write(channel,state);return;}
            Object target=client;ClassLoader gameLoader=minecraft.getClassLoader();
            ((Executor)client).execute(()->{
                try{
                    state="READY:"+swap(()->loadBridge(target,game,port,generic,idleWalk,jar,gameLoader));write(channel,state);
                }catch(StopFailure failure){unclaim();state="FAILED:STOP:"+rootType(failure);write(channel,state);}
                catch(Exception|LinkageError failure){unclaim();state="FAILED:BRIDGE:"+rootType(failure);write(channel,state);}
            });
        }catch(Exception|LinkageError failure){write(channel,"FAILED:BOOTSTRAP:"+rootType(failure));}
    }
    static final String RESTART_GAME_STATUS="FAILED:AGENT:RestartGame";
    /** Stops the bridge that is running. */
    interface Stopper{void stop()throws Exception;}
    /** Starts a bridge and returns its status text and how to stop it again. */
    interface Starter{Started start()throws Exception;}
    record Started(String result,Stopper stopper){}
    private static Stopper runningStopper;
    /** Production starter: a new loader on the attached jar, then HostBridge.start. The old loader is never closed, because Fabric listeners and Netty callbacks of a stopped bridge can still load classes from it. */
    private static Started loadBridge(Object client,Path game,int port,boolean generic,boolean idleWalk,Path jar,ClassLoader gameLoader)throws Exception{
        BridgeLoader loader=new BridgeLoader(jar.toUri().toURL(),gameLoader);
        Class<?> bridge=Class.forName("tw.handoff.relay.bridge.HostBridge",true,loader);
        String result=(String)bridge.getMethod("start",Object.class,Path.class,int.class,boolean.class,boolean.class).invoke(null,client,game,port,generic,idleWalk);
        return new Started(result,()->bridge.getMethod("stop",String.class).invoke(null,"Host updating; reconnect"));
    }
    /**
     * Old bridge first, then the new one. A bridge that cannot be stopped is kept as the running one and never replaced, so two bridges are never active at once.
     * A stop failure throws StopFailure and keeps the stopper, so the next attach tries the stop again. A start failure leaves nothing running.
     */
    static synchronized String swap(Starter starter)throws Exception{
        if(runningStopper!=null){
            try{runningStopper.stop();}
            catch(Exception|LinkageError failure){throw new StopFailure(failure);}
            runningStopper=null;
        }
        Started started=starter.start();
        runningStopper=started.stopper();
        return started.result();
    }
    static synchronized boolean hasRunningBridge(){return runningStopper!=null;}
    static final class StopFailure extends Exception{StopFailure(Throwable cause){super(cause);}}
    /** Agent options: status file, game directory and port (Base64/decimal), then an optional mode field (g = generic packet forwarding, - = off), then an optional idle walk field (w = on, - = off), then an optional jar field (Base64 path of the jar that was attached). Three or four fields mean idle walk off, three mean everything off, and without the jar field the agent's own code source is used. Returns null for anything invalid. */
    record Options(Path channel,Path game,int port,boolean generic,boolean idleWalk,Path jar){}
    static Options parse(String options){
        try{
            String[] parts=options.split("\\.",-1);if(parts.length<3||parts.length>6)return null;
            Path channel=Path.of(decode(parts[0])),game=Path.of(decode(parts[1])).toRealPath();int port=Integer.parseInt(parts[2]);
            boolean generic=false,idleWalk=false;Path jar=null;if(parts.length>=4){if(parts[3].equals("g"))generic=true;else if(!parts[3].equals("-"))return null;}
            if(parts.length>=5){if(parts[4].equals("w"))idleWalk=true;else if(!parts[4].equals("-"))return null;}
            if(parts.length==6){jar=Path.of(decode(parts[5])).toRealPath();if(!jar.isAbsolute()||!Files.isRegularFile(jar))return null;}
            if(!channel.isAbsolute()||!Files.isRegularFile(channel,LinkOption.NOFOLLOW_LINKS)||!Files.isDirectory(game)||port<0||port>65535)return null;
            return new Options(channel,game,port,generic,idleWalk,jar);
        }catch(Exception invalid){return null;}
    }
    enum Action{
        /** Nothing is running: load the jar. */START,
        /** A different jar or different settings than the running ones: stop the running bridge, then load the jar. */RELOAD,
        /** The same jar and settings are already running. */KEEP,
        /** The jar needs a different tw.handoff.relay.agent than this game holds. */RESTART_GAME}
    /** The first attach starts. A repeat attach with the same jar and settings keeps what runs; a different jar or settings reloads; an agent version this game cannot load asks for a restart and changes nothing. */
    static synchronized Action claim(String sha,int agentVersion,boolean generic,boolean idleWalk){
        if(agentVersion!=AGENT_VERSION)return Action.RESTART_GAME;
        if(runningSha==null){runningSha=sha;runningGeneric=generic;runningIdleWalk=idleWalk;return Action.START;}
        if(runningSha.equals(sha)&&runningGeneric==generic&&runningIdleWalk==idleWalk)return Action.KEEP;
        runningSha=sha;runningGeneric=generic;runningIdleWalk=idleWalk;return Action.RELOAD;
    }
    /** A failed start or stop leaves nothing known to be running, so the next attach goes through swap again. */
    static synchronized void unclaim(){runningSha=null;}
    static synchronized void resetForTest(){runningSha=null;runningGeneric=false;runningIdleWalk=false;state="PENDING";runningStopper=null;}
    static Path codeSourceJar()throws Exception{return Path.of(EntryAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();}
    static String sha256(Path file)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(file)){byte[] buffer=new byte[65536];int read;while((read=in.read(buffer))>0)digest.update(buffer,0,read);}
        return HexFormat.of().formatHex(digest.digest());
    }
    /** The Handoff-Agent-Version of a jar, or -1 when it has none, so a jar from before this attribute asks for a restart. */
    static int jarAgentVersion(Path jar){
        try(JarFile file=new JarFile(jar.toFile())){var manifest=file.getManifest();String value=manifest==null?null:manifest.getMainAttributes().getValue(VERSION_ATTRIBUTE);return value==null?-1:Integer.parseInt(value.trim());}
        catch(Exception unreadable){return -1;}
    }
    private static String decode(String value){return new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8);}
    private static String rootType(Throwable failure){while(failure.getCause()!=null&&failure.getCause()!=failure)failure=failure.getCause();return failure.getClass().getSimpleName();}
    private static void write(Path path,String value){try{Files.writeString(path,value,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);}catch(Exception ignored){System.err.println("[handoff-relay] Status unavailable");}}
}
