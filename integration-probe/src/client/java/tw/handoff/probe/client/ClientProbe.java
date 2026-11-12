package tw.handoff.probe.client;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.world.inventory.ClickType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
/** Invokes application APIs in a marked, isolated private-world fixture; no OS/UI input. */
public final class ClientProbe {
 private static boolean installed;private static String command;private static Path output;private static int remaining;
 public static void run(Object value,String operation,Path status)throws Exception{
  Minecraft mc=(Minecraft)value;Path marker=mc.gameDirectory.toPath().resolve(".handoff-viewer");
  if(!Files.isRegularFile(marker)||!Files.readString(marker).equals("MinecraftHandoffViewer:1"))throw new IllegalStateException("Only isolated private fixtures are allowed");
  if(output!=null)throw new IllegalStateException("Probe already running");
  if(operation.equals("saoRoundtrip")){Files.writeString(status,SaoFixtureRoundtrip.run(mc));return;}
  if(operation.equals("labStatus")){Files.writeString(status,labStatus(mc));return;}
  if(operation.equals("nativeAttack")){mc.setScreen(null);mc.gameRenderer.pick(1);for(String name:new String[]{"startAttack","method_1536"})try{var method=mc.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(mc);Files.writeString(status,"NATIVE_ATTACK_SWINGING="+mc.player.swinging);return;}catch(NoSuchMethodException absent){}throw new IllegalStateException("Attack mapping unavailable");}
  command=operation;output=status;remaining=operation.equals("walk")?20:operation.equals("dig")?8:5;
  if(!installed){installed=true;ClientTickEvents.START_CLIENT_TICK.register(ClientProbe::tick);}
  if(operation.equals("pause"))mc.setScreen(new PauseScreen(true));
  else if(operation.equals("chat"))mc.getConnection().sendChat("handoff-probe-chat");
  else if(operation.equals("take")||operation.equals("release"))mc.getConnection().send(new ServerboundCustomPayloadPacket(new BrandPayload("HANDOFF2:"+operation.toUpperCase(java.util.Locale.ROOT))));
  else if(operation.equals("moveItem")){mc.gameMode.handleInventoryMouseClick(0,36,0,ClickType.PICKUP,mc.player);mc.gameMode.handleInventoryMouseClick(0,37,0,ClickType.PICKUP,mc.player);}
  else if(operation.equals("respawn"))mc.player.respawn();
  else if(operation.equals("labEcho")||operation.equals("localChange"))Class.forName("tw.handoff.lab.LabClient",false,mc.getClass().getClassLoader()).getMethod(operation.equals("labEcho")?"probe":"localChange").invoke(null);
 }
 private static Object read(ClassLoader loader,String type,String name)throws Exception{var field=Class.forName(type,false,loader).getDeclaredField(name);field.setAccessible(true);return field.get(null);}
 /** The fixture's own status line plus SAOUI values read through reflection, the viewer title and what the connection can send. Read only. */
 @SuppressWarnings("unchecked") private static String labStatus(Minecraft mc)throws Exception{
  ClassLoader loader=mc.getClass().getClassLoader();
  var text=new StringBuilder((String)Class.forName("tw.handoff.lab.LabClient",false,loader).getMethod("status").invoke(null));
  try{text.append(",mana=").append(read(loader,"fr.sao.saoui.hud.ManaBar","mana")).append("/").append(read(loader,"fr.sao.saoui.hud.ManaBar","maxMana"));}catch(Exception absent){text.append(",mana=unavailable");}
  try{String skills="fr.sao.saoui.skills.SkillStatusClient";text.append(",level=").append(read(loader,skills,"level")).append(",allSkills=").append(new java.util.TreeMap<>((java.util.Map<Integer,String>)read(loader,skills,"allSkills")));}catch(Exception absent){text.append(",skills=unavailable");}
  try{
   var ends=(java.util.Map<String,Long>)read(loader,"fr.sao.saoui.skills.WeaponCooldownTracker","cooldownEndNanos");long now=System.nanoTime();var remaining=new java.util.TreeMap<String,Long>();
   for(var entry:ends.entrySet())remaining.put(entry.getKey(),Math.max(0,(entry.getValue()-now)/1_000_000));
   text.append(",weaponRemainingMs=").append(remaining);
  }catch(Exception absent){text.append(",weapon=unavailable");}
  try{text.append(",questHuds=").append(tw.handoff.sao.SaoSnapshot.capture(loader,System.nanoTime()).getAsJsonArray("quests").size());}catch(Exception|LinkageError failure){text.append(",questHuds=captureFailed:").append(failure.getClass().getSimpleName());}
  try{text.append(",title=").append(Class.forName("tw.handoff.viewer.ViewerClient",false,loader).getMethod("title").invoke(null));}catch(Exception absent){text.append(",title=none");}
  var mux=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("saocore","mux");
  text.append(",canSendMux=").append(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(mux)));
  text.append(",sendableChannels=").append(net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.getSendable().size());
  return text.toString();
 }
 private static void tick(Minecraft mc){if(output==null)return;try{
  if(command.equals("walk")||command.equals("dig")){mc.setWindowActive(true);if(mc.screen!=null)mc.setScreen(null);mc.options.keyUp.setDown(command.equals("walk"));mc.options.keyAttack.setDown(command.equals("dig"));if(command.equals("dig"))mc.player.setXRot(90);}
  if(--remaining>0)return;
  mc.options.keyUp.setDown(false);mc.options.keyAttack.setDown(false);
  String title="fixture";try{title=(String)Class.forName("tw.handoff.viewer.ViewerClient",false,mc.getClass().getClassLoader()).getMethod("title").invoke(null);}catch(Exception ignored){}
  String text="PROBE_OK:"+command+":"+title+":pos="+mc.player.position()+":health="+mc.player.getHealth();Files.writeString(output,text);output=null;
 }catch(Exception e){try{Files.writeString(output,"PROBE_FAILED:"+e.getClass().getSimpleName());}catch(Exception ignored){}output=null;}}
}
