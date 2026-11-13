package tw.handoff.viewer.mixin;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import tw.handoff.viewer.ViewerDiag;

/** TAIL only runs on the render thread; the network-thread call leaves early through ensureRunningOnSameThread, so each packet counts once. */
@Mixin(ClientPacketListener.class)
abstract class ChunkDiagMixin {
 @Inject(method="handleLevelChunkWithLight",at=@At("TAIL"))
 private void handoff$chunk(ClientboundLevelChunkWithLightPacket packet,CallbackInfo ci){ViewerDiag.CHUNKS.incrementAndGet();}
 @Inject(method="handleSetChunkCacheCenter",at=@At("TAIL"))
 private void handoff$center(ClientboundSetChunkCacheCenterPacket packet,CallbackInfo ci){ViewerDiag.center(packet.getX(),packet.getZ());}
 @Inject(method="handleForgetLevelChunk",at=@At("TAIL"))
 private void handoff$forget(ClientboundForgetLevelChunkPacket packet,CallbackInfo ci){ViewerDiag.FORGETS.incrementAndGet();}
 @Inject(method="handleSetChunkCacheRadius",at=@At("TAIL"))
 private void handoff$radius(ClientboundSetChunkCacheRadiusPacket packet,CallbackInfo ci){ViewerDiag.radius=packet.getRadius();}
 @Inject(method="handleLogin",at=@At("TAIL"))
 private void handoff$login(ClientboundLoginPacket packet,CallbackInfo ci){ViewerDiag.radius=packet.chunkRadius();}
}
