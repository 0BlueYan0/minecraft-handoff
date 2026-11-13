package tw.handoff.viewer.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tw.handoff.viewer.ReconnectPolicy;

/** Adds a reconnect button to the vanilla disconnect screen. The disconnect reason is left as vanilla draws it. */
@Mixin(DisconnectedScreen.class)
abstract class DisconnectedScreenMixin extends Screen {
    @Shadow @Final private LinearLayout layout;
    @Shadow @Final private Screen parent;

    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    // Vanilla init() adds the back button, then calls layout.arrangeElements(), visitWidgets(addRenderableWidget) and repositionElements().
    // The button must join the layout before arrangeElements so those later calls size, register and center it with the rest.
    @Inject(method = "init()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/LinearLayout;arrangeElements()V"))
    private void handoff$reconnect(CallbackInfo callback) {
        var target = ReconnectPolicy.target();
        if (target.isEmpty()) return;
        String address = target.get();
        Button button = Button.builder(Component.translatable("handoff_viewer.reconnect"), pressed -> {
            Minecraft mc = Minecraft.getInstance();
            ConnectScreen.startConnecting(this.parent, mc, ServerAddress.parseString(address),
                    new ServerData("Mac host", address, ServerData.Type.OTHER), false, null);
        }).width(200).build();
        this.layout.addChild(button);
    }
}
