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

/** Generic packet forwarding. FIXTURE (handoff.packetLab) needs the isolated fixture marker and keeps a replay journal; PRODUCTION (handoff.generic) needs no marker, keeps no journal and never replays history. Off unless one flag is set. */
final class PacketLab {
 enum Mode{FIXTURE,PRODUCTION}
 private final HostBridge host;private final Channel channel;private final Mode mode;private final PacketJournal history;
 private static final Set<String> LOCAL=Set.of("keep_alive","pong","accept_teleportation","player_loaded","chunk_batch_received","client_tick_end","chat_ack","client_information","resource_pack","custom_click_action");
 static Mode mode(net.minecraft.client.Minecraft mc)throws Exception{return mode(mc.gameDirectory.toPath());}
 /** handoff.generic needs no fixture marker; handoff.packetLab still does; both together is refused. */
 static Mode mode(java.nio.file.Path gameDirectory)throws Exception{
  boolean generic=Boolean.getBoolean("handoff.generic"),fixture=Boolean.getBoolean("handoff.packetLab");
  if(generic&&fixture)throw new IllegalStateException("handoff.generic and handoff.packetLab are mutually exclusive");
  if(generic)return Mode.PRODUCTION;
  if(!fixture)return null;
  var marker=gameDirectory.resolve(".handoff-viewer");if(!Files.isRegularFile(marker)||!Files.readString(marker).equals("MinecraftHandoffViewer:1"))throw new IllegalStateException("Packet lab requires an isolated fixture");return Mode.FIXTURE;
 }
 PacketLab(HostBridge host,Channel channel,Mode mode){this.host=host;this.channel=channel;this.mode=mode;history=mode==Mode.FIXTURE?new PacketJournal(8192,8*1024*1024):null;}
 boolean hasJournal(){return history!=null;}
 /** PRODUCTION never replays history; a joining viewer gets state from the join flow instead. */
 List<byte[]> replayFrames(){return history==null?List.of():history.snapshot();}
 static boolean allowedChannel(String channel){return OpaqueFrames.gameplayChannel(channel);}
 static boolean allowedPacket(Packet<?> packet){
  if(local(packet.type().id().getPath()))return false;
  return !(packet instanceof ServerboundChatPacket||packet instanceof ServerboundChatCommandPacket||packet instanceof ServerboundChatCommandSignedPacket); // Signed-chat ownership is outside this bounded test.
 }
 static boolean local(String name){return LOCAL.contains(name)||name.contains("cookie")||name.contains("configuration");}
 void install(){
  byte[] brand=host.codec.freeze(new ClientboundCustomPayloadPacket(new BrandPayload("lab-wire-id"))).getFirst();int id=OpaqueFrames.readVarInt(brand,new int[]{0});
  Map<Integer,String> names=new HashMap<>();GameProtocols.CLIENTBOUND_TEMPLATE.listPackets((type,number)->names.put(number,type.id().getPath()));
  java.util.function.Consumer<byte[]> sink=frame->{for(NativePeer peer:host.peers())if(peer.live)try{peer.sendFrames(List.of(frame));}catch(RuntimeException|LinkageError failed){peer.close("Lab viewer stream failed");}};
  // OrderedWireMirror requires a journal; PRODUCTION has none, so it applies the same filters inline.
  java.util.function.Consumer<byte[]> mirror=history!=null?new OrderedWireMirror(id,number->MirrorRules.wire(names.get(number)),history,sink)::receive:frame->{
   int number=OpaqueFrames.readVarInt(frame,new int[]{0});if(!MirrorRules.wire(names.get(number)))return;
   if(number==id&&!OpaqueFrames.gameplayChannel(OpaqueFrames.inspect(frame).channel()))return;
   sink.accept(frame.clone());};
  channel.eventLoop().submit(()->channel.pipeline().addBefore("decoder","handoff_opaque_lab",new ChannelInboundHandlerAdapter(){
   public void channelRead(ChannelHandlerContext ctx,Object msg)throws Exception{
    byte[] copy=null;if(msg instanceof ByteBuf b&&b.readableBytes()<=8*1024*1024){copy=new byte[b.readableBytes()];b.getBytes(b.readerIndex(),copy);}
    ctx.fireChannelRead(msg);
    if(copy!=null)try{mirror.accept(copy);}catch(RuntimeException|LinkageError ignored){for(NativePeer peer:host.peers())peer.close("Lab wire state unavailable");}
   }
  })).syncUninterruptibly();
 }
 void replay(NativePeer peer){replay(peer::sendFrames);}
 void replay(java.util.function.Consumer<List<byte[]>> sink){var frames=replayFrames();if(!frames.isEmpty())sink.accept(frames);}
 /** Everything forward checks between decoding and writing upstream; false means the frame is dropped. */
 static boolean admit(byte[] bytes,Packet<?> packet){
  if(!allowedPacket(packet))return false;
  return !(packet instanceof ServerboundCustomPayloadPacket)||allowedChannel(OpaqueFrames.inspect(bytes).channel());
 }
 void forward(String encoded){forward(encoded,host.registries(),()->host.minecraft().player);}
 /** The registries and local player are parameters so a test can drive forward with an EmbeddedChannel and no running game. */
 @SuppressWarnings("unchecked") void forward(String encoded,net.minecraft.core.RegistryAccess.Frozen registries,java.util.function.Supplier<net.minecraft.client.player.LocalPlayer> localPlayer){
  byte[] bytes=Base64.getDecoder().decode(encoded);if(bytes.length>16000)throw new IllegalArgumentException("Lab frame too large");
  Packet<?> packet=decodeFrame(bytes,registries);
  if(!admit(bytes,packet))return;
  if(packet instanceof ServerboundMovePlayerPacket move&&localPlayer.get()!=null){var p=localPlayer.get();p.setPos(move.getX(p.getX()),move.getY(p.getY()),move.getZ(p.getZ()));p.setYRot(move.getYRot(p.getYRot()));p.setXRot(move.getXRot(p.getXRot()));}
  channel.writeAndFlush(Unpooled.wrappedBuffer(bytes));
 }
 static Packet<?> decodeFrame(byte[] bytes,net.minecraft.core.RegistryAccess.Frozen registries){
  var buffer=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes),registries);
  try{Packet<?> packet=GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries)).codec().decode(buffer);if(buffer.isReadable())throw new IllegalArgumentException("Trailing bytes in game frame");return packet;}finally{buffer.release();}
 }
}
