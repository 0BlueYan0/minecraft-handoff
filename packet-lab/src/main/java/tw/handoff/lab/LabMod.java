package tw.handoff.lab;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public final class LabMod implements ModInitializer {
 private static final Logger LOG=LoggerFactory.getLogger("packet-lab");
 public void onInitialize(){
  PayloadTypeRegistry.playC2S().register(LabPayload.TYPE,LabPayload.CODEC);PayloadTypeRegistry.playS2C().register(LabPayload.TYPE,LabPayload.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(LabPayload.TYPE,(payload,context)->{
   LoggerFactory.getLogger("packet-lab").info("LAB_BACKEND_RECEIVED_HEX={}",java.util.HexFormat.of().formatHex(payload.data()));
   ServerPlayNetworking.send(context.player(),new LabPayload(payload.data()));
  });
  boolean server=FabricLoader.getInstance().getEnvironmentType()==EnvType.SERVER;
  // A marker file makes a client skip the blessing type, so the host sees that channel as a payload it cannot decode.
  boolean blessing=server||!java.nio.file.Files.exists(FabricLoader.getInstance().getGameDir().resolve("t7-no-blessing-type"));
  if(blessing)PayloadTypeRegistry.playS2C().register(LabPayload.BLESSING,LabPayload.Raw.codec(LabPayload.BLESSING));
  if(!server)return;
  PayloadTypeRegistry.playC2S().register(LabPayload.MUX,LabPayload.Raw.codec(LabPayload.MUX));PayloadTypeRegistry.playS2C().register(LabPayload.MUX,LabPayload.Raw.codec(LabPayload.MUX));
  PayloadTypeRegistry.playS2C().register(LabPayload.QUEST,LabPayload.Raw.codec(LabPayload.QUEST));
  // Declares the mux as something a client may send, so ClientPlayNetworking.canSend is true for it.
  ServerPlayNetworking.registerGlobalReceiver(LabPayload.MUX,(payload,context)->LOG.info("T7_BACKEND_MUX_C2S bytes={}",payload.data().length));
  CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->{
   for(String kind:new String[]{"state","effect"})dispatcher.register(Commands.literal("lab"+kind).requires(source->source.hasPermission(2)).then(Commands.argument("value",IntegerArgumentType.integer(0,100000)).executes(context->{int value=IntegerArgumentType.getInteger(context,"value");for(var player:context.getSource().getServer().getPlayerList().getPlayers())ServerPlayNetworking.send(player,new LabPayload((kind.toUpperCase(java.util.Locale.ROOT)+"|"+value).getBytes(StandardCharsets.UTF_8)));return 1;})));
   LiteralArgumentBuilder<CommandSourceStack> t7=Commands.literal("t7").requires(source->source.hasPermission(2));
   t7.then(Commands.literal("hello").executes(c->send(c,mux(":hello","{\"channels\":[\"saogui:main\",\"capuodev:quest_hud\"]}"))));
   t7.then(Commands.literal("init").executes(c->send(c,gui("INIT","{\"mana\":80,\"maxMana\":100,\"level\":7,\"skills\":{\"0\":\"t7-slash\",\"1\":\"t7-guard\"}}"))));
   t7.then(Commands.literal("mana").then(Commands.argument("n",IntegerArgumentType.integer(0,100000)).executes(c->send(c,gui("MANA_UPDATE","{\"mana\":"+IntegerArgumentType.getInteger(c,"n")+",\"maxMana\":100}")))));
   t7.then(Commands.literal("itemcd").executes(c->send(c,gui("ITEM_SKILL_COOLDOWN","{\"itemType\":\"t7_sword\",\"remainingMs\":60000}"))));
   t7.then(Commands.literal("charge")
    .then(Commands.literal("value").then(Commands.argument("n",IntegerArgumentType.integer(0,100000)).executes(c->send(c,blessing("{\"op\":\"CHARGE_UPDATE\",\"value\":"+IntegerArgumentType.getInteger(c,"n")+"}")))))
    .then(Commands.literal("max").then(Commands.argument("n",IntegerArgumentType.integer(0,100000)).executes(c->send(c,blessing("{\"op\":\"CHARGE_UPDATE\",\"max\":"+IntegerArgumentType.getInteger(c,"n")+"}"))))));
   t7.then(Commands.literal("session").executes(c->send(c,blessing("{\"op\":\"SESSION_START\"}"))));
   t7.then(Commands.literal("blessing").then(Commands.argument("count",IntegerArgumentType.integer(1,1000)).executes(c->{int count=IntegerArgumentType.getInteger(c,"count");for(int i=1;i<=count;i++)send(c,blessing("{\"op\":\"CHARGE_UPDATE\",\"value\":"+i+"}"));return count;})));
   t7.then(Commands.literal("quest")
    .then(Commands.literal("add").then(Commands.argument("id",StringArgumentType.word()).executes(c->send(c,mux("capuodev:quest_hud",questAdd(StringArgumentType.getString(c,"id")))))))
    .then(Commands.literal("remove").then(Commands.argument("id",StringArgumentType.word()).executes(c->send(c,mux("capuodev:quest_hud",questRemove(StringArgumentType.getString(c,"id")))))))
    .then(Commands.literal("clear").executes(c->send(c,mux("capuodev:quest_hud",questRemove("*"))))));
   dispatcher.register(t7);
  });
 }
 private static int send(CommandContext<CommandSourceStack> context,LabPayload.Raw payload){
  int sent=0;for(var player:context.getSource().getServer().getPlayerList().getPlayers()){ServerPlayNetworking.send(player,payload);sent++;}
  LOG.info("T7_BACKEND_SENT channel={} bytes={} players={}",payload.type().id(),payload.data().length,sent);return sent;
 }
 private static LabPayload.Raw blessing(String json){return new LabPayload.Raw(LabPayload.BLESSING,json.getBytes(StandardCharsets.UTF_8));}
 private static LabPayload.Raw gui(String op,String data){return mux("saogui:main",("{\"op\":\""+op+"\",\"data\":"+data+"}").getBytes(StandardCharsets.UTF_8));}
 private static LabPayload.Raw mux(String sub,String json){return mux(sub,json.getBytes(StandardCharsets.UTF_8));}
 /** saocore:mux wire form: one byte of sub-channel name length, the name, then the content. */
 private static LabPayload.Raw mux(String sub,byte[] content){
  byte[] name=sub.getBytes(StandardCharsets.UTF_8);byte[] all=new byte[1+name.length+content.length];
  all[0]=(byte)name.length;System.arraycopy(name,0,all,1,name.length);System.arraycopy(content,0,all,1+name.length,content.length);
  return new LabPayload.Raw(LabPayload.MUX,all);
 }
 private static byte[] questRemove(String id){
  var buffer=new FriendlyByteBuf(Unpooled.buffer());try{buffer.writeByte(1);buffer.writeUtf(id);return bytes(buffer);}finally{buffer.release();}
 }
 /** Field order follows SAOUI 2.51.14 QuestHudPayload.write; the values are invented. */
 private static byte[] questAdd(String id){
  var b=new FriendlyByteBuf(Unpooled.buffer());
  try{
   b.writeByte(0);b.writeUtf(id);b.writeUtf("TEXT");b.writeUtf("TOP_LEFT");b.writeInt(10);b.writeInt(10);b.writeFloat(1.0F);
   b.writeBoolean(true);b.writeInt(1920);b.writeInt(1080);b.writeBoolean(true);b.writeUtf("LEFT");
   for(int v:new int[]{2,0,4,4,0,0,0,0})b.writeInt(v);
   b.writeUtf("COLOR");b.writeInt(1711276032);b.writeUtf("");for(int v:new int[]{256,256,0,0})b.writeInt(v);
   b.writeBoolean(true);b.writeBoolean(false);for(int v:new int[]{8,8,8,8})b.writeInt(v);
   b.writeBoolean(true);b.writeInt(1920);b.writeInt(1080);b.writeInt(-1);b.writeFloat(1.0F);b.writeInt(-1);b.writeInt(0);
   b.writeUtf("");for(int v:new int[]{256,256,0,0})b.writeInt(v);
   b.writeBoolean(true);b.writeBoolean(false);for(int v:new int[]{8,8,8,8})b.writeInt(v);
   b.writeBoolean(true);b.writeInt(1920);b.writeInt(1080);b.writeInt(-1);b.writeFloat(1.0F);
   for(int i=0;i<4;i++){b.writeUtf("NONE");b.writeUtf("AUTO");b.writeInt(0);b.writeInt(0);b.writeFloat(1.0F);b.writeFloat(1.0F);}
   for(String text:new String[]{"t7 quest","t7 objective","","","","",""})b.writeUtf(text);
   b.writeFloat(1.0F);b.writeFloat(1.0F);b.writeFloat(0.0F);b.writeUtf("RELATIVE");b.writeUtf("");b.writeUtf("");
   return bytes(b);
  }finally{b.release();}
 }
 private static byte[] bytes(FriendlyByteBuf buffer){byte[] out=new byte[buffer.readableBytes()];buffer.readBytes(out);return out;}
}
