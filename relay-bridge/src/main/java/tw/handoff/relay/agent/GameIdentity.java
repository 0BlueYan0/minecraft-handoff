package tw.handoff.relay.agent;
import java.io.File;
import java.nio.file.Path;
final class GameIdentity {
 static void require(Object client,Path requested)throws Exception{
  File actual=null;
  for(String name:new String[]{"gameDirectory","field_1697"})try{Object value=client.getClass().getField(name).get(client);if(value instanceof File file){actual=file;break;}}catch(NoSuchFieldException absent){}
  if(actual==null||!actual.toPath().toRealPath().equals(requested.toRealPath()))throw new IllegalArgumentException("Wrong Minecraft instance");
 }
}
