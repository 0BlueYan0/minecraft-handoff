package tw.handoff.relay.agent;

import com.sun.tools.attach.VirtualMachine;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Initial loopback bootstrap; remote encrypted service configuration is added with transport integration. */
public final class HostMain {
    public static void main(String[] args){
        Flags flags;
        try{flags=parseFlags(args);}catch(IllegalArgumentException usage){System.out.println("Usage: --pid PID --game-dir INSTANCE [--generic] [--idle-walk]");System.exit(2);return;}
        try{
            long pid=Long.parseLong(args[1]);
            ProcessHandle target=ProcessHandle.of(pid).orElseThrow();
            if(!target.info().user().orElse("").equals(ProcessHandle.current().info().user().orElseThrow())||pid==ProcessHandle.current().pid())throw new IllegalArgumentException("Target ownership mismatch");
            Path game=Path.of(args[3]).toRealPath();
            Path self=Path.of(HostMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            Path directory=Files.createTempDirectory("handoff-relay-");Path status=Files.createFile(directory.resolve("status"));
            try{
                // An attached JVM keeps its agent archive open. Do not lock the distributable/build output.
                Path agentCopy=directory.resolve("relay-agent.jar");Files.copy(self,agentCopy);
                VirtualMachine vm=VirtualMachine.attach(Long.toString(pid));
                try{vm.loadAgent(agentCopy.toString(),agentOptions(status,game,flags.generic(),flags.idleWalk()));}finally{vm.detach();}
                long start=System.nanoTime();
                while(System.nanoTime()-start<60_000_000_000L){
                    String result=Files.readString(status);
                    if(result.startsWith("READY:")||result.startsWith("FAILED:")){
                        if(!result.matches("(?:READY:[0-9]+:[0-9a-f-]{36}:[A-Za-z0-9_]{1,16}|FAILED:[A-Z_]+:[A-Za-z0-9_$]+)"))throw new IllegalStateException("Invalid status");
                        System.out.println(result);if(result.startsWith("FAILED:"))System.exit(1);return;
                    }
                    Thread.sleep(100);
                }
                System.out.println("PENDING: original game remains running");System.exit(3);
            }finally{try{Files.deleteIfExists(status);Files.deleteIfExists(directory);}catch(Exception ignored){}}
        }catch(Exception|LinkageError failure){System.out.println("FAILED:CLIENT:"+failure.getClass().getSimpleName());System.exit(1);}
    }
    record Flags(boolean generic,boolean idleWalk){}
    /** After the four fixed arguments come the optional flags --generic and --idle-walk, each at most once, in any order. */
    static Flags parseFlags(String[] args){
        if(args.length<4||args.length>6||!args[0].equals("--pid")||!args[2].equals("--game-dir"))throw new IllegalArgumentException("usage");
        boolean generic=false,idleWalk=false;
        for(int i=4;i<args.length;i++){
            if(args[i].equals("--generic")&&!generic)generic=true;
            else if(args[i].equals("--idle-walk")&&!idleWalk)idleWalk=true;
            else throw new IllegalArgumentException("usage");
        }
        return new Flags(generic,idleWalk);
    }
    static boolean parseGeneric(String[] args){return parseFlags(args).generic();}
    /** Mode field g or -, then the idle walk field w only when it is on, so a host without it keeps the four-field form. */
    static String agentOptions(Path status,Path game,boolean generic,boolean idleWalk){return agentOptions(status,game,generic)+(idleWalk?".w":"");}
    static String agentOptions(Path status,Path game,boolean generic){return encode(status.toString())+"."+encode(game.toString())+".0."+(generic?"g":"-");}
    private static String encode(String value){return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
}
