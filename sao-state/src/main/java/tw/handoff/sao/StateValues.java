package tw.handoff.sao;
import com.google.gson.*;
import java.lang.reflect.*;
import java.util.*;
/** Explicit data records only: no arbitrary object graph traversal, adapters or class-name input. */
public final class StateValues {
 private final Set<String> allowed,nullableMembers;
 public StateValues(Set<String> allowed){this(allowed,Set.of());}
 public StateValues(Set<String> allowed,Set<String> nullableMembers){this.allowed=Set.copyOf(allowed);this.nullableMembers=Set.copyOf(nullableMembers);}
 private static final class Budget {int nodes;void visit(int depth){if(depth>16||++nodes>20000)throw new IllegalArgumentException("State nesting budget exceeded");}}
 public JsonElement encode(Object value){return encode(value,0,new Budget());}
 private JsonElement encode(Object v,int depth,Budget budget){budget.visit(depth);
  if(v==null)return JsonNull.INSTANCE;
  if(v instanceof String s){if(s.length()>131072)throw new IllegalArgumentException("State string too large");return new JsonPrimitive(s);}
  if(v instanceof Boolean b)return new JsonPrimitive(b);
  if(v instanceof Number n){if(!(n instanceof Byte||n instanceof Short||n instanceof Integer||n instanceof Long||n instanceof Float||n instanceof Double))throw new IllegalArgumentException("Invalid number");
   // SAO's QuestHudPayload uses Float.NaN for "no target angle"; JSON has no NaN, so non-finite floats travel as these names.
   if(!Double.isFinite(n.doubleValue()))return new JsonPrimitive(Double.isNaN(n.doubleValue())?"NaN":n.doubleValue()>0?"Infinity":"-Infinity");
   return new JsonPrimitive(n);}
  if(v instanceof Enum<?> e){require(e.getDeclaringClass());return new JsonPrimitive(e.name());}
  if(v instanceof Collection<?> c){if(c.size()>4096)throw new IllegalArgumentException("State collection too large");var out=new JsonArray();for(Object item:c)out.add(encode(item,depth+1,budget));return out;}
  if(v instanceof Map<?,?> m){if(m.size()>4096)throw new IllegalArgumentException("State map too large");var out=new JsonObject();for(var e:m.entrySet()){if(!(e.getKey() instanceof String||e.getKey() instanceof Integer))throw new IllegalArgumentException("Invalid state key");String key=e.getKey().toString();if(key.length()>1024)throw new IllegalArgumentException("State key too large");out.add(key,encode(e.getValue(),depth+1,budget));}return out;}
  Class<?> type=v.getClass();require(type);if(!type.isRecord())throw new IllegalArgumentException("Only declared records can be serialized");
  var out=new JsonObject();try{for(var component:type.getRecordComponents()){var field=type.getDeclaredField(component.getName());field.setAccessible(true);out.add(component.getName(),encode(field.get(v),depth+1,budget));}}catch(ReflectiveOperationException e){throw new IllegalArgumentException("Record state unavailable",e);}return out;
 }
 public Object decode(JsonElement json,Type type){return decode(json,type,0,new Budget());}
 private Object decode(JsonElement json,Type type,int depth,Budget budget){budget.visit(depth);
  if(json==null||json.isJsonNull())throw new IllegalArgumentException("Null is not declared for this state value");
  if(type instanceof ParameterizedType p){Type raw=p.getRawType();Type[] args=p.getActualTypeArguments();
   if(raw==Map.class){if(!json.isJsonObject()||json.getAsJsonObject().size()>4096)throw new IllegalArgumentException("Invalid map");var map=new LinkedHashMap<Object,Object>();for(var e:json.getAsJsonObject().entrySet()){Object key;if(args[0]==String.class)key=e.getKey();else if(args[0]==Integer.class)try{key=Integer.valueOf(e.getKey());}catch(NumberFormatException bad){throw new IllegalArgumentException(bad);}else throw new IllegalArgumentException("Invalid key type");if(e.getKey().length()>1024)throw new IllegalArgumentException();map.put(key,decode(e.getValue(),args[1],depth+1,budget));}return map;}
   if(raw==List.class||raw==Collection.class||raw==Set.class){if(!json.isJsonArray()||json.getAsJsonArray().size()>4096)throw new IllegalArgumentException("Invalid collection");Collection<Object> out=raw==Set.class?new LinkedHashSet<>():new ArrayList<>();for(JsonElement item:json.getAsJsonArray())out.add(decode(item,args[0],depth+1,budget));return out;}
   throw new IllegalArgumentException("Unsupported state type");
  }
  if(!(type instanceof Class<?> c))throw new IllegalArgumentException("Unsupported state type");
  if(c==String.class){if(!json.isJsonPrimitive()||!json.getAsJsonPrimitive().isString()||json.getAsString().length()>131072)throw new IllegalArgumentException("Invalid string");return json.getAsString();}
  if(c==boolean.class||c==Boolean.class){if(!json.isJsonPrimitive()||!json.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException();return json.getAsBoolean();}
  if((c==float.class||c==Float.class||c==double.class||c==Double.class)&&json.isJsonPrimitive()&&json.getAsJsonPrimitive().isString()){
   double special=switch(json.getAsString()){case "NaN"->Double.NaN;case "Infinity"->Double.POSITIVE_INFINITY;case "-Infinity"->Double.NEGATIVE_INFINITY;default->throw new IllegalArgumentException("Invalid numeric state");};
   return c==float.class||c==Float.class?(Object)(float)special:(Object)special;}
  if(c.isPrimitive()||Number.class.isAssignableFrom(c)){try{if(!json.isJsonPrimitive()||!json.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException();var n=json.getAsBigDecimal();if(c==int.class||c==Integer.class)return n.intValueExact();if(c==long.class||c==Long.class)return n.longValueExact();if(c==byte.class||c==Byte.class)return n.byteValueExact();if(c==short.class||c==Short.class)return n.shortValueExact();if(c==double.class||c==Double.class){double d=n.doubleValue();if(Double.isFinite(d))return d;}if(c==float.class||c==Float.class){float f=n.floatValue();if(Float.isFinite(f))return f;}throw new IllegalArgumentException();}catch(ArithmeticException e){throw new IllegalArgumentException("Invalid numeric state",e);}}
  require(c);if(c.isEnum()){if(!json.isJsonPrimitive()||!json.getAsJsonPrimitive().isString())throw new IllegalArgumentException();return enumValue(c,json.getAsString());}
  if(!c.isRecord()||!json.isJsonObject())throw new IllegalArgumentException("Invalid record");var fields=c.getRecordComponents();var object=json.getAsJsonObject();if(object.size()!=fields.length)throw new IllegalArgumentException("Record shape changed");
  Object[] values=new Object[fields.length];Class<?>[] types=new Class<?>[fields.length];for(int i=0;i<fields.length;i++){var f=fields[i];if(!object.has(f.getName()))throw new IllegalArgumentException("Missing record field");types[i]=f.getType();JsonElement value=object.get(f.getName());values[i]=value.isJsonNull()&&nullableMembers.contains(c.getName()+"#"+f.getName())?null:decode(value,f.getGenericType(),depth+1,budget);}
  try{var ctor=c.getDeclaredConstructor(types);ctor.setAccessible(true);return ctor.newInstance(values);}catch(ReflectiveOperationException e){throw new IllegalArgumentException("Record could not be restored",e);}
 }
 @SuppressWarnings({"unchecked","rawtypes"}) private static Object enumValue(Class<?> type,String name){return Enum.valueOf((Class)type,name);}
 private void require(Class<?> type){if(!allowed.contains(type.getName()))throw new IllegalArgumentException("State type is not in the declared schema: "+type.getName());}
}
