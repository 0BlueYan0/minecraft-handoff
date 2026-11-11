package tw.handoff.sao;
import java.net.*;
import java.nio.file.*;
import java.util.jar.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
/** Review round 1: the SHA-256 comparison itself was never reached by a test, so a JAR with a different hash is loaded here. */
class UnknownArtifactTest {
 @TempDir Path dir;
 private URLClassLoader foreignMux()throws Exception{
  Path jar=dir.resolve("other-sao.jar");
  try(var in=getClass().getClassLoader().getResourceAsStream("fr/sao/net/Mux.class");var out=new JarOutputStream(Files.newOutputStream(jar))){
   assertNotNull(in);out.putNextEntry(new JarEntry("fr/sao/net/Mux.class"));in.transferTo(out);out.closeEntry();
  }
  return new URLClassLoader(new URL[]{jar.toUri().toURL()},null);
 }
 @Test void differentArtifactHashFailsClosedOnVerifyCaptureAndPrepare()throws Exception{
  try(var loader=foreignMux()){
   assertEquals(loader,Class.forName("fr.sao.net.Mux",false,loader).getClassLoader());
   var verify=assertThrows(IllegalArgumentException.class,()->SaoSnapshot.verifyArtifact(loader));
   assertEquals("SAO version requires a new state contract",verify.getMessage());
   assertEquals(verify.getMessage(),assertThrows(IllegalArgumentException.class,()->SaoSnapshot.capture(loader,0)).getMessage());
   var doc=SaoSnapshot.captureFields(getClass().getClassLoader(),100);
   fr.sao.saoui.skills.SkillStatusClient.level=42;
   assertEquals(verify.getMessage(),assertThrows(IllegalArgumentException.class,()->SaoSnapshot.prepare(loader,doc,0)).getMessage());
   assertEquals(42,fr.sao.saoui.skills.SkillStatusClient.level);
  }
 }
}
