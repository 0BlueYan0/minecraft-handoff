package tw.handoff.sao;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SaoSnapshotTest {
 private final ClassLoader loader=getClass().getClassLoader();
 @Test void requiredCollectionsCannotBePreparedAsNull()throws Exception{
  for(String[] item:new String[][]{{"skills","allSkills"},{"skills","durations"},{"equipment","list"},{"mux","SERVED"}}){var s=SaoSnapshot.captureFields(loader,100);s.getAsJsonObject("sections").getAsJsonObject(item[0]).add(item[1],com.google.gson.JsonNull.INSTANCE);assertThrows(IllegalArgumentException.class,()->SaoSnapshot.prepareFields(loader,s,0));}
 }
 @Test void failureRestoresAssignedSetReferenceAndPreviouslyMutatedMaps()throws Exception{
  var state=SaoSnapshot.captureFields(loader,100);var plan=SaoSnapshot.prepareFields(loader,state,0);var old=java.util.Set.of("previous");fr.sao.net.Mux.sendable=old;
  var saved=fr.sao.saoui.skills.SkillStatusClient.allSkills;var failing=new java.util.HashMap<Integer,String>(saved){boolean fail=true;public void putAll(java.util.Map<? extends Integer,? extends String> map){if(fail){fail=false;throw new IllegalStateException("test write failure");}super.putAll(map);}};
  fr.sao.saoui.skills.SkillStatusClient.allSkills=failing;
  try{assertThrows(IllegalStateException.class,plan::apply);assertSame(old,fr.sao.net.Mux.sendable);assertEquals(saved,failing);}finally{fr.sao.saoui.skills.SkillStatusClient.allSkills=saved;}
 }
 @Test void preparedSnapshotAlsoAgesWhileWaitingToApply()throws Exception{
  fr.sao.saoui.skills.SkillStatusClient.cooldowns.put("test",10.0);var state=SaoSnapshot.captureFields(loader,100);long[] clock={0};
  var plan=SaoSnapshot.prepareFields(loader,state,3_000_000_000L,()->clock[0]);clock[0]=2_000_000_000L;plan.apply();assertEquals(5.0,fr.sao.saoui.skills.SkillStatusClient.cooldowns.get("test"));assertThrows(IllegalStateException.class,plan::apply);
 }
 @Test void captureCopiesOnlyDeclaredFieldsAndRestoresCurrentRemainingTime()throws Exception{
  fr.sao.saoui.skills.SkillStatusClient.cooldowns.put("test",10.0);fr.sao.saoui.skills.SkillStatusClient.durations.put("test",20.0);
  var state=SaoSnapshot.captureFields(loader,100);
  assertFalse(state.toString().contains("MUST_NEVER_BE_CAPTURED"));
  var plan=SaoSnapshot.prepareFields(loader,state,3_000_000_000L,()->0L);
  fr.sao.saoui.skills.SkillStatusClient.cooldowns.put("test",99.0);assertEquals(99.0,fr.sao.saoui.skills.SkillStatusClient.cooldowns.get("test"));
  plan.apply();assertEquals(7.0,fr.sao.saoui.skills.SkillStatusClient.cooldowns.get("test"));assertEquals(20.0,fr.sao.saoui.skills.SkillStatusClient.durations.get("test"));
 }
 @Test void invalidLaterSectionDoesNotMutateEarlierState()throws Exception{
  var state=SaoSnapshot.captureFields(loader,100);state.getAsJsonObject("sections").getAsJsonObject("party").addProperty("extraToken","bad");
  fr.sao.saoui.skills.SkillStatusClient.level=42;assertThrows(IllegalArgumentException.class,()->SaoSnapshot.prepareFields(loader,state,0));assertEquals(42,fr.sao.saoui.skills.SkillStatusClient.level);
 }
 @Test void unknownSchemaOrArtifactIsRejected()throws Exception{var s=SaoSnapshot.captureFields(loader,100);s.addProperty("artifact","unknown");assertThrows(IllegalArgumentException.class,()->SaoSnapshot.prepareFields(loader,s,0));}
}
