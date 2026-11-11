package tw.handoff.sao;
import org.junit.jupiter.api.*;
import org.objectweb.asm.*;
import java.util.*;
import java.util.zip.ZipFile;
import java.nio.file.*;
import java.security.*;
import static org.junit.jupiter.api.Assertions.*;
class ArtifactContractTest {
 @Test void installedArtifactHasEveryDeclaredStateFieldAndQuestRecordComponent()throws Exception{
  String path=System.getenv("HANDOFF_SAO_JAR");Assumptions.assumeTrue(path!=null,"Set HANDOFF_SAO_JAR for offline installed-mod contract verification");Path file=Path.of(path);
  assertEquals(SaoSchema.SHA256,HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))));
  try(var zip=new ZipFile(file.toFile())){
   for(var e:SaoSchema.CLASSES.entrySet()){Set<String> names=fields(zip,e.getValue());assertTrue(names.containsAll(SaoSchema.FIELDS.get(e.getKey())),e.getKey());}
   assertTrue(fields(zip,"fr.sao.saoui.skills.SkillStatusClient").contains("lastTickNanos"));
   assertTrue(fields(zip,"fr.sao.questtracker.model.QuestHudState").containsAll(SaoSchema.QUEST_FIELDS));
   assertTrue(fields(zip,"fr.sao.questtracker.model.QuestHudState").contains("targetVisible"));
   var components=new ArrayList<String>();new ClassReader(zip.getInputStream(zip.getEntry("fr/sao/questtracker/messaging/QuestHudPayload.class"))).accept(new ClassVisitor(Opcodes.ASM9){public RecordComponentVisitor visitRecordComponent(String name,String descriptor,String signature){components.add(name);return null;}},ClassReader.SKIP_CODE);
   assertEquals(21,components.size());assertEquals("packetType",components.getFirst());
  }
 }
 private Set<String> fields(ZipFile zip,String name)throws Exception{var out=new HashSet<String>();new ClassReader(zip.getInputStream(zip.getEntry(name.replace('.','/')+".class"))).accept(new ClassVisitor(Opcodes.ASM9){public FieldVisitor visitField(int access,String name,String descriptor,String signature,Object value){out.add(name);return null;}},ClassReader.SKIP_CODE);return out;}
}
