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
 @Test void nonFiniteTimerFailsAtCaptureWithFieldPath(){
  var c=fr.sao.saoui.skills.SkillStatusClient.cooldowns;c.put("nan-test",Double.NaN);
  try{assertEquals("skills.cooldowns: non-finite timer",assertThrows(IllegalArgumentException.class,()->SaoSnapshot.captureFields(loader,100)).getMessage());}finally{c.remove("nan-test");}
 }
 @SuppressWarnings({"unchecked","rawtypes"}) @Test void encodeFailureNamesTheField(){
  var skills=(java.util.Map)fr.sao.saoui.skills.SkillStatusClient.allSkills;skills.put(999,new Object());
  try{assertEquals("skills.allSkills: State type is not in the declared schema: java.lang.Object",assertThrows(IllegalArgumentException.class,()->SaoSnapshot.captureFields(loader,100)).getMessage());}finally{skills.remove(999);}
 }
 /** 2026-11-11 live capture: a compass HUD with no target keeps targetAngleDegrees=Float.NaN and must come back as NaN. */
 @Test void questHudWithNaNTargetAngleRoundTrips()throws Exception{
  var states=fr.sao.questtracker.model.QuestHudStore.STATES;
  var hud=new fr.sao.questtracker.model.QuestHudState("compass",fr.sao.questtracker.model.QuestHudKind.COMPASS,fr.sao.questtracker.model.QuestHudAnchor.TOP_CENTER,0,10,1.5f,new fr.sao.questtracker.model.QuestHudStyle(true,4),"\"quest\"","\"objective\"","\"12m\"","\"top\"","\"bottom\"","","",1f,1f,Float.NaN,"target","","{}");
  states.put("compass",hud);
  try{
   var snapshot=SaoSnapshot.captureFields(loader,100);
   assertEquals("NaN",snapshot.getAsJsonArray("quests").get(0).getAsJsonObject().get("targetAngleDegrees").getAsString());
   var plan=SaoSnapshot.prepareFields(loader,snapshot,0,()->0L);
   states.clear();plan.apply();
   Object restored=states.get("compass");assertNotNull(restored);assertNotSame(hud,restored);
   for(String name:SaoSchema.QUEST_FIELDS)if(!name.equals("targetAngleDegrees"))assertEquals(SaoSnapshot.field(hud.getClass(),name).get(hud),SaoSnapshot.field(restored.getClass(),name).get(restored),name);
   assertTrue(Float.isNaN((float)SaoSnapshot.field(restored.getClass(),"targetAngleDegrees").get(restored)));
  }finally{states.clear();}
 }
 @Test void unknownSchemaOrArtifactIsRejected()throws Exception{var s=SaoSnapshot.captureFields(loader,100);s.addProperty("artifact","unknown");assertThrows(IllegalArgumentException.class,()->SaoSnapshot.prepareFields(loader,s,0));}
}
