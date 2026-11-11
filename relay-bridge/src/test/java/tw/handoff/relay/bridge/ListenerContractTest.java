package tw.handoff.relay.bridge;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ListenerContractTest {
    interface MappedNames { PacketFlow a(); ConnectionProtocol b(); }
    @Test void listenerContractSurvivesProductionMethodRenaming()throws Throwable{
        var channel=new EmbeddedChannel();
        try{
            var peer=new NativePeer(null,new Connection(PacketFlow.SERVERBOUND),channel);
            assertEquals(PacketFlow.SERVERBOUND,peer.invoke(null,MappedNames.class.getMethod("a"),null));
            assertEquals(ConnectionProtocol.HANDSHAKING,peer.invoke(null,MappedNames.class.getMethod("b"),null));
        }finally{channel.finishAndReleaseAll();}
    }
}
