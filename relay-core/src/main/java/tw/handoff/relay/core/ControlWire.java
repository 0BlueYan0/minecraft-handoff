package tw.handoff.relay.core;
public final class ControlWire {
 public static final String PREFIX="HANDOFF2:";
 public record Action(long epoch,long sequence,String data){public String encode(){return PREFIX+"A:"+epoch+":"+sequence+":"+data;}}
 public static Action action(long epoch,long sequence,String data){
  if(epoch<=0||sequence<=0||data==null||data.length()>22000||!data.matches("[A-Za-z0-9+/]*={0,2}"))throw new IllegalArgumentException("Invalid action envelope");
  return new Action(epoch,sequence,data);
 }
 public static Action parseAction(String s){try{if(s==null||s.length()>22100||!s.startsWith(PREFIX+"A:"))throw new IllegalArgumentException();String[] p=s.substring((PREFIX+"A:").length()).split(":",-1);if(p.length!=3)throw new IllegalArgumentException();return action(Long.parseLong(p[0]),Long.parseLong(p[1]),p[2]);}catch(RuntimeException bad){throw new IllegalArgumentException("Invalid action envelope");}}
 private ControlWire(){}
}
