package tw.handoff.probe;
import com.sun.tools.attach.VirtualMachine;
import java.nio.file.*;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
public final class ProbeMain {
 public static void main(String[] a)throws Exception{
  if(a.length!=2||!a[1].matches("status|chat|walk|dig|take|release|moveItem|respawn|pause|labEcho|labStatus|localChange|nativeAttack"))throw new IllegalArgumentException();
  Path status=Files.createTempFile("handoff-private-probe-",".txt");Path self=Path.of(ProbeMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());Path copy=Files.createTempFile("handoff-probe-code-",".jar");Files.copy(self,copy,StandardCopyOption.REPLACE_EXISTING);
  var vm=VirtualMachine.attach(a[0]);try{vm.loadAgent(copy.toString(),a[1]+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(status.toString().getBytes(StandardCharsets.UTF_8)));}finally{vm.detach();}
  long deadline=System.nanoTime()+30_000_000_000L;while(System.nanoTime()<deadline){String result=Files.readString(status);if(!result.isBlank()){System.out.println(result);Files.delete(status);return;}Thread.sleep(100);}throw new IllegalStateException("Private probe timed out");
 }
}
