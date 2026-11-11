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
import net.minecraft.network.protocol.game.ClientboundBundleDelimiterPacket;
import net.minecraft.network.protocol.game.GameProtocols;

final class FrameCodec {
    private final StreamCodec codec;
    FrameCodec(RegistryAccess access){codec=GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(access)).codec();}
    List<byte[]> freeze(Packet<?> packet){
        List<byte[]> frames=new ArrayList<>();
        if(packet instanceof ClientboundBundlePacket bundle){
            frames.add(encode(new ClientboundBundleDelimiterPacket()));
            for(Packet<?> child:bundle.subPackets())if(MirrorRules.forward(child))frames.addAll(freeze(child));
            frames.add(encode(new ClientboundBundleDelimiterPacket()));
        }else frames.add(encode(packet));
        return frames;
    }
    @SuppressWarnings("unchecked")
    private byte[] encode(Packet<?> packet){
        ByteBuf buffer=Unpooled.buffer();
        try{codec.encode(buffer,packet);if(buffer.readableBytes()>8*1024*1024)throw new IllegalStateException("Relay frame too large");byte[] data=new byte[buffer.readableBytes()];buffer.readBytes(data);return data;}
        finally{buffer.release();}
    }
}
