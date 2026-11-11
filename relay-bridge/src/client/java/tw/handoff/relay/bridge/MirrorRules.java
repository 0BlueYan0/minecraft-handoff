package tw.handoff.relay.bridge;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
final class MirrorRules {
    static boolean forward(Packet<?> packet){
        String name=packet.type().id().getPath();
        if(name.contains("cookie")||name.equals("keep_alive")||name.equals("ping")
                ||name.startsWith("resource_pack")||name.equals("transfer")||name.equals("start_configuration"))return false;
        if(packet instanceof ClientboundCustomPayloadPacket custom){
            if(custom.payload() instanceof DiscardedPayload)return false;
            if(custom.payload().type().id().toString().equals("launcher:validation"))return false;
        }
        return true;
    }
}
