package tw.handoff.relay.agent;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BridgeLoaderOwnershipTest {
    @Test void ownsEverythingUnderHandoffExceptTheAgentPackage(){
        assertTrue(BridgeLoader.ownsClass("tw.handoff.relay.bridge.HostBridge"));
        assertTrue(BridgeLoader.ownsClass("tw.handoff.relay.core.ControlFrame"));
        assertTrue(BridgeLoader.ownsClass("tw.handoff.sao.SaoSnapshot"));
        assertFalse(BridgeLoader.ownsClass("tw.handoff.relay.agent.EntryAgent"));
        assertFalse(BridgeLoader.ownsClass("tw.handoff.relay.agent.BridgeLoader"));
        assertFalse(BridgeLoader.ownsClass("net.minecraft.client.Minecraft"));assertFalse(BridgeLoader.ownsClass("java.lang.String"));
        assertFalse(BridgeLoader.ownsClass("tw.handoffx.Other"));
    }
    @Test void relayCoreAndSaoComeFromTheNewJarAndAgentFromTheParent()throws Exception{
        ClassLoader parent=Thread.currentThread().getContextClassLoader();
        try(BridgeLoader loader=new BridgeLoader(Path.of(System.getProperty("handoff.bridgeDevJar")).toUri().toURL(),parent)){
            for(String name:new String[]{"tw.handoff.relay.core.ControlFrame","tw.handoff.sao.SaoSnapshot"}){
                Class<?> own=Class.forName(name,false,loader);
                assertSame(loader,own.getClassLoader(),name);assertNotSame(Class.forName(name,false,parent),own,name);
            }
            assertSame(EntryAgent.class,Class.forName("tw.handoff.relay.agent.EntryAgent",false,loader));
        }
    }
    @Test void twoLoadersOnTheSameJarGiveSeparateClasses()throws Exception{
        ClassLoader parent=Thread.currentThread().getContextClassLoader();var url=Path.of(System.getProperty("handoff.bridgeDevJar")).toUri().toURL();
        try(BridgeLoader first=new BridgeLoader(url,parent);BridgeLoader second=new BridgeLoader(url,parent)){
            assertNotSame(Class.forName("tw.handoff.relay.core.ControlFrame",false,first),Class.forName("tw.handoff.relay.core.ControlFrame",false,second));
        }
    }
}
