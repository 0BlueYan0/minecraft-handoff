package tw.handoff.sao;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TimerSnapshotTest {
 private static final long SECOND=1_000_000_000L;
 private final ClassLoader loader=getClass().getClassLoader();
 private Class<?> weapon,shield;
 @BeforeEach void clean()throws Exception{weapon=Class.forName(SaoSchema.WEAPON_CLASS);shield=Class.forName(SaoSchema.SHIELD_CLASS);reset();}
 @AfterEach void restore()throws Exception{reset();}
 private void reset()throws Exception{ends().clear();starts().clear();SaoSnapshot.field(shield,"lockMillis").setLong(null,0);SaoSnapshot.field(shield,"lockReceivedNanos").setLong(null,0);}
 @SuppressWarnings("unchecked") private Map<String,Long> ends()throws Exception{return (Map<String,Long>)SaoSnapshot.field(weapon,"cooldownEndNanos").get(null);}
 @SuppressWarnings("unchecked") private Map<String,Long> starts()throws Exception{return (Map<String,Long>)SaoSnapshot.field(weapon,"cooldownStartNanos").get(null);}
 private long lockMillis()throws Exception{return SaoSnapshot.field(shield,"lockMillis").getLong(null);}
 private long lockReceived()throws Exception{return SaoSnapshot.field(shield,"lockReceivedNanos").getLong(null);}

 @Test void weaponCooldownAgesByElapsedOnly()throws Exception{
  long now=System.nanoTime();ends().put("sword",now+5*SECOND);starts().put("sword",now-SECOND);
  var state=SaoSnapshot.captureFields(loader,now);
  var entry=state.getAsJsonObject("timers").getAsJsonObject("weapon").getAsJsonObject("sword");
  assertEquals(5*SECOND,entry.get("remainingNanos").getAsLong());assertEquals(SECOND,entry.get("startAgoNanos").getAsLong());
  var plan=SaoSnapshot.prepareFields(loader,state,2*SECOND);ends().clear();starts().clear();plan.apply();
  long left=ends().get("sword")-System.nanoTime();assertTrue(left<=3*SECOND&&left>2_900_000_000L,"remaining "+left);
  long ago=System.nanoTime()-starts().get("sword");assertTrue(ago>=3*SECOND&&ago<3_100_000_000L,"startAgo "+ago);
 }
 @Test void weaponCooldownAlsoAgesWhileWaitingToApply()throws Exception{
  long now=System.nanoTime();ends().put("sword",now+5*SECOND);starts().put("sword",now-SECOND);
  var state=SaoSnapshot.captureFields(loader,now);long[] clock={0};
  var plan=SaoSnapshot.prepareFields(loader,state,2*SECOND,()->clock[0]);clock[0]=SECOND;plan.apply();
  assertEquals(3*SECOND,ends().get("sword"));assertEquals(-3*SECOND,starts().get("sword"));
 }
 private com.google.gson.JsonObject weaponEntry(String key,Number remaining,Number startAgo)throws Exception{
  var state=SaoSnapshot.captureFields(loader,100);var item=new com.google.gson.JsonObject();item.addProperty("remainingNanos",remaining);if(startAgo!=null)item.addProperty("startAgoNanos",startAgo);
  state.getAsJsonObject("timers").getAsJsonObject("weapon").add(key,item);return state;
 }
 @Test void invalidTimerValuesAreRefusedAsIllegalArgument()throws Exception{
  assertEquals("Invalid timer state",assertThrows(IllegalArgumentException.class,()->SaoSnapshot.prepareFields(loader,weaponEntry("a".repeat(1025),SECOND,null),0)).getMessage());
  SaoSnapshot.prepareFields(loader,weaponEntry("a".repeat(1024),SECOND,null),0);
  for(Number bad:new Number[]{-1L,1.5,Long.MAX_VALUE*2.0})assertThrows(IllegalArgumentException.class,()->SaoSnapshot.prepareFields(loader,weaponEntry("sword",bad,null),0),"remaining "+bad);
  assertThrows(IllegalArgumentException.class,()->SaoSnapshot.prepareFields(loader,weaponEntry("sword",SECOND,-1L),0));
  assertThrows(IllegalArgumentException.class,()->SaoSnapshot.prepareFields(loader,weaponEntry("sword",5*SECOND,Long.MAX_VALUE),2*SECOND),"startAgo overflow");
  for(long bad:new long[]{-1,Long.MAX_VALUE}){var state=SaoSnapshot.captureFields(loader,100);state.getAsJsonObject("timers").getAsJsonObject("shield").addProperty("lockRemainingMillis",bad);assertThrows(IllegalArgumentException.class,()->SaoSnapshot.prepareFields(loader,state,0),"shield "+bad);}
 }
 @Test void captureRefusesOversizedWeaponKey()throws Exception{
  long now=System.nanoTime();ends().put("a".repeat(1025),now+SECOND);
  assertThrows(IllegalArgumentException.class,()->SaoSnapshot.captureFields(loader,now));
 }
 @Test void expiredWeaponKeyDropped()throws Exception{
  long now=System.nanoTime();ends().put("sword",now+SECOND);starts().put("sword",now-SECOND);
  var state=SaoSnapshot.captureFields(loader,now);var plan=SaoSnapshot.prepareFields(loader,state,2*SECOND);
  ends().put("stale",now);starts().put("stale",now);plan.apply();
  assertFalse(ends().containsKey("sword"));assertFalse(starts().containsKey("sword"));assertTrue(ends().isEmpty());assertTrue(starts().isEmpty());
 }
 @Test void shieldLockAges()throws Exception{
  long now=1_000_000L;SaoSnapshot.field(shield,"lockMillis").setLong(null,1000);SaoSnapshot.field(shield,"lockReceivedNanos").setLong(null,now-200_000_000L);
  var state=SaoSnapshot.captureFields(loader,now);
  assertEquals(800,state.getAsJsonObject("timers").getAsJsonObject("shield").get("lockRemainingMillis").getAsLong());
  long[] clock={0};var plan=SaoSnapshot.prepareFields(loader,state,300_000_000L,()->clock[0]);clock[0]=777;plan.apply();
  assertEquals(500,lockMillis());assertEquals(777,lockReceived());
 }
 @Test void schemaOneDocumentRefused()throws Exception{
  var state=SaoSnapshot.captureFields(loader,100);state.addProperty("schema",1);state.remove("timers");
  assertEquals("Snapshot contract mismatch",assertThrows(IllegalArgumentException.class,()->SaoSnapshot.prepareFields(loader,state,0)).getMessage());
 }
 @Test void timersRollBackOnFailure()throws Exception{
  long now=System.nanoTime();ends().put("sword",now+5*SECOND);starts().put("sword",now-SECOND);SaoSnapshot.field(shield,"lockMillis").setLong(null,900);SaoSnapshot.field(shield,"lockReceivedNanos").setLong(null,now);
  var state=SaoSnapshot.captureFields(loader,now);var plan=SaoSnapshot.prepareFields(loader,state,0);
  ends().clear();starts().clear();SaoSnapshot.prepareFields(loader,state,0).apply();assertTrue(ends().containsKey("sword"));assertTrue(lockMillis()>0&&lockMillis()<=900);// the plan really writes the timers
  ends().clear();starts().clear();ends().put("bow",42L);starts().put("bow",7L);SaoSnapshot.field(shield,"lockMillis").setLong(null,11);SaoSnapshot.field(shield,"lockReceivedNanos").setLong(null,22);
  var saved=fr.sao.saoui.skills.SkillStatusClient.allSkills;var failing=new HashMap<Integer,String>(saved){boolean fail=true;public void putAll(Map<? extends Integer,? extends String> map){if(fail){fail=false;throw new IllegalStateException("test write failure");}super.putAll(map);}};
  fr.sao.saoui.skills.SkillStatusClient.allSkills=failing;
  try{assertThrows(IllegalStateException.class,plan::apply);}finally{fr.sao.saoui.skills.SkillStatusClient.allSkills=saved;}
  assertEquals(Map.of("bow",42L),ends());assertEquals(Map.of("bow",7L),starts());assertEquals(11,lockMillis());assertEquals(22,lockReceived());
 }
}
