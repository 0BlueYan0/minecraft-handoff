package tw.handoff.relay.core;
public record ControlFrame(long epoch,long sequence,int keys,float yaw,float pitch,int slot,boolean gui){
 public static final int FORWARD=1,BACKWARD=2,LEFT=4,RIGHT=8,JUMP=16,SNEAK=32,SPRINT=64,ATTACK=128,USE=256;
 public ControlFrame{if(epoch<=0||sequence<=0||(keys&~511)!=0||!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90||Math.abs(yaw)>1e7||slot<0||slot>8)throw new IllegalArgumentException("Invalid input frame");}
 public boolean down(int flag){return !gui&&(keys&flag)!=0;}
 public String encode(){return ControlWire.PREFIX+"I:"+epoch+":"+sequence+":"+keys+":"+yaw+":"+pitch+":"+slot+":"+(gui?1:0);}
 public static ControlFrame decode(String value){try{
  if(value==null||value.length()>200||!value.startsWith(ControlWire.PREFIX+"I:"))throw new IllegalArgumentException();
  String[] p=value.substring((ControlWire.PREFIX+"I:").length()).split(":",-1);if(p.length!=7||!(p[6].equals("0")||p[6].equals("1")))throw new IllegalArgumentException();
  return new ControlFrame(Long.parseLong(p[0]),Long.parseLong(p[1]),Integer.parseInt(p[2]),Float.parseFloat(p[3]),Float.parseFloat(p[4]),Integer.parseInt(p[5]),p[6].equals("1"));
 }catch(RuntimeException bad){throw new IllegalArgumentException("Invalid input frame");}}
}
