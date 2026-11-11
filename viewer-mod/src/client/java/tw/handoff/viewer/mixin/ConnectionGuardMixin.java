package tw.handoff.viewer.mixin;
import java.net.InetSocketAddress;
import io.netty.channel.ChannelFuture;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tw.handoff.viewer.ViewerClient;

@Mixin(Connection.class)
abstract class ConnectionGuardMixin {
    @Inject(method="connect(Ljava/net/InetSocketAddress;ZLnet/minecraft/network/Connection;)Lio/netty/channel/ChannelFuture;",at=@At("HEAD"))
    private static void handoff$guard(InetSocketAddress address,boolean nativeTransport,Connection connection,CallbackInfoReturnable<ChannelFuture> callback){ViewerClient.check(address);}
}
