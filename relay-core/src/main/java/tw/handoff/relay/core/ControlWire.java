package tw.handoff.relay.core;
public final class ControlWire {
 public static final String PREFIX="HANDOFF2:";
 public record Action(long epoch,long sequence,String data){public String encode(){return PREFIX+"A:"+epoch+":"+sequence+":"+data;}}
 public static Action action(long epoch,long sequence,String data){
  if(epoch<=0||sequence<=0||data==null||data.length()>22000||!data.matches("[A-Za-z0-9+/]*={0,2}"))throw new IllegalArgumentException("Invalid action envelope");
  return new Action(epoch,sequence,data);
 }
 public static Action parseAction(String s){try{if(s==null||s.length()>22100||!s.startsWith(PREFIX+"A:"))throw new IllegalArgumentException();String[] p=s.substring((PREFIX+"A:").length()).split(":",-1);if(p.length!=3)throw new IllegalArgumentException();return action(Long.parseLong(p[0]),Long.parseLong(p[1]),p[2]);}catch(RuntimeException bad){throw new IllegalArgumentException("Invalid action envelope");}}
 public static final int MAX_CHUNK_CHARS=24000,MAX_CHUNKS=200;
 public record Chunk(int index,int total,String data){public String encode(){return PREFIX+"S:"+index+":"+total+":"+data;}}
 private static boolean validToken(String s,String pattern){return s!=null&&s.matches(pattern);}
 private static String body(String s,String tag,int max){if(s==null||s.length()>max||!s.startsWith(PREFIX+tag))throw new IllegalArgumentException("Invalid control envelope");return s.substring((PREFIX+tag).length());}
 public static String ping(String nonce){if(!validToken(nonce,"[0-9a-f]{1,64}"))throw new IllegalArgumentException("Invalid control envelope");return PREFIX+"Q:"+nonce;}
 public static String parsePing(String s){String n=body(s,"Q:",100);if(!validToken(n,"[0-9a-f]{1,64}"))throw new IllegalArgumentException("Invalid control envelope");return n;}
 public static String pong(String nonce){ping(nonce);return PREFIX+"q:"+nonce;}
 public static String parsePong(String s){String n=body(s,"q:",100);if(!validToken(n,"[0-9a-f]{1,64}"))throw new IllegalArgumentException("Invalid control envelope");return n;}
 public static Chunk chunk(int index,int total,String data){
  if(index<0||total<1||total>MAX_CHUNKS||index>=total||data==null||data.length()>MAX_CHUNK_CHARS||!data.matches("[A-Za-z0-9+/]*={0,2}"))throw new IllegalArgumentException("Invalid control envelope");
  return new Chunk(index,total,data);
 }
 public static Chunk parseChunk(String s){try{String[] p=body(s,"S:",MAX_CHUNK_CHARS+100).split(":",-1);if(p.length!=3)throw new IllegalArgumentException();return chunk(Integer.parseInt(p[0]),Integer.parseInt(p[1]),p[2]);}catch(RuntimeException bad){throw new IllegalArgumentException("Invalid control envelope");}}
 public static String missing(String piece){return pieceMessage("M:",piece);}
 public static String parseMissing(String s){return piece(body(s,"M:",100));}
 public static String failed(String piece){return pieceMessage("N:",piece);}
 public static String parseFailed(String s){return piece(body(s,"N:",100));}
 /** viewer 觀察中送的 SAOUI 翻譯表請求，內容是 base64 的 serverbound frame，不帶 epoch，常駐端另外用 SaoTranslateRequest 檢查。 */
 public static String language(String data){
  if(data==null||data.isEmpty()||data.length()>22000||!data.matches("[A-Za-z0-9+/]*={0,2}"))throw new IllegalArgumentException("Invalid control envelope");
  return PREFIX+"L:"+data;
 }
 public static String parseLanguage(String s){return language(body(s,"L:",22100)).substring((PREFIX+"L:").length());}
 public static String ack(){return PREFIX+"K";}
 public static boolean isAck(String s){return (PREFIX+"K").equals(s);}
 private static String pieceMessage(String tag,String piece){return PREFIX+tag+piece(piece);}
 private static String piece(String p){if(!validToken(p,"[a-z-]{1,32}"))throw new IllegalArgumentException("Invalid control envelope");return p;}
 private ControlWire(){}
}
