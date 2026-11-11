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
    private static BridgeLoader bridgeLoader;
    public static void agentmain(String options,Instrumentation instrumentation){
        Path channel,game;int port;
        try{
            String[] parts=options.split("\\.",-1);if(parts.length!=3)return;
            channel=Path.of(decode(parts[0]));game=Path.of(decode(parts[1])).toRealPath();port=Integer.parseInt(parts[2]);
            if(!channel.isAbsolute()||!Files.isRegularFile(channel,LinkOption.NOFOLLOW_LINKS)||!Files.isDirectory(game)||port<0||port>65535)return;
        }catch(Exception invalid){return;}
        if(!STARTED.compareAndSet(false,true)){write(channel,state);return;}
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
            bridgeLoader=new BridgeLoader(EntryAgent.class.getProtectionDomain().getCodeSource().getLocation(),minecraft.getClassLoader());
            Object target=client;
            ((Executor)client).execute(()->{
                try{
                    Class<?> bridge=Class.forName("tw.handoff.relay.bridge.HostBridge",true,bridgeLoader);
                    Object result=bridge.getMethod("start",Object.class,Path.class,int.class).invoke(null,target,game,port);
                    state="READY:"+result;write(channel,state);
                }catch(Exception|LinkageError failure){state="FAILED:BRIDGE:"+rootType(failure);write(channel,state);}
            });
        }catch(Exception|LinkageError failure){state="FAILED:BOOTSTRAP:"+rootType(failure);write(channel,state);}
    }
    private static String decode(String value){return new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8);}
    private static String rootType(Throwable failure){while(failure.getCause()!=null&&failure.getCause()!=failure)failure=failure.getCause();return failure.getClass().getSimpleName();}
    private static void write(Path path,String value){try{Files.writeString(path,value,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);}catch(Exception ignored){System.err.println("[handoff-relay] Status unavailable");}}
}
