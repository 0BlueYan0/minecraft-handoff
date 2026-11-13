package tw.handoff.relay.bridge;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDeleteChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A viewer joins mid-session with an empty message signature cache. player_chat and delete_chat point into that cache by index,
 * so vanilla cannot unpack them and disconnects with "Server sent an invalid packet" (2026-11-13, a viewer on GeForce NOW dropped
 * the second a player spoke). The viewer gets the text vanilla would have shown, as a system message, and no delete_chat.
 * The host's own connection and what SAO sees are untouched.
 */
final class ChatForViewer {
    private static final Logger LOG=LoggerFactory.getLogger("minecraft-handoff-relay");
    private static volatile boolean warned;
    private ChatForViewer(){}
    /** The packet the viewer gets instead, or null when it gets nothing. Any other packet comes back as it is. */
    @SuppressWarnings("unchecked")
    static Packet<?> rewrite(Packet<?> packet){
        // FrameCodec.freeze writes a bundle's children one by one, so chat inside a bundle needs the same treatment.
        if(packet instanceof net.minecraft.network.protocol.game.ClientboundBundlePacket bundle){
            var children=new java.util.ArrayList<Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>>();
            for(Packet<?> child:bundle.subPackets()){Packet<?> out=rewrite(child);if(out!=null)children.add((Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>)out);}
            return new net.minecraft.network.protocol.game.ClientboundBundlePacket(children);
        }
        if(packet instanceof ClientboundDeleteChatPacket)return null;
        if(!(packet instanceof ClientboundPlayerChatPacket chat))return packet;
        // Same choice as vanilla ChatListener: a fully filtered message is hidden, a masked one shows the masked signed text, otherwise the unsigned content wins.
        if(chat.filterMask().isFullyFiltered())return null;
        Component content=chat.filterMask().isEmpty()
            ?(chat.unsignedContent()!=null?chat.unsignedContent():Component.literal(chat.body().content()))
            :chat.filterMask().applyWithFormatting(chat.body().content());
        if(content==null)return null;
        return new ClientboundSystemChatPacket(chat.chatType().decorate(content),false);
    }
    /** Generic forwarding works on raw frames; only the two chat packets are decoded. name is the frame's clientbound packet name. */
    static List<byte[]> rewrite(byte[] frame,String name,FrameCodec codec){
        if("delete_chat".equals(name))return List.of();
        if(!"player_chat".equals(name))return List.of(frame);
        try{Packet<?> out=rewrite(codec.decode(frame));return out==null?List.of():codec.freeze(out);}
        catch(RuntimeException|LinkageError unreadable){
            // Forwarding it unchanged would disconnect the viewer, so the message is left out.
            if(!warned){warned=true;LOG.warn("Relay left out a player chat it could not convert for the viewer: {}",unreadable.toString());}
            return List.of();
        }
    }
}
