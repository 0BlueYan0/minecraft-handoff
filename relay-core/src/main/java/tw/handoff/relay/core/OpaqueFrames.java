package tw.handoff.relay.core;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
public final class OpaqueFrames {
 public record View(int packetId,String channel,int dataOffset){}
 public static int readVarInt(byte[] bytes,int[] offset){int value=0;for(int i=0;i<5;i++){if(offset[0]>=bytes.length)throw new IllegalArgumentException("Truncated frame");int b=bytes[offset[0]++]&255;if(i==4&&(b&240)!=0)throw new IllegalArgumentException("Invalid VarInt");value|=(b&127)<<(7*i);if((b&128)==0)return value;}throw new IllegalArgumentException("Invalid VarInt");}
 private static void write(ByteArrayOutputStream out,int n){do{int b=n&127;n>>>=7;out.write(n==0?b:b|128);}while(n!=0);}
 public static View inspect(byte[] bytes){int[] p={0};int id=readVarInt(bytes,p),len=readVarInt(bytes,p);if(id<0||len<1||len>32767||len>bytes.length-p[0])throw new IllegalArgumentException("Invalid payload frame");String channel=new String(bytes,p[0],len,StandardCharsets.UTF_8);if(!channel.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))throw new IllegalArgumentException("Invalid channel");return new View(id,channel,p[0]+len);}
 public static byte[] payload(int packetId,String channel,byte[] data){var out=new ByteArrayOutputStream();write(out,packetId);byte[] name=channel.getBytes(StandardCharsets.UTF_8);write(out,name.length);out.writeBytes(name);out.writeBytes(data);return out.toByteArray();}
 public static boolean gameplayChannel(String channel){String ns=channel.split(":",2)[0];return !java.util.Set.of("launcher","handoff","fabric","fabric-networking-api-v1","c","fml","forge").contains(ns)&&!channel.equals("minecraft:brand")&&!channel.startsWith("minecraft:register")&&!channel.startsWith("minecraft:unregister");}
 private OpaqueFrames(){}
}
