package tw.handoff.relay.bridge;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.lwjgl.glfw.*;
import tw.handoff.relay.core.ControlFrame;

/** Only the original client simulates movement and emits real server gameplay packets. */
final class RemoteDriver {
 private final Minecraft mc;private final Runnable emergency;
 private ClientInput original;private final VirtualInput virtual=new VirtualInput();
 private boolean active,oldPause,oldGrabbed;private final Field grabbed;
 private GLFWKeyCallback keyboard;private GLFWMouseButtonCallback buttons;private GLFWCursorPosCallback cursor;private GLFWScrollCallback scroll;
 private ControlFrame frame;private int prior;
 RemoteDriver(Minecraft mc,Runnable emergency)throws Exception{
  this.mc=mc;this.emergency=emergency;
  Field g=null;for(String alias:new String[]{"mouseGrabbed","field_1783"})try{g=mc.mouseHandler.getClass().getDeclaredField(alias);break;}catch(NoSuchFieldException ignored){}
  if(g==null)throw new IllegalStateException("Mouse state contract unavailable");grabbed=g;grabbed.setAccessible(true);
  long window=mc.getWindow().getWindow();
  keyboard=GLFW.glfwSetKeyCallback(window,(w,k,s,a,m)->{if(active){if(k==GLFW.GLFW_KEY_F8&&a==GLFW.GLFW_PRESS)mc.execute(emergency);}else if(keyboard!=null)keyboard.invoke(w,k,s,a,m);});
  buttons=GLFW.glfwSetMouseButtonCallback(window,(w,b,a,m)->{if(!active&&buttons!=null)buttons.invoke(w,b,a,m);});
  cursor=GLFW.glfwSetCursorPosCallback(window,(w,x,y)->{if(!active&&cursor!=null)cursor.invoke(w,x,y);});
  scroll=GLFW.glfwSetScrollCallback(window,(w,x,y)->{if(!active&&scroll!=null)scroll.invoke(w,x,y);});
 }
 void begin(){if(active)return;original=mc.player.input;oldPause=mc.options.pauseOnLostFocus;oldGrabbed=mc.mouseHandler.isMouseGrabbed();active=true;mc.options.pauseOnLostFocus=false;KeyMapping.releaseAll();}
 void frame(ControlFrame value){frame=value;}
 void tick(){if(!active||mc.player==null)return;
  ControlFrame f=frame;virtual.frame=f;if(mc.player.input!=virtual){original=mc.player.input;mc.player.input=virtual;}
  mc.setWindowActive(true);try{grabbed.setBoolean(mc.mouseHandler,true);}catch(IllegalAccessException failure){throw new IllegalStateException(failure);}
  mc.getFramerateLimitTracker().onInputReceived();
  int keys=f==null||f.gui()?0:f.keys();
  press(mc.options.keyAttack,(keys&ControlFrame.ATTACK)!=0,(prior&ControlFrame.ATTACK)!=0);
  press(mc.options.keyUse,(keys&ControlFrame.USE)!=0,(prior&ControlFrame.USE)!=0);
  mc.options.keySprint.setDown((keys&ControlFrame.SPRINT)!=0);
  if(f!=null){mc.player.setYRot(f.yaw());mc.player.setXRot(f.pitch());mc.player.getInventory().selected=f.slot();}
  prior=keys;
 }
 private void press(KeyMapping key,boolean down,boolean before){key.setDown(down);if(down&&!before)KeyMapping.click(KeyBindingHelper.getBoundKeyOf(key));}
 void clear(){frame=null;virtual.frame=null;prior=0;mc.options.keyAttack.setDown(false);mc.options.keyUse.setDown(false);mc.options.keySprint.setDown(false);}
 void end(){if(!active)return;clear();active=false;if(mc.player!=null&&original!=null)mc.player.input=original;mc.options.pauseOnLostFocus=oldPause;
  try{grabbed.setBoolean(mc.mouseHandler,oldGrabbed);}catch(IllegalAccessException ignored){}
  mc.setWindowActive(GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE);KeyMapping.releaseAll();
 }
 private static final class VirtualInput extends ClientInput {
  ControlFrame frame;
  public void tick(){ControlFrame f=frame;if(f==null){keyPresses=Input.EMPTY;forwardImpulse=leftImpulse=0;return;}
   keyPresses=new Input(f.down(ControlFrame.FORWARD),f.down(ControlFrame.BACKWARD),f.down(ControlFrame.LEFT),f.down(ControlFrame.RIGHT),f.down(ControlFrame.JUMP),f.down(ControlFrame.SNEAK),f.down(ControlFrame.SPRINT));
   forwardImpulse=(keyPresses.forward()?1:0)-(keyPresses.backward()?1:0);leftImpulse=(keyPresses.left()?1:0)-(keyPresses.right()?1:0);
  }
 }
}
