package tw.handoff.relay.bridge;
import io.netty.buffer.Unpooled;
import java.util.Base64;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.game.*;
import tw.handoff.relay.core.*;

final class RemoteControl {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("minecraft-handoff-relay");
 private final HostBridge host;private final Minecraft mc;private final ControlLease<NativePeer> lease=new ControlLease<>();private final RemoteDriver driver;
 private long lastInput;private int ticks;
 private volatile boolean remote;
 private final IdleWalker walk;
 RemoteControl(HostBridge host,boolean idleWalk)throws Exception{
  this.host=host;mc=host.minecraft();driver=new RemoteDriver(mc,this::release);
  if(idleWalk){walk=new IdleWalker(mc,new IdleWalkPlan(System::nanoTime),this::remoteOwns);driver.onHumanInput(walk::humanInput);}else walk=null;
 }
 /** Q 先於 R:0 送出。viewer 收到 R: 才送 ACK，pong 因此一定排在 ACK 前面，閘門不會先看到 ACK 才看到往返時間。 */
 void ready(NativePeer peer){
  Runnable sendReady=()->peer.send(message(ControlWire.PREFIX+"R:0"));
  JoinState join=peer.join;
  if(join!=null)join.ready(sendReady);else sendReady.run();
 }
 private void grant(NativePeer peer){
  if(!peer.lifecycle.active()||peer.closed||mc.player==null)return;
  if(mc.player.containerMenu!=mc.player.inventoryMenu||!mc.player.containerMenu.getCarried().isEmpty()){peer.send(message(ControlWire.PREFIX+"E:Close the host container before taking control"));return;}
  LOG.debug("Viewer asks for control, host screen: {}",mc.screen==null?"none":mc.screen.getClass().getName());
  boolean saoPause=mc.screen!=null&&PauseScreens.saoPause(mc.screen.getClass().getName());
  if(mc.screen!=null&&!saoPause&&!(mc.screen instanceof PauseScreen)&&!(mc.screen instanceof ChatScreen)&&!(mc.screen instanceof InventoryScreen)){peer.send(message(ControlWire.PREFIX+"E:Close the host screen before taking control"));return;}
  if(mc.screen!=null&&PauseScreens.closeOnGrant(saoPause,host.lab!=null))mc.setScreen(null);
  // driver.begin records mc.player.input as the original, so an idle walk has to be over before it runs.
  if(walk!=null)walk.abort("viewer took control");
  release();lease.grant(peer,true);if(host.lab==null)driver.begin();remote=true;lastInput=System.nanoTime();announce();
 }
 void release(){if(walk!=null)walk.abort("control released");NativePeer p=lease.owner();if(p!=null)lease.release(p);driver.end();remote=false;if(p!=null)announce();}
 private void announce(){for(NativePeer p:host.peers())if(p.lifecycle.active())p.send(message(ControlWire.PREFIX+"R:"+(lease.ownedBy(p)?lease.epoch():0)));}
 boolean remoteOwns(){return remote;}
 static ClientboundCustomPayloadPacket message(String value){return new ClientboundCustomPayloadPacket(new BrandPayload(value));}
 void input(NativePeer peer,Packet<?> packet){
  if(!(packet instanceof ServerboundCustomPayloadPacket custom)||!(custom.payload() instanceof BrandPayload brand)||!brand.brand().startsWith(ControlWire.PREFIX))return;
  String text=brand.brand();if(!peer.inputs.offer(text,text.startsWith(ControlWire.PREFIX+"I:")))peer.close("Too many queued control actions");
 }
 private void dispatch(NativePeer peer,String text){try{
   if(peer.closed||!peer.lifecycle.active())return;
   JoinState join=peer.join;
   if(join!=null&&join.handle(text,()->grant(peer)))return;
   if(text.equals(ControlWire.PREFIX+"TAKE"))return; // No join flow ran for this viewer, so there is no state to hand over.
   if(text.equals(ControlWire.PREFIX+"RELEASE")){if(lease.ownedBy(peer))release();return;}
   if(text.startsWith(ControlWire.PREFIX+"I:")){ControlFrame f=ControlFrame.decode(text);if(lease.accept(peer,f.epoch(),f.sequence())){lastInput=System.nanoTime();driver.frame(f);}return;}
   if(host.lab!=null&&text.startsWith(ControlWire.PREFIX+"G:")){var a=ControlWire.parseAction(text.replaceFirst("HANDOFF2:G:","HANDOFF2:A:"));if(lease.accept(peer,a.epoch(),a.sequence())){lastInput=System.nanoTime();host.lab.forward(a.data());}return;}
   if(text.startsWith(ControlWire.PREFIX+"A:")){var a=ControlWire.parseAction(text);if(lease.accept(peer,a.epoch(),a.sequence())){lastInput=System.nanoTime();action(a.data());}}
  }catch(RuntimeException|LinkageError bad){peer.close("Invalid control input");}
 }
 @SuppressWarnings("unchecked") private void action(String data){
  byte[] bytes=Base64.getDecoder().decode(data);var buffer=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes),host.registries());
  Packet<?> packet;try{packet=(Packet<?>)GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(host.registries())).codec().decode(buffer);if(buffer.isReadable())throw new IllegalArgumentException();}finally{buffer.release();}
  if(mc.player==null||mc.gameMode==null||mc.getConnection()==null)return;
  if(packet instanceof ServerboundChatPacket chat)mc.getConnection().sendChat(chat.message());
  else if(packet instanceof ServerboundChatCommandPacket command)mc.getConnection().sendCommand(command.command());
  else if(packet instanceof ServerboundChatCommandSignedPacket command)mc.getConnection().sendCommand(command.command());
  else if(packet instanceof ServerboundClientCommandPacket command){if(command.getAction()==ServerboundClientCommandPacket.Action.PERFORM_RESPAWN&&mc.player.isDeadOrDying()){mc.player.respawn();mc.setScreen(null);}}
  else if(packet instanceof ServerboundContainerClickPacket click){
   var menu=mc.player.containerMenu;
   if(click.getContainerId()==menu.containerId&&(click.getSlotNum()==-999||(click.getSlotNum()>=0&&click.getSlotNum()<menu.slots.size())))mc.gameMode.handleInventoryMouseClick(menu.containerId,click.getSlotNum(),click.getButtonNum(),click.getClickType(),mc.player);
  }else if(packet instanceof ServerboundContainerClosePacket close){if(close.getContainerId()==mc.player.containerMenu.containerId)mc.player.closeContainer();}
  else if(packet instanceof ServerboundPlayerActionPacket action){switch(action.getAction()){
   case DROP_ITEM->mc.player.drop(false);case DROP_ALL_ITEMS->mc.player.drop(true);
   case SWAP_ITEM_WITH_OFFHAND->mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,net.minecraft.core.BlockPos.ZERO,net.minecraft.core.Direction.DOWN));
   default->{} // Digging, physics, item use and prediction belong exclusively to the original engine.
  }}
 }
 void tick(){
  for(NativePeer peer:host.peers())for(String text:peer.inputs.drain())dispatch(peer,text);
  if(mc.player==null||mc.level==null||mc.getConnection()==null){if(walk!=null)walk.tick();release();return;}
  NativePeer owner=lease.owner();if(owner!=null&&(owner.closed||!owner.lifecycle.active()||System.nanoTime()-lastInput>2_000_000_000L))release();
  else if(owner!=null&&System.nanoTime()-lastInput>300_000_000L)driver.clear();
  driver.tick();
  if(walk!=null)walk.tick();
 }
 void pose(){if(mc.player==null||host.lab!=null)return;var p=mc.player;
  String value=ControlWire.PREFIX+"P:"+p.getX()+":"+p.getY()+":"+p.getZ()+":"+p.getYRot()+":"+p.getXRot()+":"+(p.onGround()?1:0)+":"+p.getInventory().selected;
  for(NativePeer peer:host.peers())if(peer.live&&peer.lifecycle.active()){peer.send(message(value));if(ticks%10==0){var menu=p.containerMenu;peer.send(new ClientboundContainerSetContentPacket(menu.containerId,menu.getStateId(),menu.getItems(),menu.getCarried()));}}
  ticks++;
 }
}
