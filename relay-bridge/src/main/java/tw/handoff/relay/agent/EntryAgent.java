package tw.handoff.relay.agent;

import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EntryAgent {
    private static final AtomicBoolean STARTED=new AtomicBoolean();
    private static volatile String state="PENDING";
    private static volatile boolean startedGeneric,startedIdleWalk;
    private static BridgeLoader bridgeLoader;
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
            String repeat=claim(generic,idleWalk);
            if(repeat!=null){write(channel,repeat.isEmpty()?state:repeat);return;}
            bridgeLoader=new BridgeLoader(EntryAgent.class.getProtectionDomain().getCodeSource().getLocation(),minecraft.getClassLoader());
            Object target=client;
            ((Executor)client).execute(()->{
                try{
                    Class<?> bridge=Class.forName("tw.handoff.relay.bridge.HostBridge",true,bridgeLoader);
                    Object result=bridge.getMethod("start",Object.class,Path.class,int.class,boolean.class,boolean.class).invoke(null,target,game,port,generic,idleWalk);
                    state="READY:"+result;write(channel,state);
                }catch(Exception|LinkageError failure){state="FAILED:BRIDGE:"+rootType(failure);write(channel,state);}
            });
        }catch(Exception|LinkageError failure){write(channel,"FAILED:BOOTSTRAP:"+rootType(failure));}
    }
    /** Agent options: status file, game directory and port (Base64/decimal), then an optional mode field (g = generic packet forwarding, - = off), then an optional idle walk field (w = on, - = off). Three or four fields mean idle walk off, three mean everything off. Returns null for anything invalid. */
    record Options(Path channel,Path game,int port,boolean generic,boolean idleWalk){}
    static Options parse(String options){
        try{
            String[] parts=options.split("\\.",-1);if(parts.length<3||parts.length>5)return null;
            Path channel=Path.of(decode(parts[0])),game=Path.of(decode(parts[1])).toRealPath();int port=Integer.parseInt(parts[2]);
            boolean generic=false,idleWalk=false;if(parts.length>=4){if(parts[3].equals("g"))generic=true;else if(!parts[3].equals("-"))return null;}
            if(parts.length==5){if(parts[4].equals("w"))idleWalk=true;else if(!parts[4].equals("-"))return null;}
            if(!channel.isAbsolute()||!Files.isRegularFile(channel,LinkOption.NOFOLLOW_LINKS)||!Files.isDirectory(game)||port<0||port>65535)return null;
            return new Options(channel,game,port,generic,idleWalk);
        }catch(Exception invalid){return null;}
    }
    /** First attach returns null. A repeat attach returns "" when it asks for the mode already running, or a FAILED status when it asks for a different one, so a second attach never silently keeps the first mode. */
    static synchronized String claim(boolean generic){return claim(generic,false);}
    /** The idle walk setting is held to the same rule as the packet mode. */
    static synchronized String claim(boolean generic,boolean idleWalk){
        if(STARTED.compareAndSet(false,true)){startedGeneric=generic;startedIdleWalk=idleWalk;return null;}
        if(startedGeneric==generic&&startedIdleWalk==idleWalk)return "";
        System.err.println("[handoff-relay] Repeat attach asked for different settings; the running ones are unchanged");
        return "FAILED:MODE:Mismatch";
    }
    static synchronized void resetForTest(){STARTED.set(false);startedGeneric=false;startedIdleWalk=false;state="PENDING";}
    private static String decode(String value){return new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8);}
    private static String rootType(Throwable failure){while(failure.getCause()!=null&&failure.getCause()!=failure)failure=failure.getCause();return failure.getClass().getSimpleName();}
    private static void write(Path path,String value){try{Files.writeString(path,value,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);}catch(Exception ignored){System.err.println("[handoff-relay] Status unavailable");}}
}
