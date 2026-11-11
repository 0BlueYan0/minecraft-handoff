package tw.handoff.sao.probe;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class ReadOnlyAttachTest {
 @TempDir Path temp;
 @Test @Timeout(40) void twoRealAttachmentsRefuseUnsupportedArtifactAndPreserveTarget()throws Exception{
  String exe=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();Path game=Files.createDirectory(temp.resolve("game"));
  var target=new ProcessBuilder(exe,"-cp",System.getProperty("java.class.path"),ProbeTargetMain.class.getName(),game.toString()).redirectErrorStream(true).start();
  try{var reader=new java.io.BufferedReader(new java.io.InputStreamReader(target.getInputStream()));assertEquals("READY",reader.readLine());
   for(int i=0;i<2;i++){Path out=temp.resolve("capture"+i+".json");var helper=new ProcessBuilder(exe,"--add-modules","jdk.attach","-jar",System.getProperty("handoff.saoProbeJar"),"--pid",Long.toString(target.pid()),"--game-dir",game.toString(),"--out",out.toString()).redirectErrorStream(true).start();
    try{assertTrue(helper.waitFor(15,TimeUnit.SECONDS));assertEquals(1,helper.exitValue());assertTrue(new String(helper.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("CAPTURE_FAILED:IllegalArgumentException"));assertTrue(target.isAlive());assertFalse(Files.exists(out));}finally{helper.destroyForcibly();}
   }
  }finally{target.destroyForcibly();target.waitFor(5,TimeUnit.SECONDS);}
 }
}
