package tw.handoff.sao;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StateValuesTest {
 record Item(String name,int count,List<String> lore){}
 record Slot(String name,Item item){}
 @Test void nullableMemberIsExplicitAndDoesNotPermitOtherNulls(){var codec=new StateValues(Set.of(Item.class.getName(),Slot.class.getName()),Set.of(Slot.class.getName()+"#item"));assertEquals(new Slot("empty",null),codec.decode(codec.encode(new Slot("empty",null)),Slot.class));assertThrows(IllegalArgumentException.class,()->codec.decode(codec.encode(new Slot(null,null)),Slot.class));}
 @Test void whitelistedRecordRoundTripsWithoutCallingTranslatedAccessor()throws Exception{
  var codec=new StateValues(Set.of(Item.class.getName()));var value=new Item("blade",3,List.of("lore"));
  assertEquals(value,codec.decode(codec.encode(value),Item.class));
 }
 record Hud(float targetAngleDegrees,double scale){}
 /** 2026-11-11 live SAO capture: QuestHudState.targetAngleDegrees is Float.NaN when a HUD has no target. */
 @Test void nonFiniteFloatsRoundTripOnlyIntoFloatingFields(){
  var codec=new StateValues(Set.of(Hud.class.getName()));
  var json=codec.encode(new Hud(Float.NaN,Double.NEGATIVE_INFINITY));assertEquals("NaN",json.getAsJsonObject().get("targetAngleDegrees").getAsString());
  var back=(Hud)codec.decode(json,Hud.class);assertTrue(Float.isNaN(back.targetAngleDegrees()));assertEquals(Double.NEGATIVE_INFINITY,back.scale());
  assertEquals(Float.POSITIVE_INFINITY,codec.decode(new com.google.gson.JsonPrimitive("Infinity"),float.class));
  assertThrows(IllegalArgumentException.class,()->codec.decode(new com.google.gson.JsonPrimitive("NaN"),int.class));
  assertThrows(IllegalArgumentException.class,()->codec.decode(new com.google.gson.JsonPrimitive("nan"),float.class));
  assertThrows(IllegalArgumentException.class,()->codec.decode(new com.google.gson.JsonPrimitive("1.5"),double.class));
 }
 @Test void arbitraryObjectsAndOversizedValuesAreRejected(){var codec=new StateValues(Set.of());assertThrows(IllegalArgumentException.class,()->codec.encode(new Object()));assertThrows(IllegalArgumentException.class,()->codec.encode(new java.math.BigDecimal("1")));assertThrows(IllegalArgumentException.class,()->codec.encode("x".repeat(131073)));var list=new ArrayList<Object>();list.add(list);assertThrows(IllegalArgumentException.class,()->codec.encode(list));}
 @Test void decoderRejectsMissingFieldsUnknownFieldsAndFractionalIntegers(){var codec=new StateValues(Set.of(Item.class.getName()));assertThrows(IllegalArgumentException.class,()->codec.decode(com.google.gson.JsonParser.parseString("{\"name\":\"x\"}"),Item.class));assertThrows(IllegalArgumentException.class,()->codec.decode(com.google.gson.JsonParser.parseString("{\"name\":\"x\",\"count\":1.5,\"lore\":[]}"),Item.class));assertThrows(IllegalArgumentException.class,()->codec.decode(com.google.gson.JsonParser.parseString("{\"name\":\"x\",\"count\":1,\"lore\":[],\"token\":\"never\"}"),Item.class));}
 @Test void rawRecordFieldsPreserveValuesEvenWithCustomAccessors(){record Translated(String value){public String value(){return "translated";}}var codec=new StateValues(Set.of(Translated.class.getName()));assertEquals("raw",codec.encode(new Translated("raw")).getAsJsonObject().get("value").getAsString());}
}
