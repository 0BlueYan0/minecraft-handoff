package tw.handoff.lab;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import java.nio.charset.StandardCharsets;
public final class LabClient implements ClientModInitializer {
 private static int state,echoes,localOnly;private static long effectUntil;private static String hex="";
 public void onInitializeClient(){ClientPlayNetworking.registerGlobalReceiver(LabPayload.TYPE,(payload,context)->{
  String text=new String(payload.data(),StandardCharsets.UTF_8);
  if(text.startsWith("STATE|"))state=Integer.parseInt(text.substring(6));
  else if(text.startsWith("EFFECT|"))effectUntil=System.currentTimeMillis()+Long.parseLong(text.substring(7));
  else{echoes++;hex=java.util.HexFormat.of().formatHex(payload.data());}
 });}
 public static void probe(){ClientPlayNetworking.send(new LabPayload(new byte[]{0,1,-1,0,127,-128,42}));}
 public static void localChange(){localOnly=5;}
 public static String status(){return "state="+state+",effectActive="+(effectUntil>System.currentTimeMillis())+",echoes="+echoes+",hex="+hex+",localOnly="+localOnly+",swinging="+(Minecraft.getInstance().player!=null&&Minecraft.getInstance().player.swinging);}
}
