package tw.handoff.relay.agent;
import java.net.URL;
import java.net.URLClassLoader;

/** Minecraft-facing bridge classes must be defined below Knot, never by the system agent loader. */
final class BridgeLoader extends URLClassLoader {
    BridgeLoader(URL jar,ClassLoader game){super(new URL[]{jar},game);}
    protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
        synchronized(getClassLoadingLock(name)){
            Class<?> loaded=findLoadedClass(name);
            if(loaded==null&&name.startsWith("tw.handoff.relay.bridge.")){
                try{loaded=findClass(name);}catch(ClassNotFoundException absent){/* Parent fallback is limited to dependencies. */}
            }
            if(loaded==null)loaded=super.loadClass(name,false);
            if(resolve)resolveClass(loaded);return loaded;
        }
    }
}
