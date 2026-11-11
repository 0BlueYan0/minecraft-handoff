package tw.handoff.viewer.mixin;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tw.handoff.viewer.ViewerClient;
@Mixin(Connection.class)
abstract class ControlSendMixin {
 @Inject(method="send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V",at=@At("HEAD"),cancellable=true)
 private void handoff$envelope(Packet<?> packet,PacketSendListener listener,boolean flush,CallbackInfo ci){if(ViewerClient.wrapAction(packet))ci.cancel();}
}
