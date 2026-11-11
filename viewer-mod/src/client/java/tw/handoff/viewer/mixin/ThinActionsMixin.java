package tw.handoff.viewer.mixin;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import tw.handoff.viewer.ViewerClient;
@Mixin(Minecraft.class)
abstract class ThinActionsMixin {
 @Inject(method="createTitle",at=@At("HEAD"),cancellable=true)
 private void handoff$title(CallbackInfoReturnable<String> ci){ci.setReturnValue(ViewerClient.title());}
 @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
 private void handoff$attack(CallbackInfoReturnable<Boolean> ci){if(ViewerClient.THIN)ci.setReturnValue(false);}
 @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
 private void handoff$dig(boolean down,CallbackInfo ci){if(ViewerClient.THIN)ci.cancel();}
 @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true)
 private void handoff$use(CallbackInfo ci){if(ViewerClient.THIN)ci.cancel();}
}
