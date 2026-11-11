package tw.handoff.viewer.mixin;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tw.handoff.viewer.ViewerClient;
import tw.handoff.relay.core.ControlWire;
@Mixin(ClientCommonPacketListenerImpl.class)
abstract class ControlReceiveMixin {
 @Inject(method="handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V",at=@At("HEAD"),cancellable=true)
 private void handoff$control(ClientboundCustomPayloadPacket packet,CallbackInfo ci){
  if((ViewerClient.THIN||ViewerClient.LAB)&&packet.payload() instanceof BrandPayload b&&b.brand().startsWith(ControlWire.PREFIX)){ViewerClient.receive(b.brand());ci.cancel();}
 }
}
