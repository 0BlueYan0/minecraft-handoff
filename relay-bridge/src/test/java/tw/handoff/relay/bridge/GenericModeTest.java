package tw.handoff.relay.bridge;
import java.nio.file.Files;
import java.nio.file.Path;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class GenericModeTest {
 @BeforeEach @AfterEach void clear(){System.clearProperty("handoff.generic");System.clearProperty("handoff.packetLab");}
 @Test void productionNeedsNoFixtureMarker(@TempDir Path dir)throws Exception{
  System.setProperty("handoff.generic","true");
  assertEquals(PacketLab.Mode.PRODUCTION,PacketLab.mode(dir));
 }
 @Test void fixtureStillNeedsMarker(@TempDir Path dir)throws Exception{
  System.setProperty("handoff.packetLab","true");
  assertThrows(IllegalStateException.class,()->PacketLab.mode(dir));
  Files.writeString(dir.resolve(".handoff-viewer"),"MinecraftHandoffViewer:1");
  assertEquals(PacketLab.Mode.FIXTURE,PacketLab.mode(dir));
 }
 @Test void bothFlagsRefused(@TempDir Path dir)throws Exception{
  System.setProperty("handoff.generic","true");System.setProperty("handoff.packetLab","true");
  Files.writeString(dir.resolve(".handoff-viewer"),"MinecraftHandoffViewer:1");
  assertThrows(IllegalStateException.class,()->PacketLab.mode(dir));
  System.clearProperty("handoff.generic");System.clearProperty("handoff.packetLab");
  assertNull(PacketLab.mode(dir));
 }
 @Test void productionNeverReplaysHistory(){
  var lab=new PacketLab(null,null,PacketLab.Mode.PRODUCTION);
  assertFalse(lab.hasJournal());
  var sent=new java.util.ArrayList<java.util.List<byte[]>>();
  lab.replay(sent::add);
  assertEquals(0,sent.size());
 }
 static byte[] encode(net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ServerGamePacketListener> packet){
  SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
  var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
  try{GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(RegistryAccess.EMPTY)).codec().encode(b,packet);byte[] data=new byte[b.readableBytes()];b.readBytes(data);return data;}finally{b.release();}
 }
 static boolean admitted(net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ServerGamePacketListener> packet){
  byte[] bytes=encode(packet);return PacketLab.admit(bytes,PacketLab.decodeFrame(bytes,RegistryAccess.EMPTY));
 }
 static net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket payload(String channel){
  return new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(new net.minecraft.network.protocol.common.custom.DiscardedPayload(net.minecraft.resources.ResourceLocation.parse(channel)));
 }
 static int written(net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ServerGamePacketListener> packet){
  var channel=new io.netty.channel.embedded.EmbeddedChannel();
  new PacketLab(null,channel,PacketLab.Mode.PRODUCTION).forward(java.util.Base64.getEncoder().encodeToString(encode(packet)),net.minecraft.core.RegistryAccess.EMPTY.freeze(),()->null);
  int count=channel.outboundMessages().size();channel.finishAndReleaseAll();return count;
 }
 @Test void forwardWritesNothingForChatOrLauncherButWritesGameplay(){
  assertEquals(0,written(new ServerboundChatCommandPacket("say hi")));
  assertEquals(0,written(payload("launcher:auth")));
  assertEquals(0,written(payload("fabric:x")));
  assertEquals(1,written(payload("saojob:skills")));
  assertEquals(1,written(new net.minecraft.network.protocol.game.ServerboundSwingPacket(net.minecraft.world.InteractionHand.MAIN_HAND)));
 }
 @Test void chatAndLauncherStillBlocked(){
  assertFalse(admitted(new ServerboundChatCommandPacket("say hi")));
  assertFalse(admitted(payload("launcher:auth")));
  assertFalse(admitted(payload("fabric:x")));
  assertTrue(admitted(payload("saojob:skills")));
  assertTrue(admitted(new net.minecraft.network.protocol.game.ServerboundSwingPacket(net.minecraft.world.InteractionHand.MAIN_HAND)));
 }
}
