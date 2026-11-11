package tw.handoff.relay.bridge;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.network.protocol.common.custom.BrandPayload;
final class MirrorRules {
    static boolean forward(Packet<?> packet){
        String name=packet.type().id().getPath();
        if(!wire(name))return false;
        if(packet instanceof ClientboundCustomPayloadPacket custom){
            if(custom.payload() instanceof BrandPayload||custom.payload().type().id().getNamespace().equals("handoff"))return false;
            if(custom.payload() instanceof DiscardedPayload)return false;
            if(custom.payload().type().id().toString().equals("launcher:validation"))return false;
        }
        return true;
    }
    static boolean wire(String name){return name!=null&&!name.contains("cookie")&&!name.equals("keep_alive")&&!name.equals("ping")&&!name.startsWith("resource_pack")&&!name.equals("transfer")&&!name.equals("start_configuration");}
}
