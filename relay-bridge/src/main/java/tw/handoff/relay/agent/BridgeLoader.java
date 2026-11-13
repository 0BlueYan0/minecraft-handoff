package tw.handoff.relay.agent;
import java.net.URL;
import java.net.URLClassLoader;

/** Minecraft-facing bridge classes must be defined below Knot, never by the system agent loader. */
final class BridgeLoader extends URLClassLoader {
    BridgeLoader(URL jar,ClassLoader game){super(new URL[]{jar},game);}
    /**
     * Everything under tw.handoff. comes from this loader's own jar, so a reload gets new relay-core and sao-state classes too, except the agent package.
     * The agent package is the one the game already holds from its first attach; a second copy would be a different class with the same name.
     */
    static boolean ownsClass(String name){return name.startsWith("tw.handoff.")&&!name.startsWith("tw.handoff.relay.agent.");}
    protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
        synchronized(getClassLoadingLock(name)){
            Class<?> loaded=findLoadedClass(name);
            if(loaded==null&&ownsClass(name)){
                try{loaded=findClass(name);}catch(ClassNotFoundException absent){/* Parent fallback is limited to dependencies. */}
            }
            if(loaded==null)loaded=super.loadClass(name,false);
            if(resolve)resolveClass(loaded);return loaded;
        }
    }
}
