package tw.handoff.relay.bridge;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.GameProtocols;

final class FrameCodec {
    private final StreamCodec codec;
    FrameCodec(RegistryAccess access){codec=GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(access)).codec();}
    List<byte[]> freeze(Packet<?> packet){
        List<byte[]> frames=new ArrayList<>();
        if(packet instanceof ClientboundBundlePacket bundle){
            frames.add(delimiter());
            for(Packet<?> child:bundle.subPackets())if(MirrorRules.forward(child))frames.addAll(freeze(child));
            frames.add(delimiter());
        }else frames.add(encode(packet));
        return frames;
    }
    /**
     * The delimiter's codec only accepts the protocol's own instance, so encoding a new ClientboundBundleDelimiterPacket threw
     * and every bundle on this path was left out. It has no body: the frame is just its packet id.
     */
    private static byte[] delimiter(){
        int[] id={-1};GameProtocols.CLIENTBOUND_TEMPLATE.listPackets((type,number)->{if(type.id().getPath().equals("bundle_delimiter"))id[0]=number;});
        if(id[0]<0||id[0]>127)throw new IllegalStateException("bundle_delimiter id");
        return new byte[]{(byte)id[0]};
    }
    /** One clientbound frame (packet id first) back into a packet, with the same registries the frames are written with. */
    Packet<?> decode(byte[] frame){
        ByteBuf buffer=Unpooled.wrappedBuffer(frame);
        try{Packet<?> packet=(Packet<?>)codec.decode(buffer);if(buffer.isReadable())throw new IllegalArgumentException("Trailing bytes in game frame");return packet;}
        finally{buffer.release();}
    }
    @SuppressWarnings("unchecked")
    private byte[] encode(Packet<?> packet){
        ByteBuf buffer=Unpooled.buffer();
        try{codec.encode(buffer,packet);if(buffer.readableBytes()>8*1024*1024)throw new IllegalStateException("Relay frame too large");byte[] data=new byte[buffer.readableBytes()];buffer.readBytes(data);return data;}
        finally{buffer.release();}
    }
}
