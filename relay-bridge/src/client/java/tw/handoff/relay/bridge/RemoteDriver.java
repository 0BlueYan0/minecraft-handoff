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
  window=mc.getWindow().getWindow();
  // The callbacks are kept so dispose can tell whether they are still the ones GLFW calls. Once disposed they only pass events on to the callbacks they replaced.
  keyboardOurs=GLFWKeyCallback.create((w,k,s,a,m)->{if(!disposed&&active){if(k==GLFW.GLFW_KEY_F8&&a==GLFW.GLFW_PRESS)mc.execute(emergency);}else{if(!disposed)human();if(keyboard!=null)keyboard.invoke(w,k,s,a,m);}});
  keyboard=GLFW.glfwSetKeyCallback(window,keyboardOurs);
  buttonsOurs=GLFWMouseButtonCallback.create((w,b,a,m)->{if(disposed||active){if(disposed&&buttons!=null)buttons.invoke(w,b,a,m);}else{human();if(buttons!=null)buttons.invoke(w,b,a,m);}});
  buttons=GLFW.glfwSetMouseButtonCallback(window,buttonsOurs);
  cursorOurs=GLFWCursorPosCallback.create((w,x,y)->{if(disposed||active){if(disposed&&cursor!=null)cursor.invoke(w,x,y);}else{human();if(cursor!=null)cursor.invoke(w,x,y);}});
  cursor=GLFW.glfwSetCursorPosCallback(window,cursorOurs);
  scrollOurs=GLFWScrollCallback.create((w,x,y)->{if(disposed||active){if(disposed&&scroll!=null)scroll.invoke(w,x,y);}else{human();if(scroll!=null)scroll.invoke(w,x,y);}});
  scroll=GLFW.glfwSetScrollCallback(window,scrollOurs);
 }
 private final long window;private volatile boolean disposed;
 private final GLFWKeyCallback keyboardOurs;private final GLFWMouseButtonCallback buttonsOurs;private final GLFWCursorPosCallback cursorOurs;private final GLFWScrollCallback scrollOurs;
 /**
  * Gives control back and takes this driver out of the window's callback chain (game thread). A callback that something else was chained after cannot be unlinked without dropping that one,
  * so it stays, passes events on, and is not freed. Without this a replaced bridge keeps swallowing keyboard, mouse and scroll events while a viewer drives.
  */
 void dispose(){
  if(disposed)return;
  end();disposed=true;humanInput=null;
  GLFWKeyCallback k=GLFW.glfwSetKeyCallback(window,keyboard);if(k!=null&&k.address()==keyboardOurs.address())keyboardOurs.free();else GLFW.glfwSetKeyCallback(window,k);
  GLFWMouseButtonCallback b=GLFW.glfwSetMouseButtonCallback(window,buttons);if(b!=null&&b.address()==buttonsOurs.address())buttonsOurs.free();else GLFW.glfwSetMouseButtonCallback(window,b);
  GLFWCursorPosCallback c=GLFW.glfwSetCursorPosCallback(window,cursor);if(c!=null&&c.address()==cursorOurs.address())cursorOurs.free();else GLFW.glfwSetCursorPosCallback(window,c);
  GLFWScrollCallback s=GLFW.glfwSetScrollCallback(window,scroll);if(s!=null&&s.address()==scrollOurs.address())scrollOurs.free();else GLFW.glfwSetScrollCallback(window,s);
 }
 /** Receives the time of each event the host window gets while no viewer drives it (GLFW thread). Generic mode never sets active, so every event counts there. */
 private volatile Runnable humanInput;
 void onHumanInput(Runnable listener){humanInput=listener;}
 private void human(){Runnable listener=humanInput;if(listener!=null)listener.run();}
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
