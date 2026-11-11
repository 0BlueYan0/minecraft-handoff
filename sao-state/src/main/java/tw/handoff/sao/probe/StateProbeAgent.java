package tw.handoff.sao.probe;
import java.lang.instrument.Instrumentation;
import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executor;
public final class StateProbeAgent {
 public static void agentmain(String options,Instrumentation instrumentation){Path status;
  try{
   String[] p=options.split("\\.",-1);if(p.length!=4)return;status=path(p[0]);Path game=path(p[1]).toRealPath(),out=path(p[2]),image=path(p[3]).toRealPath();if(!Files.isRegularFile(status,LinkOption.NOFOLLOW_LINKS)||!status.isAbsolute()||!out.isAbsolute()||!image.equals(status.getParent().resolve("probe.jar").toRealPath()))return;
   try{
    Class<?> type=null;for(Class<?> c:instrumentation.getAllLoadedClasses())if(c.getName().equals("net.minecraft.class_310")||c.getName().equals("net.minecraft.client.Minecraft")){if(type!=null)throw new IllegalArgumentException("Ambiguous client");type=c;}
    if(type==null)throw new IllegalArgumentException();Object mc=null;for(String method:new String[]{"method_1551","getInstance"})try{mc=type.getMethod(method).invoke(null);break;}catch(NoSuchMethodException absent){}
    Object client=mc;var loader=new URLClassLoader(new URL[]{image.toUri().toURL()},type.getClassLoader()){
     protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{synchronized(getClassLoadingLock(name)){Class<?> c=findLoadedClass(name);if(c==null&&name.startsWith("tw.handoff.sao."))c=findClass(name);if(c==null)c=super.loadClass(name,false);if(resolve)resolveClass(c);return c;}}
    };
    ((Executor)mc).execute(()->{try{Class.forName("tw.handoff.sao.probe.NativeCapture",true,loader).getMethod("capture",Object.class,Path.class,Path.class,Path.class).invoke(null,client,game,out,status);}catch(Throwable error){while(error.getCause()!=null&&error.getCause()!=error)error=error.getCause();write(status,"CAPTURE_FAILED:"+error.getClass().getSimpleName());}});
   }catch(Exception|LinkageError error){write(status,"CAPTURE_FAILED:"+error.getClass().getSimpleName());}
  }catch(Exception ignored){}
 }
 private static Path path(String value){return Path.of(new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8));}
 static void write(Path path,String value){try{Files.writeString(path,value,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING);}catch(Exception ignored){}}
}
