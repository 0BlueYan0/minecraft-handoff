package tw.handoff.relay.bridge;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PacketLabFrameTest {
 @Test void ordinaryPacketWithTrailingGarbageNeverQualifiesForUpstream(){
  SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
  var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
  byte[] data;try{GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(RegistryAccess.EMPTY)).codec().encode(b,new ServerboundSwingPacket(InteractionHand.MAIN_HAND));data=new byte[b.readableBytes()];b.readBytes(data);}finally{b.release();}
  assertInstanceOf(ServerboundSwingPacket.class,PacketLab.decodeFrame(data,RegistryAccess.EMPTY));
  byte[] malformed=java.util.Arrays.copyOf(data,data.length+1);assertThrows(IllegalArgumentException.class,()->PacketLab.decodeFrame(malformed,RegistryAccess.EMPTY));
 }
}
