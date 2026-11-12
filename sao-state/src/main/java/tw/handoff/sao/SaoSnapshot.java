package tw.handoff.sao;
import com.google.gson.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Call on the native client game thread. Capture never invokes network/sound/UI handlers. */
public final class SaoSnapshot {
 private static final StateValues VALUES=new StateValues(SaoSchema.VALUES,Set.of("fr.sao.equipment.EquipmentClient$Slot#item"));
 private static final Set<String> NULLABLE_FIELDS=Set.of("mux.sendable","equipment.state","equipment.presets");
 public static JsonObject capture(ClassLoader loader,long now)throws Exception{return captureFields(loader,now,verifyArtifact(loader));}
 /** Returns the contract fingerprint of the loaded SAO JAR; throws unless it equals SaoSchema.CONTRACT_FINGERPRINT. */
 public static String verifyArtifact(ClassLoader loader)throws Exception{
  Class<?> type=Class.forName("fr.sao.net.Mux",false,loader);var url=type.getProtectionDomain().getCodeSource().getLocation();
  if(!url.getProtocol().equals("file"))throw new IllegalArgumentException("Unsupported SAO artifact location");Path jar=Path.of(url.toURI()).toRealPath();
  if(!Files.isRegularFile(jar))throw new IllegalArgumentException("Unsupported SAO artifact");
  String actual;
  try(var zip=new java.util.zip.ZipFile(jar.toFile())){
   actual=SaoSchema.fingerprint(name->{
    try{
     Class<?> c=Class.forName(name,false,loader);var source=c.getProtectionDomain().getCodeSource();
     if(source==null||!source.getLocation().getProtocol().equals("file")||!Path.of(source.getLocation().toURI()).toRealPath().equals(jar))return null;
     var entry=zip.getEntry(name.replace('.','/')+".class");if(entry==null)return null;try(var in=zip.getInputStream(entry)){return in.readAllBytes();}
    }catch(ClassNotFoundException|LinkageError|java.io.IOException|java.net.URISyntaxException missing){return null;}
   });
  }
  if(!actual.equals(SaoSchema.CONTRACT_FINGERPRINT))throw new IllegalArgumentException("SAO version requires a new state contract");
  return actual;
 }
 static JsonObject captureFields(ClassLoader loader,long now)throws Exception{return captureFields(loader,now,SaoSchema.CONTRACT_FINGERPRINT);}
 static JsonObject captureFields(ClassLoader loader,long now,String artifact)throws Exception{
  var out=new JsonObject();out.addProperty("schema",2);out.addProperty("artifact",artifact);out.addProperty("capturedNanos",now);var sections=new JsonObject();out.add("sections",sections);
  for(var e:SaoSchema.CLASSES.entrySet()){Class<?> c=Class.forName(e.getValue(),false,loader);var section=new JsonObject();for(String name:SaoSchema.FIELDS.get(e.getKey())){Object value=field(c,name).get(null);synchronized(value==null?c:value){if(e.getKey().equals("skills")&&value instanceof Map<?,?> times)for(Object t:times.values())if(t instanceof Double d&&!Double.isFinite(d))throw new IllegalArgumentException("skills."+name+": non-finite timer");section.add(name,encodeAt(e.getKey()+"."+name,value));}}sections.add(e.getKey(),section);}
  Class<?> store=Class.forName("fr.sao.questtracker.model.QuestHudStore",false,loader);Object raw=field(store,"STATES").get(null);var quests=new JsonArray();
  if(!(raw instanceof Map<?,?> states)||states.size()>256)throw new IllegalArgumentException("Invalid quest state");
  for(Object state:states.values()){
   if(!(boolean)field(state.getClass(),"targetVisible").get(state))continue;
   var item=new JsonObject();item.addProperty("packetType",0);for(String name:SaoSchema.QUEST_FIELDS)item.add(payloadName(name),encodeAt("quests."+name,field(state.getClass(),name).get(state)));quests.add(item);
  }out.add("quests",quests);out.add("timers",captureTimers(loader,now));bounded(out);return out;
 }
 /** Weapon cooldowns and the shield lock as durations relative to {@code now}; absolute nanoTime values never leave this machine. */
 private static JsonObject captureTimers(ClassLoader loader,long now)throws Exception{
  try{return captureTimersChecked(loader,now);}catch(ArithmeticException overflow){throw new IllegalArgumentException("timers: timer value out of range",overflow);}
 }
 private static JsonObject captureTimersChecked(ClassLoader loader,long now)throws Exception{
  Class<?> weaponClass=Class.forName(SaoSchema.WEAPON_CLASS,false,loader),shieldClass=Class.forName(SaoSchema.SHIELD_CLASS,false,loader);
  var ends=copyTimes(field(weaponClass,"cooldownEndNanos").get(null),"timers.weapon.end");var starts=copyTimes(field(weaponClass,"cooldownStartNanos").get(null),"timers.weapon.start");
  var weapon=new JsonObject();
  for(var e:ends.entrySet()){long remaining=Math.subtractExact(e.getValue(),now);if(remaining<=0)continue;var item=new JsonObject();Long start=starts.get(e.getKey());if(start!=null)item.addProperty("startAgoNanos",Math.max(0,Math.subtractExact(now,start)));item.addProperty("remainingNanos",remaining);weapon.add(e.getKey(),item);}
  long lockMillis,received;synchronized(shieldClass){lockMillis=field(shieldClass,"lockMillis").getLong(null);received=field(shieldClass,"lockReceivedNanos").getLong(null);}
  var shield=new JsonObject();shield.addProperty("lockRemainingMillis",Math.max(0,lockMillis-Math.max(0,Math.subtractExact(now,received))/1_000_000));
  var timers=new JsonObject();timers.add("weapon",weapon);timers.add("shield",shield);return timers;
 }
 private static Map<String,Long> copyTimes(Object raw,String path){
  if(!(raw instanceof Map<?,?> map))throw new IllegalArgumentException(path+": not a map");var copy=new LinkedHashMap<String,Long>();
  synchronized(map){if(map.size()>1024)throw new IllegalArgumentException(path+": too many entries");for(var e:map.entrySet()){if(!(e.getKey() instanceof String key)||key.length()>1024||!(e.getValue() instanceof Long time))throw new IllegalArgumentException(path+": unexpected entry type");copy.put(key,time);}}
  return copy;
 }
 private static long nonNegative(JsonObject object,String name){
  if(!object.has(name)||!object.get(name).isJsonPrimitive()||!object.get(name).getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid timer state");
  long value;try{value=object.get(name).getAsBigDecimal().longValueExact();}catch(ArithmeticException bad){throw new IllegalArgumentException("Invalid timer state");}
  if(value<0)throw new IllegalArgumentException("Invalid timer state");return value;
 }
 /** Weapon keys: [remainingNanos, startAgoNanos or -1]. Already aged by the snapshot age; apply() subtracts the local wait. */
 private record WeaponTimes(Map<String,long[]> entries){
  Map<String,Long> end(long now,long waiting){try{var out=new LinkedHashMap<String,Long>();for(var e:entries.entrySet()){long left=e.getValue()[0]-waiting;if(left>0)out.put(e.getKey(),Math.addExact(now,left));}return out;}catch(ArithmeticException overflow){throw new IllegalArgumentException("Invalid timer state",overflow);}}
  Map<String,Long> start(long now,long waiting){try{var out=new LinkedHashMap<String,Long>();for(var e:entries.entrySet()){long left=e.getValue()[0]-waiting,ago=e.getValue()[1];if(left>0&&ago>=0)out.put(e.getKey(),Math.subtractExact(Math.subtractExact(now,ago),waiting));}return out;}catch(ArithmeticException overflow){throw new IllegalArgumentException("Invalid timer state",overflow);}}
 }
 /** Shield lock remaining, in nanoseconds, already aged by the snapshot age. Rounded up to whole milliseconds because the game stores milliseconds: the result is at most 1 ms above the exact remaining time and never above the snapshot value. */
 private record ShieldLock(long nanos){long millis(long waiting){return (Math.max(0,nanos-waiting)+999_999)/1_000_000;}}
 private static JsonElement encodeAt(String path,Object value){try{return VALUES.encode(value);}catch(IllegalArgumentException failure){throw new IllegalArgumentException(path+": "+failure.getMessage(),failure);}}
 private static String payloadName(String field){return Set.of("questTextJson","objectiveTextJson","distanceTextJson","topTextJson","bottomTextJson").contains(field)?field.substring(0,field.length()-4):field;}
 static Field field(Class<?> type,String name)throws NoSuchFieldException{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 private static void bounded(JsonObject value){if(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>2*1024*1024)throw new IllegalArgumentException("Snapshot exceeds budget");}

 /** Validates and constructs every replacement before returning a plan; no field writes here. */
 public static RestorePlan prepare(ClassLoader loader,JsonObject document,long elapsedNanos)throws Exception{
  long start=System.nanoTime();String local=verifyArtifact(loader);return prepareFields(loader,document,Math.addExact(elapsedNanos,System.nanoTime()-start),System::nanoTime,local);
 }
 static RestorePlan prepareFields(ClassLoader loader,JsonObject document,long elapsedNanos)throws Exception{
  return prepareFields(loader,document,elapsedNanos,System::nanoTime);
 }
 static RestorePlan prepareFields(ClassLoader loader,JsonObject document,long elapsedNanos,java.util.function.LongSupplier clock)throws Exception{
  return prepareFields(loader,document,elapsedNanos,clock,SaoSchema.CONTRACT_FINGERPRINT);
 }
 /** The snapshot and this client must both carry the pinned contract fingerprint; any other value is refused. */
 static RestorePlan prepareFields(ClassLoader loader,JsonObject document,long elapsedNanos,java.util.function.LongSupplier clock,String localArtifact)throws Exception{
  long preparationStarted=clock.getAsLong();
  if(elapsedNanos<0)throw new IllegalArgumentException("Snapshot age unavailable");bounded(document);JsonObject data=document.deepCopy();
  if(!data.keySet().equals(Set.of("schema","artifact","capturedNanos","sections","quests","timers"))||data.get("schema").getAsInt()!=2||!data.get("artifact").isJsonPrimitive()||!SaoSchema.CONTRACT_FINGERPRINT.equals(localArtifact)||!data.get("artifact").getAsString().equals(localArtifact))throw new IllegalArgumentException("Snapshot contract mismatch");
  var sections=data.getAsJsonObject("sections");if(!sections.keySet().equals(SaoSchema.CLASSES.keySet()))throw new IllegalArgumentException("Incomplete state sections");
  var changes=new ArrayList<Change>();
  addTimerChanges(loader,data.get("timers"),elapsedNanos,changes);
  for(var e:SaoSchema.CLASSES.entrySet()){
   Class<?> c=Class.forName(e.getValue(),false,loader);var section=sections.getAsJsonObject(e.getKey());if(!section.keySet().equals(new HashSet<>(SaoSchema.FIELDS.get(e.getKey()))))throw new IllegalArgumentException("State field contract changed");
   for(String name:SaoSchema.FIELDS.get(e.getKey())){
    var f=field(c,name);JsonElement json=section.get(name);Object value=json.isJsonNull()&&NULLABLE_FIELDS.contains(e.getKey()+"."+name)?null:VALUES.decode(json,f.getGenericType());
    if(e.getKey().equals("skills")&&name.equals("cooldowns")){if(!(value instanceof Map<?,?> map))throw new IllegalArgumentException("Missing cooldowns");@SuppressWarnings("unchecked")var times=(Map<String,Double>)map;times.replaceAll((skill,remaining)->CooldownAge.remaining(remaining,elapsedNanos));}
    changes.add(new Change(f,value));
   }
  }
  var quests=data.getAsJsonArray("quests");if(quests.size()>256)throw new IllegalArgumentException("Too many quest HUDs");var questMap=new LinkedHashMap<String,Object>();
  if(!quests.isEmpty()){
   Class<?> payload=Class.forName("fr.sao.questtracker.messaging.QuestHudPayload",false,loader),model=Class.forName("fr.sao.questtracker.model.QuestHudState",false,loader);
   var components=payload.getRecordComponents();if(components.length!=21)throw new IllegalArgumentException("Quest payload contract changed");Class<?>[] types=Arrays.stream(components).skip(1).map(RecordComponent::getType).toArray(Class[]::new);var ctor=model.getConstructor(types);
   for(JsonElement json:quests){Object decoded=VALUES.decode(json,payload);if(((Number)field(payload,"packetType").get(decoded)).byteValue()!=0)throw new IllegalArgumentException("Snapshot must contain current HUDs only");Object[] values=new Object[20];for(int i=1;i<21;i++)values[i-1]=field(payload,components[i].getName()).get(decoded);Object state=ctor.newInstance(values);String id=(String)field(model,"hudId").get(state);if(questMap.put(id,state)!=null)throw new IllegalArgumentException("Duplicate HUD identity");}
  }
  changes.add(new Change(field(Class.forName("fr.sao.questtracker.model.QuestHudStore",false,loader),"STATES"),questMap));
  Class<?> skills=Class.forName(SaoSchema.CLASSES.get("skills"),false,loader);changes.add(new Change(field(skills,"lastTickNanos"),System.nanoTime()));
  for(String section:List.of("equipment","party")){var f=field(Class.forName(SaoSchema.CLASSES.get(section),false,loader),"version");changes.add(new Change(f,f.getInt(null)+1));}
  return new RestorePlan(changes,clock,preparationStarted);
 }
 /** Timer changes go first so a later failing Change exercises their rollback. Ages by the snapshot age only; apply() adds the local wait. */
 private static void addTimerChanges(ClassLoader loader,JsonElement json,long elapsedNanos,List<Change> changes)throws Exception{
  try{addTimerChangesChecked(loader,json,elapsedNanos,changes);}catch(ArithmeticException overflow){throw new IllegalArgumentException("Invalid timer state",overflow);}
 }
 private static void addTimerChangesChecked(ClassLoader loader,JsonElement json,long elapsedNanos,List<Change> changes)throws Exception{
  if(json==null||!json.isJsonObject()||!json.getAsJsonObject().keySet().equals(Set.of("weapon","shield")))throw new IllegalArgumentException("Invalid timer state");
  var timers=json.getAsJsonObject();if(!timers.get("weapon").isJsonObject()||!timers.get("shield").isJsonObject())throw new IllegalArgumentException("Invalid timer state");
  // Weapon entries are capped at 1024 (StateValues allows 4096 for maps): a player holds a handful of weapons, so the tighter cap bounds what a network snapshot can write into the game's maps.
  var weapon=timers.getAsJsonObject("weapon");if(weapon.size()>1024)throw new IllegalArgumentException("Invalid timer state");var entries=new LinkedHashMap<String,long[]>();
  for(var e:weapon.entrySet()){
   if(e.getKey().length()>1024||!e.getValue().isJsonObject())throw new IllegalArgumentException("Invalid timer state");var item=e.getValue().getAsJsonObject();
   if(!Set.of("remainingNanos","startAgoNanos").containsAll(item.keySet())||!item.has("remainingNanos"))throw new IllegalArgumentException("Invalid timer state");
   long remaining=Math.max(0,nonNegative(item,"remainingNanos")-elapsedNanos);if(remaining==0)continue;
   entries.put(e.getKey(),new long[]{remaining,item.has("startAgoNanos")?Math.addExact(nonNegative(item,"startAgoNanos"),elapsedNanos):-1});
  }
  var shield=timers.getAsJsonObject("shield");if(!shield.keySet().equals(Set.of("lockRemainingMillis")))throw new IllegalArgumentException("Invalid timer state");
  long lock=Math.max(0,Math.multiplyExact(nonNegative(shield,"lockRemainingMillis"),1_000_000L)-elapsedNanos);
  Class<?> weaponClass=Class.forName(SaoSchema.WEAPON_CLASS,false,loader),shieldClass=Class.forName(SaoSchema.SHIELD_CLASS,false,loader);var weaponTimes=new WeaponTimes(entries);
  changes.add(new Change(field(weaponClass,"cooldownEndNanos"),weaponTimes));changes.add(new Change(field(weaponClass,"cooldownStartNanos"),weaponTimes));
  changes.add(new Change(field(shieldClass,"lockMillis"),new ShieldLock(lock)));changes.add(new Change(field(shieldClass,"lockReceivedNanos"),new ShieldLock(lock)));
 }
 private record Change(Field field,Object value){}
 public static final class RestorePlan {
  private final List<Change> changes;private boolean used;private final java.util.function.LongSupplier clock;private final long preparedAt;
  private RestorePlan(List<Change> changes,java.util.function.LongSupplier clock,long preparationStarted){this.changes=List.copyOf(changes);this.clock=clock;preparedAt=preparationStarted;}
  public synchronized void apply()throws Exception{
   if(used)throw new IllegalStateException("Snapshot plan already applied");used=true;long now=clock.getAsLong(),waiting=now-preparedAt;if(waiting<0)throw new IllegalStateException("Local monotonic clock moved backwards");var undo=new ArrayList<Change>();
   try{for(Change c:changes){Object current=c.field.get(null);Object backup=current instanceof Map<?,?> m?new LinkedHashMap<>(m):Modifier.isFinal(c.field.getModifiers())&&current instanceof Collection<?> s?new ArrayList<>(s):current;undo.add(new Change(c.field,backup));Object next=c.value;
    if(next instanceof WeaponTimes times)next=c.field.getName().equals("cooldownEndNanos")?times.end(now,waiting):times.start(now,waiting);
    else if(next instanceof ShieldLock lock)next=c.field.getName().equals("lockMillis")?(Object)lock.millis(waiting):(Object)now;
    if(c.field.getDeclaringClass().getName().equals(SaoSchema.CLASSES.get("skills"))){if(c.field.getName().equals("lastTickNanos"))next=now;else if(c.field.getName().equals("cooldowns")){@SuppressWarnings("unchecked")var aged=new LinkedHashMap<>((Map<String,Double>)next);aged.replaceAll((skill,time)->CooldownAge.remaining(time,waiting));next=aged;}}
    write(c.field,next);}}
   catch(Exception failure){Collections.reverse(undo);for(Change old:undo)try{write(old.field,old.value);}catch(Exception rollback){failure.addSuppressed(rollback);}throw failure;}
  }
  @SuppressWarnings({"rawtypes","unchecked"}) private static void write(Field f,Object value)throws IllegalAccessException{
   Object current=f.get(null);if(current instanceof Map map&&value instanceof Map incoming){synchronized(map){map.clear();map.putAll(incoming);}}
   else if(Modifier.isFinal(f.getModifiers())&&current instanceof Collection set&&value instanceof Collection incoming){synchronized(set){set.clear();set.addAll(incoming);}}
   else f.set(null,value);
  }
 }
 public static String summary(JsonObject data){var s=data.getAsJsonObject("sections");return "muxChannels="+s.getAsJsonObject("mux").getAsJsonArray("SERVED").size()+",skills="+s.getAsJsonObject("skills").getAsJsonObject("allSkills").size()+",cooldowns="+s.getAsJsonObject("skills").getAsJsonObject("cooldowns").size()+",questHuds="+data.getAsJsonArray("quests").size()+",equipmentReady="+!s.getAsJsonObject("equipment").get("state").isJsonNull();}
 private SaoSnapshot(){}
}
