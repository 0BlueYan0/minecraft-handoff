package tw.handoff.relay.core;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * SAOUI 的翻譯表請求（saocore:mux 子通道 saocore:translate）。
 * SAOUI 在 JOIN 後 10 秒內重試 3 次 SYNC，那時 viewer 通常還沒接管，請求被吞掉就會停在伺服器預設的法文。
 * 這幾個 op 只向伺服器要文字，不動角色，所以 viewer 觀察中也放行。
 */
public final class SaoTranslateRequest {
 public static final String CHANNEL="saocore:mux",SUB_CHANNEL="saocore:translate";
 public static final int MAX_BYTES=16000;
 private static final Set<String> OPS=Set.of("SYNC","NAMES","MISSING");

 /** data 是 custom payload 在通道名稱之後的內容：1 byte 長度、子通道名稱、JSON {"op":...,"data":...}。 */
 public static boolean allows(String channel,byte[] data){
  if(!CHANNEL.equals(channel)||data==null||data.length<2||data.length>MAX_BYTES)return false;
  int n=data[0]&255;
  if(n==0||n>data.length-1)return false;
  if(!SUB_CHANNEL.equals(new String(data,1,n,StandardCharsets.UTF_8)))return false;
  try{
   JsonElement root=JsonParser.parseString(new String(data,1+n,data.length-1-n,StandardCharsets.UTF_8));
   if(!root.isJsonObject())return false;
   JsonElement op=root.getAsJsonObject().get("op");
   return op!=null&&op.isJsonPrimitive()&&op.getAsJsonPrimitive().isString()&&OPS.contains(op.getAsString());
  }catch(RuntimeException malformed){return false;}
 }

 /** 整個 serverbound frame（packet id、通道、內容）。 */
 public static boolean allowsFrame(byte[] frame){
  if(frame==null||frame.length>MAX_BYTES)return false;
  OpaqueFrames.View view;
  try{view=OpaqueFrames.inspect(frame);}catch(RuntimeException malformed){return false;}
  byte[] data=java.util.Arrays.copyOfRange(frame,view.dataOffset(),frame.length);
  return allows(view.channel(),data);
 }
 private SaoTranslateRequest(){}
}
