package tw.handoff.sao.probe;
import tw.handoff.sao.SaoSnapshot;
import java.nio.file.*;
import java.io.File;
public final class NativeCapture {
 // A game JVM keeps the StateProbeAgent class from its first attach, and that older agentmain only reports the root
 // exception's class name. This class is reloaded from each probe JAR, so it reports its own failures and returns normally.
 public static void capture(Object client,Path game,Path output,Path status){
  try{captureOrThrow(client,game,output,status);}
  catch(Throwable error){StateProbeAgent.write(status,"CAPTURE_FAILED:"+error.getClass().getSimpleName()+":capture"+StateProbeAgent.detail(error));}
 }
 static void captureOrThrow(Object client,Path game,Path output,Path status)throws Exception{
  File actual=null;for(String name:new String[]{"gameDirectory","field_1697"})try{actual=(File)client.getClass().getField(name).get(client);break;}catch(NoSuchFieldException absent){}
  if(actual==null||!actual.toPath().toRealPath().equals(game)||output.toAbsolutePath().normalize().startsWith(game))throw new IllegalArgumentException("Wrong instance");
  requireWorld(client);
  var snapshot=SaoSnapshot.capture(client.getClass().getClassLoader(),System.nanoTime());
  try{Files.createFile(output,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));}catch(UnsupportedOperationException windows){Files.createFile(output);}
  Files.writeString(output,snapshot.toString(),StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);
  StateProbeAgent.write(status,"READ_ONLY_CAPTURED:"+SaoSnapshot.summary(snapshot));
 }
 static void requireWorld(Object client)throws Exception{
  if(read(client,"player","field_1724")==null||read(client,"level","field_1687")==null)throw new IllegalStateException("Join the server before capturing state");
 }
 private static Object read(Object client,String... names)throws Exception{for(String name:names)try{return client.getClass().getField(name).get(client);}catch(NoSuchFieldException absent){}throw new IllegalStateException("Client world contract unavailable");}
}
