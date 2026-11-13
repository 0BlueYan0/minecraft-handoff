package tw.handoff.relay.bridge;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RemoveHandlerTest {
    @Test void removesTheNamedHandlerOnTheChannelThread(){
        var channel=new EmbeddedChannel();channel.pipeline().addLast("keep",new ChannelInboundHandlerAdapter());channel.pipeline().addLast("handoff_relay_capture",new ChannelInboundHandlerAdapter());
        HostBridge.removeHandler(channel,"handoff_relay_capture");
        assertNull(channel.pipeline().get("handoff_relay_capture"));assertNotNull(channel.pipeline().get("keep"));
    }
    @Test void absentHandlerIsANoOpAndASecondCallIsHarmless(){
        var channel=new EmbeddedChannel();channel.pipeline().addLast("handoff_opaque_lab",new ChannelInboundHandlerAdapter());
        HostBridge.removeHandler(channel,"missing");assertNotNull(channel.pipeline().get("handoff_opaque_lab"));
        HostBridge.removeHandler(channel,PacketLab.HANDLER);HostBridge.removeHandler(channel,PacketLab.HANDLER);
        assertNull(channel.pipeline().get(PacketLab.HANDLER));
    }
    @Test void closedChannelNeedsNoCleanup(){
        var channel=new EmbeddedChannel();channel.pipeline().addLast("handoff_relay_capture",new ChannelInboundHandlerAdapter());channel.close();
        assertDoesNotThrow(()->HostBridge.removeHandler(channel,"handoff_relay_capture"));
    }
}
