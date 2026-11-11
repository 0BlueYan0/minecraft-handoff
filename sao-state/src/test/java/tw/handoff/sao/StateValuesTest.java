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
 @Test void arbitraryObjectsAndOversizedOrNonFiniteValuesAreRejected(){var codec=new StateValues(Set.of());assertThrows(IllegalArgumentException.class,()->codec.encode(new Object()));assertThrows(IllegalArgumentException.class,()->codec.encode(Double.NaN));assertThrows(IllegalArgumentException.class,()->codec.encode("x".repeat(131073)));var list=new ArrayList<Object>();list.add(list);assertThrows(IllegalArgumentException.class,()->codec.encode(list));}
 @Test void decoderRejectsMissingFieldsUnknownFieldsAndFractionalIntegers(){var codec=new StateValues(Set.of(Item.class.getName()));assertThrows(IllegalArgumentException.class,()->codec.decode(com.google.gson.JsonParser.parseString("{\"name\":\"x\"}"),Item.class));assertThrows(IllegalArgumentException.class,()->codec.decode(com.google.gson.JsonParser.parseString("{\"name\":\"x\",\"count\":1.5,\"lore\":[]}"),Item.class));assertThrows(IllegalArgumentException.class,()->codec.decode(com.google.gson.JsonParser.parseString("{\"name\":\"x\",\"count\":1,\"lore\":[],\"token\":\"never\"}"),Item.class));}
 @Test void rawRecordFieldsPreserveValuesEvenWithCustomAccessors(){record Translated(String value){public String value(){return "translated";}}var codec=new StateValues(Set.of(Translated.class.getName()));assertEquals("raw",codec.encode(new Translated("raw")).getAsJsonObject().get("value").getAsString());}
}
