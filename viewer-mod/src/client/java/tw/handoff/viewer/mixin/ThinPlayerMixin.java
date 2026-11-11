package tw.handoff.viewer.mixin;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tw.handoff.viewer.ViewerClient;
@Mixin(LocalPlayer.class)
abstract class ThinPlayerMixin {
 @Inject(method="aiStep",at=@At("HEAD"),cancellable=true)
 private void handoff$noPhysics(CallbackInfo ci){if(ViewerClient.THIN)ci.cancel();}
}
