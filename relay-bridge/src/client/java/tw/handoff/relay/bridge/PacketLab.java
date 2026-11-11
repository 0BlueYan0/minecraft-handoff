package tw.handoff.relay.bridge;
import io.netty.buffer.*;
import io.netty.channel.*;
import java.util.*;
import java.nio.file.Files;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.game.*;
import tw.handoff.relay.core.*;

/** Explicit private-fixture experiment. Not enabled in normal/SAO instances. */
final class PacketLab {
 private final HostBridge host;private final Channel channel;private final PacketJournal history=new PacketJournal(8192,8*1024*1024);
 private static final Set<String> LOCAL=Set.of("keep_alive","pong","accept_teleportation","player_loaded","chunk_batch_received","client_tick_end","chat_ack","client_information","resource_pack","custom_click_action");
 static boolean enabled(net.minecraft.client.Minecraft mc)throws Exception{
  if(!Boolean.getBoolean("handoff.packetLab"))return false;
  var marker=mc.gameDirectory.toPath().resolve(".handoff-viewer");if(!Files.isRegularFile(marker)||!Files.readString(marker).equals("MinecraftHandoffViewer:1"))throw new IllegalStateException("Packet lab requires an isolated fixture");return true;
 }
 PacketLab(HostBridge host,Channel channel){this.host=host;this.channel=channel;}
 static boolean local(String name){return LOCAL.contains(name)||name.contains("cookie")||name.contains("configuration");}
 void install(){
  byte[] brand=host.codec.freeze(new ClientboundCustomPayloadPacket(new BrandPayload("lab-wire-id"))).getFirst();int id=OpaqueFrames.readVarInt(brand,new int[]{0});
  Map<Integer,String> names=new HashMap<>();GameProtocols.CLIENTBOUND_TEMPLATE.listPackets((type,number)->names.put(number,type.id().getPath()));
  var mirror=new OrderedWireMirror(id,number->MirrorRules.wire(names.get(number)),history,frame->{for(NativePeer peer:host.peers())if(peer.live)try{peer.sendFrames(List.of(frame));}catch(RuntimeException|LinkageError failed){peer.close("Lab viewer stream failed");}});
  channel.eventLoop().submit(()->channel.pipeline().addBefore("decoder","handoff_opaque_lab",new ChannelInboundHandlerAdapter(){
   public void channelRead(ChannelHandlerContext ctx,Object msg)throws Exception{
    byte[] copy=null;if(msg instanceof ByteBuf b&&b.readableBytes()<=8*1024*1024){copy=new byte[b.readableBytes()];b.getBytes(b.readerIndex(),copy);}
    ctx.fireChannelRead(msg);
    if(copy!=null)try{mirror.receive(copy);}catch(RuntimeException|LinkageError ignored){for(NativePeer peer:host.peers())peer.close("Lab wire state unavailable");}
   }
  })).syncUninterruptibly();
 }
 void replay(NativePeer peer){peer.sendFrames(history.snapshot());}
 @SuppressWarnings("unchecked") void forward(String encoded){
  byte[] bytes=Base64.getDecoder().decode(encoded);if(bytes.length>16000)throw new IllegalArgumentException("Lab frame too large");
  Packet<?> packet=decodeFrame(bytes,host.registries());
  if(local(packet.type().id().getPath()))return;
  if(packet instanceof ServerboundCustomPayloadPacket){var view=OpaqueFrames.inspect(bytes);if(!OpaqueFrames.gameplayChannel(view.channel()))return;}
  if(packet instanceof ServerboundChatPacket||packet instanceof ServerboundChatCommandPacket||packet instanceof ServerboundChatCommandSignedPacket)return; // Signed-chat ownership is outside this bounded test.
  if(packet instanceof ServerboundMovePlayerPacket move&&host.minecraft().player!=null){var p=host.minecraft().player;p.setPos(move.getX(p.getX()),move.getY(p.getY()),move.getZ(p.getZ()));p.setYRot(move.getYRot(p.getYRot()));p.setXRot(move.getXRot(p.getXRot()));}
  channel.writeAndFlush(Unpooled.wrappedBuffer(bytes));
 }
 static Packet<?> decodeFrame(byte[] bytes,net.minecraft.core.RegistryAccess.Frozen registries){
  var buffer=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes),registries);
  try{Packet<?> packet=GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries)).codec().decode(buffer);if(buffer.isReadable())throw new IllegalArgumentException("Trailing bytes in game frame");return packet;}finally{buffer.release();}
 }
}
