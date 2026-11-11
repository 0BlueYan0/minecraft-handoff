package tw.handoff.probe;
import java.lang.instrument.Instrumentation;
import java.net.*;
import java.nio.file.*;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
public final class ProbeAgent {
 private static URLClassLoader loader;
 public static void agentmain(String options,Instrumentation instrumentation)throws Exception{
  String[] p=options.split("\\.");Path status=Path.of(new String(Base64.getUrlDecoder().decode(p[1]),StandardCharsets.UTF_8));
  try{
   Class<?> mc=null;for(Class<?> c:instrumentation.getAllLoadedClasses())if(c.getName().equals("net.minecraft.class_310")||c.getName().equals("net.minecraft.client.Minecraft"))mc=c;
   if(mc==null)throw new IllegalStateException("No client");Object instance=null;for(String name:new String[]{"method_1551","getInstance"})try{instance=mc.getMethod(name).invoke(null);break;}catch(NoSuchMethodException absent){}
   if(loader==null)loader=new URLClassLoader(new URL[]{ProbeAgent.class.getProtectionDomain().getCodeSource().getLocation()},mc.getClassLoader()){
    protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{synchronized(getClassLoadingLock(name)){Class<?> c=findLoadedClass(name);if(c==null&&name.startsWith("tw.handoff.probe.client."))c=findClass(name);if(c==null)c=super.loadClass(name,false);if(resolve)resolveClass(c);return c;}}
   };
   Object target=instance;((Executor)instance).execute(()->{try{Class.forName("tw.handoff.probe.client.ClientProbe",true,loader).getMethod("run",Object.class,String.class,Path.class).invoke(null,target,p[0],status);}catch(Throwable e){try{Files.writeString(status,"PROBE_FAILED:"+e.getClass().getSimpleName());}catch(Exception ignored){}}});
  }catch(Exception e){Files.writeString(status,"PROBE_FAILED:"+e.getClass().getSimpleName());}
 }
}
