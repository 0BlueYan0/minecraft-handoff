package tw.handoff.lab;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.Consumer;
public final class LabClient implements ClientModInitializer {
 private static int state,echoes,localOnly;private static long effectUntil;private static String hex="";
 // Fake saoblessing frames: CHARGE_UPDATE changes only the fields it carries, SESSION_START and SESSION_END clear them.
 private static volatile int chargeValue=-1,chargeMax=-1,blessingFrames;private static volatile String lastTitle="";private static volatile boolean joinSeen;
 private static Path gameDir(){return FabricLoader.getInstance().getGameDir();}
 /** Timestamped lines go to t7-events.log in the game directory so a run can be read back afterwards. */
 static void log(String text){
  try{Files.writeString(gameDir().resolve("t7-events.log"),System.currentTimeMillis()+" "+text+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception ignored){}
 }
 public void onInitializeClient(){
  ClientPlayNetworking.registerGlobalReceiver(LabPayload.TYPE,(payload,context)->{
   String text=new String(payload.data(),StandardCharsets.UTF_8);
   if(text.startsWith("STATE|"))state=Integer.parseInt(text.substring(6));
   else if(text.startsWith("EFFECT|"))effectUntil=System.currentTimeMillis()+Long.parseLong(text.substring(7));
   else{echoes++;hex=java.util.HexFormat.of().formatHex(payload.data());}
  });
  // saoblessing is not loaded in the fixtures. SAOUI owns saocore:mux and capuodev:quest_hud, so the probe reads those through reflection instead.
  if(!Files.exists(gameDir().resolve("t7-no-blessing-type")))ClientPlayNetworking.registerGlobalReceiver(LabPayload.BLESSING,(payload,context)->blessing(payload.data()));
  ClientLifecycleEvents.CLIENT_STARTED.register(client->wrapOverlay());
  ClientPlayConnectionEvents.INIT.register((handler,client)->{joinSeen=false;hostActive=false;synchronized(DEFERRED){DEFERRED.clear();}watchWire(handler.getConnection());});
  ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->{joinSeen=true;log("JOIN_HANDLED");});
  ClientTickEvents.END_CLIENT_TICK.register(client->{String title=title();if(!title.equals(lastTitle)){lastTitle=title;log("TITLE "+title);}});
 }
 private static void blessing(byte[] data){
  JsonObject o=JsonParser.parseString(new String(data,StandardCharsets.UTF_8)).getAsJsonObject();
  String op=o.get("op").getAsString();blessingFrames++;
  if(op.equals("SESSION_START")||op.equals("SESSION_END")){chargeValue=-1;chargeMax=-1;}
  else if(op.equals("CHARGE_UPDATE")){if(o.has("value"))chargeValue=o.get("value").getAsInt();if(o.has("max"))chargeMax=o.get("max").getAsInt();}
  // The flag file is consumed by the first blessing frame this client handles, which for a joining viewer is during the replay.
  try{if(Files.deleteIfExists(gameDir().resolve("t7-stall-take.flag")))stallThenTake();}catch(Exception ignored){}
 }
 /** Holds the game thread so the join snapshot cannot be applied (and acknowledged) yet, and sends the F6 request from another thread meanwhile. */
 private static void stallThenTake(){
  log("STALL_BEGIN");
  Thread helper=new Thread(()->{
   try{Thread.sleep(1000);}catch(InterruptedException stop){return;}
   var connection=Minecraft.getInstance().getConnection();
   if(connection!=null){connection.send(new ServerboundCustomPayloadPacket(new BrandPayload("HANDOFF2:TAKE")));log("TAKE_SENT_BEFORE_ACK");}
   try{Thread.sleep(1500);}catch(InterruptedException stop){return;}
   log("TITLE_DURING_STALL "+title());
  },"t7-take");
  helper.setDaemon(true);helper.start();
  try{Thread.sleep(4000);}catch(InterruptedException stop){Thread.currentThread().interrupt();}
  log("STALL_END");
 }
 /** Records every overlay text the viewer's join state is asked to show, before it is handed to the game. */
 @SuppressWarnings("unchecked") private static void wrapOverlay(){
  try{
   Class<?> viewer=Class.forName("tw.handoff.viewer.ViewerClient",true,LabClient.class.getClassLoader());
   Field joinField=viewer.getDeclaredField("JOIN_STATE");joinField.setAccessible(true);Object join=joinField.get(null);
   Field overlay=join.getClass().getDeclaredField("overlay");overlay.setAccessible(true);
   Consumer<String> original=(Consumer<String>)overlay.get(join);
   overlay.set(join,(Consumer<String>)text->{log("OVERLAY "+text);original.accept(text);});
  }catch(ReflectiveOperationException|RuntimeException absent){log("OVERLAY_WRAP_UNAVAILABLE "+absent.getClass().getSimpleName());}
 }
 /** Logs the join handshake's control messages (never their payload bytes) and the channel of every other custom payload, in both directions, with timestamps. */
 private static void watchWire(net.minecraft.network.Connection connection){
  try{
   io.netty.channel.Channel channel=null;
   for(Field field:net.minecraft.network.Connection.class.getDeclaredFields())if(io.netty.channel.Channel.class.isAssignableFrom(field.getType())){field.setAccessible(true);channel=(io.netty.channel.Channel)field.get(connection);break;}
   if(channel==null||channel.pipeline().get("t7_wire")!=null)return;
   channel.pipeline().addBefore("packet_handler","t7_wire",new io.netty.channel.ChannelDuplexHandler(){
    public void channelRead(io.netty.channel.ChannelHandlerContext context,Object message)throws Exception{
     holdControlUntilJoin(message);wire("RX",message);context.fireChannelRead(message);
     if(message instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet&&packet.payload() instanceof BrandPayload brand&&brand.brand().startsWith("HANDOFF2:R:"))releaseReplies(context);
    }
    public void write(io.netty.channel.ChannelHandlerContext context,Object message,io.netty.channel.ChannelPromise promise)throws Exception{
     if(deferReply(context,message,promise))return;
     wire("TX",message);context.write(message,promise);
    }
   });
  }catch(ReflectiveOperationException|RuntimeException failure){log("WIRE_LOG_UNAVAILABLE "+failure.getClass().getSimpleName());}
 }
 /**
  * Fixture-only timing aid, on when t7-delay-control.flag exists in the game directory. A control message that reaches the connection thread
  * before the game thread has handled the login packet finds no player, so the viewer cannot reply (Minecraft.getConnection() is null) and its
  * join state is not marked joined yet. This holds such messages until the join has been handled. It hides that race, so a run with it on
  * does not show the viewer-mod code passing on its own.
  */
 private static void holdControlUntilJoin(Object message)throws InterruptedException{
  if(!(message instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet)||!(packet.payload() instanceof BrandPayload brand)||!brand.brand().startsWith("HANDOFF2:"))return;
  if(!Files.exists(gameDir().resolve("t7-delay-control.flag")))return;
  long start=System.nanoTime();
  while((Minecraft.getInstance().player==null||!joinSeen)&&System.nanoTime()-start<10_000_000_000L)Thread.sleep(5);
  Thread.sleep(50);
  long waited=(System.nanoTime()-start)/1_000_000;
  if(waited>60)log("CONTROL_HELD_MS "+waited+" "+brand.brand().substring(0,Math.min(12,brand.brand().length())));
 }
 private record Deferred(Object message,io.netty.channel.ChannelPromise promise){}
 private static final java.util.List<Deferred> DEFERRED=new java.util.ArrayList<>();
 private static volatile boolean hostActive;
 /**
  * Fixture-only, on when t7-defer-replies.flag exists. The host ignores control messages from a viewer it has not yet marked active
  * (world snapshot sent and player_loaded received), so a pong or ACK sent earlier is dropped without an answer. This keeps the viewer's
  * join replies until the host's first R: message, which the host sends once it is active. With t7-take-before-ack.flag as well, one
  * TAKE is sent between the pong and the ACK.
  */
 private static boolean deferReply(io.netty.channel.ChannelHandlerContext context,Object message,io.netty.channel.ChannelPromise promise){
  if(hostActive||!(message instanceof ServerboundCustomPayloadPacket packet)||!(packet.payload() instanceof BrandPayload brand))return false;
  String text=brand.brand();
  if(!(text.startsWith("HANDOFF2:q:")||text.equals("HANDOFF2:K")||text.startsWith("HANDOFF2:N:")))return false;
  if(!Files.exists(gameDir().resolve("t7-defer-replies.flag")))return false;
  synchronized(DEFERRED){DEFERRED.add(new Deferred(message,promise));}
  log("REPLY_DEFERRED "+text.substring(0,Math.min(14,text.length())));
  return true;
 }
 private static void releaseReplies(io.netty.channel.ChannelHandlerContext context){
  if(hostActive)return;
  hostActive=true;
  java.util.List<Deferred> pending;synchronized(DEFERRED){pending=new java.util.ArrayList<>(DEFERRED);DEFERRED.clear();}
  boolean takeFirst=Files.exists(gameDir().resolve("t7-take-before-ack.flag"));
  for(Deferred item:pending){
   boolean ack=item.message() instanceof ServerboundCustomPayloadPacket packet&&packet.payload() instanceof BrandPayload brand&&brand.brand().equals("HANDOFF2:K");
   if(ack&&takeFirst){context.write(new ServerboundCustomPayloadPacket(new BrandPayload("HANDOFF2:TAKE")),context.newPromise());log("TX control HANDOFF2:TAKE");log("TAKE_SENT_BEFORE_ACK");}
   wire("TX",item.message());context.write(item.message(),item.promise());
  }
  context.flush();log("REPLIES_RELEASED "+pending.size());
 }
 private static void wire(String direction,Object message){
  net.minecraft.network.protocol.common.custom.CustomPacketPayload payload=null;
  if(message instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket packet)payload=packet.payload();
  else if(message instanceof ServerboundCustomPayloadPacket packet)payload=packet.payload();
  if(payload==null)return;
  if(payload instanceof BrandPayload brand&&brand.brand().startsWith("HANDOFF2:")){String text=brand.brand();log(direction+" control "+(text.startsWith("HANDOFF2:S:")?text.substring(0,Math.min(text.length(),20))+"...":text.substring(0,Math.min(text.length(),60))));}
  else log(direction+" payload "+payload.type().id());
 }
 static String title(){
  try{return (String)Class.forName("tw.handoff.viewer.ViewerClient",false,LabClient.class.getClassLoader()).getMethod("title").invoke(null);}catch(Exception absent){return "none";}
 }
 public static void probe(){ClientPlayNetworking.send(new LabPayload(new byte[]{0,1,-1,0,127,-128,42}));}
 public static void localChange(){localOnly=5;}
 public static String status(){return "state="+state+",effectActive="+(effectUntil>System.currentTimeMillis())+",echoes="+echoes+",hex="+hex+",localOnly="+localOnly+",swinging="+(Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.swinging)+",blessingFrames="+blessingFrames+",chargeValue="+chargeValue+",chargeMax="+chargeMax;}
}
