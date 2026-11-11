package tw.handoff.sao;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** SAOUI auto-updated from 2.51.13 to 2.51.14 on 2026-11-11 and a whole-JAR hash refused it; the contract now pins only the classes it touches. */
class VersionContractTest {
 private final ClassLoader loader=getClass().getClassLoader();
 private static final String OTHER="0".repeat(64);
 @Test void contractCoversEverySchemaAndValueClass(){
  assertTrue(SaoSchema.CONTRACT_CLASSES.containsAll(SaoSchema.CLASSES.values()));
  assertTrue(SaoSchema.CONTRACT_CLASSES.containsAll(SaoSchema.VALUES));
  assertTrue(SaoSchema.CONTRACT_CLASSES.containsAll(List.of("fr.sao.questtracker.model.QuestHudStore","fr.sao.questtracker.model.QuestHudState")));
 }
 @Test void fingerprintChangesWhenAnyContractClassChangesOrIsMissing()throws Exception{
  var bytes=new HashMap<String,byte[]>();for(String name:SaoSchema.CONTRACT_CLASSES)bytes.put(name,name.getBytes());
  String base=SaoSchema.fingerprint(bytes::get);assertEquals(base,SaoSchema.fingerprint(bytes::get));
  for(String name:SaoSchema.CONTRACT_CLASSES){var changed=new HashMap<>(bytes);changed.put(name,new byte[]{1});assertNotEquals(base,SaoSchema.fingerprint(changed::get),name);}
  var missing=new HashMap<>(bytes);missing.remove(SaoSchema.CONTRACT_CLASSES.getFirst());
  assertEquals("SAO version requires a new state contract",assertThrows(IllegalArgumentException.class,()->SaoSchema.fingerprint(missing::get)).getMessage());
 }
 @Test void captureRecordsTheContractFingerprint()throws Exception{
  assertEquals(SaoSchema.CONTRACT_FINGERPRINT,SaoSnapshot.captureFields(loader,100).get("artifact").getAsString());
 }
 @Test void otherFingerprintIsRefusedOnEitherSideWithoutMutation()throws Exception{
  fr.sao.saoui.skills.SkillStatusClient.level=42;
  var foreign=SaoSnapshot.captureFields(loader,100,OTHER);
  assertEquals("Snapshot contract mismatch",assertThrows(IllegalArgumentException.class,()->SaoSnapshot.prepareFields(loader,foreign,0,()->0L,SaoSchema.CONTRACT_FINGERPRINT)).getMessage());
  assertEquals("Snapshot contract mismatch",assertThrows(IllegalArgumentException.class,()->SaoSnapshot.prepareFields(loader,foreign,0,()->0L,OTHER)).getMessage());
  assertEquals(42,fr.sao.saoui.skills.SkillStatusClient.level);
 }
 @Test void nonStringArtifactIsRefused()throws Exception{
  var s=SaoSnapshot.captureFields(loader,100);s.add("artifact",new com.google.gson.JsonObject());
  assertThrows(IllegalArgumentException.class,()->SaoSnapshot.prepareFields(loader,s,0));
 }
}
