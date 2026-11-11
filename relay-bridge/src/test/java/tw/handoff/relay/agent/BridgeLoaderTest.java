package tw.handoff.relay.agent;
import java.nio.file.Path;
import net.minecraft.network.protocol.Packet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BridgeLoaderTest {
    @Test void bridgeIsChildFirstWhileMinecraftTypesKeepGameIdentity()throws Exception{
        ClassLoader parent=Thread.currentThread().getContextClassLoader();
        Class<?> original=Class.forName("tw.handoff.relay.bridge.MirrorRules",false,parent);
        try(BridgeLoader loader=new BridgeLoader(Path.of(System.getProperty("handoff.bridgeDevJar")).toUri().toURL(),parent)){
            Class<?> child=Class.forName(original.getName(),false,loader);
            assertNotSame(original,child);assertSame(loader,child.getClassLoader());
            assertSame(Packet.class,loader.loadClass(Packet.class.getName()));
        }
    }
}
