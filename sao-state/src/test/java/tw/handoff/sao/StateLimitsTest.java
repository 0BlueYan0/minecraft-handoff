package tw.handoff.sao;
import com.google.gson.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Review round 1 found the size limits enforced but untested; each limit is checked at the boundary on encode and decode. */
class StateLimitsTest {
 static List<String> listShape;static Map<String,Integer> mapShape;static List<List<String>> nestedShape;
 private static Type type(String name)throws Exception{return StateLimitsTest.class.getDeclaredField(name).getGenericType();}
 private final StateValues codec=new StateValues(Set.of());
 private static List<String> strings(int n){return IntStream.range(0,n).mapToObj(Integer::toString).collect(Collectors.toList());}
 private static Map<String,Integer> ints(int n){var m=new LinkedHashMap<String,Integer>();for(int i=0;i<n;i++)m.put("k"+i,i);return m;}

 @Test void collectionLimitIs4096OnEncodeAndDecode()throws Exception{
  assertEquals(4096,codec.encode(strings(4096)).getAsJsonArray().size());
  assertThrows(IllegalArgumentException.class,()->codec.encode(strings(4097)));
  var tooMany=new JsonArray();strings(4097).forEach(tooMany::add);
  assertThrows(IllegalArgumentException.class,()->codec.decode(tooMany,type("listShape")));
  var ok=new JsonArray();strings(4096).forEach(ok::add);
  assertEquals(4096,((List<?>)codec.decode(ok,type("listShape"))).size());
 }
 @Test void mapLimitIs4096OnEncodeAndDecode()throws Exception{
  assertEquals(4096,codec.encode(ints(4096)).getAsJsonObject().size());
  assertThrows(IllegalArgumentException.class,()->codec.encode(ints(4097)));
  var tooMany=new JsonObject();ints(4097).forEach(tooMany::addProperty);
  assertThrows(IllegalArgumentException.class,()->codec.decode(tooMany,type("mapShape")));
 }
 @Test void mapKeyLimitIs1024OnEncodeAndDecode()throws Exception{
  assertDoesNotThrow(()->codec.encode(Map.of("k".repeat(1024),1)));
  assertThrows(IllegalArgumentException.class,()->codec.encode(Map.of("k".repeat(1025),1)));
  var json=new JsonObject();json.addProperty("k".repeat(1025),1);
  assertThrows(IllegalArgumentException.class,()->codec.decode(json,type("mapShape")));
 }
 @Test void nodeBudgetIs20000AtShallowDepth()throws Exception{
  // 1 root + 19 lists + 19*1001 strings = 19039 nodes; 20 lists push it to 20041.
  var under=new ArrayList<List<String>>();for(int i=0;i<19;i++)under.add(strings(1001));
  var over=new ArrayList<List<String>>(under);over.add(strings(1001));
  assertDoesNotThrow(()->codec.encode(under));
  assertThrows(IllegalArgumentException.class,()->codec.encode(over));
  var json=new JsonArray();for(var list:over){var inner=new JsonArray();list.forEach(inner::add);json.add(inner);}
  assertThrows(IllegalArgumentException.class,()->codec.decode(json,type("nestedShape")));
 }
 @Test void oversizedDocumentIsRejectedBeforeAnyFieldIsTouched()throws Exception{
  var loader=getClass().getClassLoader();var doc=SaoSnapshot.captureFields(loader,100);
  doc.addProperty("padding","x".repeat(2*1024*1024+1));
  fr.sao.saoui.skills.SkillStatusClient.level=42;
  var failure=assertThrows(IllegalArgumentException.class,()->SaoSnapshot.prepareFields(loader,doc,0));
  assertEquals("Snapshot exceeds budget",failure.getMessage());
  assertEquals(42,fr.sao.saoui.skills.SkillStatusClient.level);
 }
 @Test void captureRejectsStateLargerThan2MiB()throws Exception{
  var loader=getClass().getClassLoader();var saved=fr.sao.saoui.skills.SkillStatusClient.allSkills;
  var big=new HashMap<Integer,String>();for(int i=0;i<17;i++)big.put(i,"s".repeat(131072));
  fr.sao.saoui.skills.SkillStatusClient.allSkills=big;
  try{var failure=assertThrows(IllegalArgumentException.class,()->SaoSnapshot.captureFields(loader,100));assertEquals("Snapshot exceeds budget",failure.getMessage());}
  finally{fr.sao.saoui.skills.SkillStatusClient.allSkills=saved;}
 }
}
