package tw.handoff.sao.probe;
import com.sun.tools.attach.VirtualMachine;
import java.nio.file.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
/** Read-only game state capture. No relay listener, input interception, or server packets. */
public final class StateProbeMain {
 public static void main(String[] args){try{System.exit(run(args));}catch(Exception|LinkageError error){System.out.println("CAPTURE_FAILED:"+error.getClass().getSimpleName());System.exit(1);}}
 private static int run(String[] args)throws Exception{
  if(args.length!=6||!args[0].equals("--pid")||!args[2].equals("--game-dir")||!args[4].equals("--out"))throw new IllegalArgumentException();
  long pid=Long.parseLong(args[1]);var target=ProcessHandle.of(pid).orElseThrow();if(pid==ProcessHandle.current().pid()||!target.info().user().orElse("").equals(ProcessHandle.current().info().user().orElseThrow()))throw new IllegalArgumentException();
  Path game=Path.of(args[3]).toRealPath(),out=Path.of(args[5]).toAbsolutePath().normalize();Files.createDirectories(out.getParent());out=out.getParent().toRealPath().resolve(out.getFileName());
  if(out.startsWith(game)||Files.exists(out,LinkOption.NOFOLLOW_LINKS))throw new IllegalArgumentException("Use a new local output outside the game instance");
  Path temp=Files.createTempDirectory("handoff-sao-readonly-");Path status=Files.createFile(temp.resolve("status"));Path self=Path.of(StateProbeMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());Path copy=temp.resolve("probe.jar");Files.copy(self,copy);
  try{
   var vm=VirtualMachine.attach(Long.toString(pid));try{vm.loadAgent(copy.toString(),encode(status)+"."+encode(game)+"."+encode(out)+"."+encode(copy));}finally{vm.detach();}
   long stop=System.nanoTime()+60_000_000_000L;
   while(System.nanoTime()<stop){String result=Files.readString(status);if(!result.isBlank()){System.out.println(result);return result.startsWith("READ_ONLY_CAPTURED:")?0:1;}Thread.sleep(100);}
   System.out.println("CAPTURE_PENDING: original game was left running");return 3;
  }finally{Files.deleteIfExists(status);}
 }
 private static String encode(Path path){return Base64.getUrlEncoder().withoutPadding().encodeToString(path.toString().getBytes(StandardCharsets.UTF_8));}
}
