package tw.handoff.lab;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.nio.charset.StandardCharsets;
import org.slf4j.LoggerFactory;
public final class LabMod implements ModInitializer {
 public void onInitialize(){
  PayloadTypeRegistry.playC2S().register(LabPayload.TYPE,LabPayload.CODEC);PayloadTypeRegistry.playS2C().register(LabPayload.TYPE,LabPayload.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(LabPayload.TYPE,(payload,context)->{
   LoggerFactory.getLogger("packet-lab").info("LAB_BACKEND_RECEIVED_HEX={}",java.util.HexFormat.of().formatHex(payload.data()));
   ServerPlayNetworking.send(context.player(),new LabPayload(payload.data()));
  });
  CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment)->{
   for(String kind:new String[]{"state","effect"})dispatcher.register(Commands.literal("lab"+kind).requires(source->source.hasPermission(2)).then(Commands.argument("value",IntegerArgumentType.integer(0,100000)).executes(context->{int value=IntegerArgumentType.getInteger(context,"value");for(var player:context.getSource().getServer().getPlayerList().getPlayers())ServerPlayNetworking.send(player,new LabPayload((kind.toUpperCase(java.util.Locale.ROOT)+"|"+value).getBytes(StandardCharsets.UTF_8)));return 1;})));
  });
 }
}
