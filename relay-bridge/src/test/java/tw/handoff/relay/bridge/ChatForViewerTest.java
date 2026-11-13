package tw.handoff.relay.bridge;
import com.mojang.serialization.Lifecycle;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.ChatTypeDecoration;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FilterMask;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MessageSignatureCache;
import net.minecraft.network.chat.SignedMessageBody;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDeleteChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatForViewerTest {
 static RegistryAccess.Frozen access;static FrameCodec codec;static ChatType.Bound bound;
 @BeforeAll static void registries(){
  SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
  var chat=new MappedRegistry<ChatType>(Registries.CHAT_TYPE,Lifecycle.stable());
  var key=ResourceKey.create(Registries.CHAT_TYPE,ResourceLocation.parse("sao:proximity"));
  Registry.register(chat,key,new ChatType(ChatTypeDecoration.withSender("chat.type.text"),ChatTypeDecoration.withSender("chat.type.text.narrate")));
  chat.freeze();
  access=new RegistryAccess.ImmutableRegistryAccess(List.of(chat)).freeze();
  codec=new FrameCodec(access);
  bound=new ChatType.Bound(chat.getOrThrow(key),Component.literal("BBBroccoli"),java.util.Optional.empty());
 }
 /** A body that, like SAO's, refers to an earlier message by its index in the receiver's signature cache. */
 static SignedMessageBody.Packed body(String text){
  return new SignedMessageBody.Packed(text,Instant.ofEpochSecond(1_763_000_000L),42L,new LastSeenMessages.Packed(List.of(new MessageSignature.Packed(3))));
 }
 static ClientboundPlayerChatPacket chat(String text,Component unsigned,FilterMask mask){
  return new ClientboundPlayerChatPacket(UUID.randomUUID(),7,null,body(text),unsigned,mask,bound);
 }
 static String name(byte[] frame){
  int id=tw.handoff.relay.core.OpaqueFrames.readVarInt(frame,new int[]{0});
  String[] found={null};GameProtocols.CLIENTBOUND_TEMPLATE.listPackets((type,number)->{if(number==id)found[0]=type.id().getPath();});return found[0];
 }
 static List<byte[]> forViewer(Packet<?> packet){byte[] frame=codec.freeze(packet).getFirst();return ChatForViewer.rewrite(frame,name(frame),codec);}
 static Component shown(List<byte[]> frames){
  assertEquals(1,frames.size());
  assertInstanceOf(ClientboundSystemChatPacket.class,codec.decode(frames.getFirst()));
  var system=(ClientboundSystemChatPacket)codec.decode(frames.getFirst());
  assertFalse(system.overlay());return system.content();
 }
 /** What the viewer decodes for this text: the wire turns unstyled literal arguments into plain strings. */
 static Component wire(Component expected){return ((ClientboundSystemChatPacket)codec.decode(codec.freeze(new ClientboundSystemChatPacket(expected,false)).getFirst())).content();}

 @Test void aViewerThatJoinedLateCannotReadTheForwardedChat(){
  // This empty lookup is what vanilla's ClientPacketListener disconnects on with multiplayer.disconnect.invalid_packet.
  assertTrue(body("好遠").unpack(MessageSignatureCache.createDefault()).isEmpty());
 }
 @Test void playerChatReachesTheViewerAsTheDecoratedSystemMessage(){
  assertEquals(wire(bound.decorate(Component.literal("好遠"))),shown(forViewer(chat("好遠",null,FilterMask.PASS_THROUGH))));
 }
 @Test void unsignedContentWinsOverTheSignedText(){
  Component styled=Component.literal("好遠").withStyle(net.minecraft.ChatFormatting.AQUA);
  assertEquals(wire(bound.decorate(styled)),shown(forViewer(chat("好遠",styled,FilterMask.PASS_THROUGH))));
 }
 @Test void aPartlyFilteredMessageShowsTheMaskedText(){
  var mask=new FilterMask(5);mask.setFiltered(0);
  assertEquals(wire(bound.decorate(mask.applyWithFormatting("abcde"))),shown(forViewer(chat("abcde",null,mask))));
 }
 @Test void aFullyFilteredMessageAndDeleteChatAreNotSent(){
  assertEquals(0,forViewer(chat("hidden",null,FilterMask.FULLY_FILTERED)).size());
  assertEquals(0,forViewer(new ClientboundDeleteChatPacket(new MessageSignature.Packed(3))).size());
 }
 @Test void otherFramesPassUnchanged(){
  byte[] frame=codec.freeze(new ClientboundSetTimePacket(1L,2L,true)).getFirst();
  var out=ChatForViewer.rewrite(frame,name(frame),codec);
  assertEquals(1,out.size());assertSame(frame,out.getFirst());
 }
 @Test void aChatFrameThatCannotBeReadIsDroppedNotForwarded(){
  byte[] frame=codec.freeze(chat("x",null,FilterMask.PASS_THROUGH)).getFirst();
  byte[] cut=java.util.Arrays.copyOf(frame,frame.length-3);
  assertEquals(0,ChatForViewer.rewrite(cut,"player_chat",codec).size());
 }
 @Test void chatInsideABundleIsConvertedOnThePacketPath(){
  var bundle=new net.minecraft.network.protocol.game.ClientboundBundlePacket(List.of(chat("好遠",null,FilterMask.PASS_THROUGH),new ClientboundDeleteChatPacket(new MessageSignature.Packed(3)),new ClientboundSetTimePacket(1L,2L,true)));
  var names=new java.util.ArrayList<String>();for(byte[] f:codec.freeze(ChatForViewer.rewrite(bundle)))names.add(name(f));
  assertEquals(List.of("bundle_delimiter","system_chat","set_time","bundle_delimiter"),names);
  var frames=codec.freeze(ChatForViewer.rewrite(bundle));
  assertInstanceOf(net.minecraft.network.protocol.game.ClientboundBundleDelimiterPacket.class,codec.decode(frames.getFirst()));
  assertInstanceOf(net.minecraft.network.protocol.game.ClientboundBundleDelimiterPacket.class,codec.decode(frames.getLast()));
 }
 @Test void packetFormUsedWithoutGenericForwarding(){
  assertInstanceOf(ClientboundSystemChatPacket.class,ChatForViewer.rewrite(chat("好遠",null,FilterMask.PASS_THROUGH)));
  assertNull(ChatForViewer.rewrite(new ClientboundDeleteChatPacket(new MessageSignature.Packed(3))));
  var time=new ClientboundSetTimePacket(1L,2L,true);assertSame(time,ChatForViewer.rewrite(time));
 }
}
