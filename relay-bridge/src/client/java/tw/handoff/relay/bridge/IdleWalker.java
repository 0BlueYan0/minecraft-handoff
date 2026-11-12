package tw.handoff.relay.bridge;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies {@link IdleWalkPlan} to the original client. Like RemoteDriver it swaps LocalPlayer.input for its own instance and puts the original back,
 * so no key mapping, GLFW or operating system event is involved and no screen is closed. Runs on the client thread only, except humanInput.
 */
final class IdleWalker {
 private static final Logger LOG=LoggerFactory.getLogger("minecraft-handoff-relay");
 private final Minecraft mc;private final IdleWalkPlan plan;private final BooleanSupplier remoteOwns;
 private final WalkInput input=new WalkInput();
 private LocalPlayer player;private ClientInput original;
 IdleWalker(Minecraft mc,IdleWalkPlan plan,BooleanSupplier remoteOwns){this.mc=mc;this.plan=plan;this.remoteOwns=remoteOwns;}
 /** GLFW thread. */
 void humanInput(){plan.noteHumanInput();}
 void tick(){
  var decision=plan.tick(blocked());
  switch(decision.event()){
   case STARTED->{begin();LOG.info("Idle walk started");}
   case FINISHED->{restore();LOG.info("Idle walk finished");}
   case INTERRUPTED->{restore();LOG.info("Idle walk interrupted ({})",decision.reason());}
   case SKIPPED->LOG.debug("Idle walk skipped ({})",decision.reason());
   default->{}
  }
  if(player!=null)input.set(decision.direction());
 }
 /** Must run before RemoteDriver.begin records mc.player.input as the original one. */
 void abort(String reason){if(plan.abort()){restore();LOG.info("Idle walk interrupted ({})",reason);}}
 private String blocked(){
  if(mc.player==null||mc.level==null||mc.getConnection()==null)return "not in a world";
  if(player!=null&&mc.player!=player)return "player replaced";
  if(mc.player.isDeadOrDying())return "player dead";
  var screen=mc.screen;
  if(screen instanceof ReceivingLevelScreen||screen instanceof LevelLoadingScreen||screen instanceof ConnectScreen||screen instanceof ProgressScreen||screen instanceof GenericMessageScreen||screen instanceof DisconnectedScreen)return "loading screen";
  if(remoteOwns.getAsBoolean())return "viewer has control";
  return null;
 }
 private void begin(){player=mc.player;original=player.input;player.input=input;}
 private void restore(){
  // A replaced player (respawn, dimension change) is gone with its own input; only the same player gets the original back.
  if(player!=null&&mc.player==player&&player.input==input)player.input=original;
  player=null;original=null;input.set(IdleWalkPlan.Direction.NONE);
 }
 private static final class WalkInput extends ClientInput {
  public void tick(){}
  void set(IdleWalkPlan.Direction d){
   boolean forward=d==IdleWalkPlan.Direction.FORWARD,backward=d==IdleWalkPlan.Direction.BACKWARD,left=d==IdleWalkPlan.Direction.LEFT,right=d==IdleWalkPlan.Direction.RIGHT;
   keyPresses=new Input(forward,backward,left,right,false,false,false);
   forwardImpulse=(forward?1:0)-(backward?1:0);leftImpulse=(left?1:0)-(right?1:0);
  }
 }
}
