package tw.handoff.relay.core;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 每個 key 只留最新一筆的 clientbound custom payload frame，viewer 中途加入時重播。 */
public final class SaoReplayTable {
 public record Replay(List<byte[]> frames,Set<String> missing){}
 private static final int BLESSING_LIMIT=256;
 private static final Set<String> GUI_SINGLE=Set.of("SHIELD_UPDATE","SKILL_VARIANT","SETTINGS","UI_STYLE","PROJECTION_LIST");
 private record Entry(long seq,byte[] frame){}
 private final int customPayloadPacketId,maxBytes;
 private final Map<String,Entry> keyed=new HashMap<>();
 private final List<Entry> blessing=new ArrayList<>();
 private long seq;private boolean blessingOverflow,budgetExceeded,skipped;

 public SaoReplayTable(int customPayloadPacketId,int maxBytes){if(maxBytes<1)throw new IllegalArgumentException();this.customPayloadPacketId=customPayloadPacketId;this.maxBytes=maxBytes;}

 public synchronized void observe(byte[] frame){
  if(budgetExceeded)return;
  OpaqueFrames.View view;
  // A frame without a readable custom payload header cannot be attributed to any table channel.
  try{view=OpaqueFrames.inspect(frame);}catch(RuntimeException unattributable){return;}
  try{apply(view,frame);}catch(RuntimeException unparsable){skipped=true;return;}
  if(totalBytes()>maxBytes){keyed.clear();blessing.clear();blessingOverflow=false;budgetExceeded=true;}
 }

 public synchronized Replay replay(){
  var all=Collections.newSetFromMap(new IdentityHashMap<Entry,Boolean>());
  all.addAll(keyed.values());all.addAll(blessing);
  var frames=all.stream().sorted(Comparator.comparingLong(Entry::seq)).map(e->e.frame().clone()).toList();
  var missing=new TreeSet<String>();
  if(blessingOverflow)missing.add("saoblessing");
  if(budgetExceeded)missing.add("replay-budget");
  if(skipped)missing.add("replay-skipped");
  return new Replay(frames,Collections.unmodifiableSet(missing));
 }

 /** The host saw a frame on a table channel that it could not record; it is never recoverable on this connection. */
 public synchronized void markSkipped(){skipped=true;}

 private int totalBytes(){
  var all=Collections.newSetFromMap(new IdentityHashMap<Entry,Boolean>());
  all.addAll(keyed.values());all.addAll(blessing);
  long sum=0;for(var e:all)sum+=e.frame().length;
  return sum>Integer.MAX_VALUE?Integer.MAX_VALUE:(int)sum;
 }

 private void apply(OpaqueFrames.View view,byte[] frame){
  if(view.packetId()!=customPayloadPacketId)return;
  byte[] data=Arrays.copyOfRange(frame,view.dataOffset(),frame.length);
  switch(view.channel()){
   case "saocore:mux"->applyMux(frame,data);
   case "capuodev:quest_hud"->applyQuest(frame,data);
   case "saoblessing:main"->applyBlessing(frame,data);
   default->{}
  }
 }

 private void applyMux(byte[] frame,byte[] data){
  if(data.length<1)throw new IllegalArgumentException("Empty mux payload");
  int n=data[0]&255;
  if(n==0)return; // An empty sub-channel name is valid on the wire but is not one the table handles.
  if(n>data.length-1)throw new IllegalArgumentException("Bad mux sub-channel length");
  String sub=new String(data,1,n,StandardCharsets.UTF_8);
  byte[] content=Arrays.copyOfRange(data,1+n,data.length);
  switch(sub){
   case ":hello"->put(frame,"mux:hello");
   case "capuodev:quest_hud"->applyQuest(frame,content);
   case "saogui:main"->applyGui(frame,content);
   default->{}
  }
 }

 private void applyGui(byte[] frame,byte[] content){
  String op=op(content);
  switch(op){
   case "INIT"->put(frame,"skillbar","mana","level");
   case "USE_SKILL_OK"->put(frame,"skillbar");
   case "MANA_UPDATE"->put(frame,"mana");
   case "LEVEL_UP"->put(frame,"level");
   default->{if(GUI_SINGLE.contains(op))put(frame,"saogui:"+op);}
  }
 }

 private void applyQuest(byte[] frame,byte[] content){
  if(content.length<2)throw new IllegalArgumentException("Short quest_hud payload");
  int type=content[0]&255;
  int[] p={1};
  int len=OpaqueFrames.readVarInt(content,p);
  if(len<0||len>content.length-p[0])throw new IllegalArgumentException("Bad quest_hud id length");
  String id=new String(content,p[0],len,StandardCharsets.UTF_8);
  if(type==0)put(frame,"quest:"+id);
  else if(type==1){if(id.equals("*"))keyed.keySet().removeIf(k->k.startsWith("quest:"));else keyed.remove("quest:"+id);}
 }

 private void applyBlessing(byte[] frame,byte[] data){
  String op=op(data);
  if(op.equals("SERVER_HELLO"))return;
  if(op.equals("SESSION_START")||op.equals("SESSION_END")){blessing.clear();blessingOverflow=false;blessing.add(new Entry(++seq,frame.clone()));return;}
  if(blessingOverflow)return;
  blessing.add(new Entry(++seq,frame.clone()));
  if(blessing.size()>BLESSING_LIMIT){blessing.clear();blessingOverflow=true;}
 }

 private void put(byte[] frame,String... keys){
  var e=new Entry(++seq,frame.clone());
  for(String k:keys)keyed.put(k,e);
 }

 private static String op(byte[] json){
  JsonElement root=JsonParser.parseString(new String(json,StandardCharsets.UTF_8));
  if(!root.isJsonObject())throw new IllegalArgumentException("Payload is not a JSON object");
  JsonObject o=root.getAsJsonObject();
  JsonElement op=o.get("op");
  if(op==null||!op.isJsonPrimitive()||!op.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Missing op");
  return op.getAsString();
 }
}
