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
  var out=new JsonObject();out.addProperty("schema",1);out.addProperty("artifact",artifact);out.addProperty("capturedNanos",now);var sections=new JsonObject();out.add("sections",sections);
  for(var e:SaoSchema.CLASSES.entrySet()){Class<?> c=Class.forName(e.getValue(),false,loader);var section=new JsonObject();for(String name:SaoSchema.FIELDS.get(e.getKey())){Object value=field(c,name).get(null);synchronized(value==null?c:value){section.add(name,VALUES.encode(value));}}sections.add(e.getKey(),section);}
  Class<?> store=Class.forName("fr.sao.questtracker.model.QuestHudStore",false,loader);Object raw=field(store,"STATES").get(null);var quests=new JsonArray();
  if(!(raw instanceof Map<?,?> states)||states.size()>256)throw new IllegalArgumentException("Invalid quest state");
  for(Object state:states.values()){
   if(!(boolean)field(state.getClass(),"targetVisible").get(state))continue;
   var item=new JsonObject();item.addProperty("packetType",0);for(String name:SaoSchema.QUEST_FIELDS)item.add(payloadName(name),VALUES.encode(field(state.getClass(),name).get(state)));quests.add(item);
  }out.add("quests",quests);bounded(out);return out;
 }
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
  if(!data.keySet().equals(Set.of("schema","artifact","capturedNanos","sections","quests"))||data.get("schema").getAsInt()!=1||!data.get("artifact").isJsonPrimitive()||!SaoSchema.CONTRACT_FINGERPRINT.equals(localArtifact)||!data.get("artifact").getAsString().equals(localArtifact))throw new IllegalArgumentException("Snapshot contract mismatch");
  var sections=data.getAsJsonObject("sections");if(!sections.keySet().equals(SaoSchema.CLASSES.keySet()))throw new IllegalArgumentException("Incomplete state sections");
  var changes=new ArrayList<Change>();
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
 private record Change(Field field,Object value){}
 public static final class RestorePlan {
  private final List<Change> changes;private boolean used;private final java.util.function.LongSupplier clock;private final long preparedAt;
  private RestorePlan(List<Change> changes,java.util.function.LongSupplier clock,long preparationStarted){this.changes=List.copyOf(changes);this.clock=clock;preparedAt=preparationStarted;}
  public synchronized void apply()throws Exception{
   if(used)throw new IllegalStateException("Snapshot plan already applied");used=true;long now=clock.getAsLong(),waiting=now-preparedAt;if(waiting<0)throw new IllegalStateException("Local monotonic clock moved backwards");var undo=new ArrayList<Change>();
   try{for(Change c:changes){Object current=c.field.get(null);Object backup=current instanceof Map<?,?> m?new LinkedHashMap<>(m):Modifier.isFinal(c.field.getModifiers())&&current instanceof Collection<?> s?new ArrayList<>(s):current;undo.add(new Change(c.field,backup));Object next=c.value;
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
