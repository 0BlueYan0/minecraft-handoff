package tw.handoff.relay.bridge;
import java.util.function.LongSupplier;

/**
 * Schedule and state machine of the resident host's idle walk. It knows nothing about Minecraft: the caller passes a blocking reason each game tick
 * and applies the returned direction. One round is W, D, S, A for {@link #SEGMENT_TICKS} game ticks each, started every {@link #INTERVAL_NANOS}.
 */
final class IdleWalkPlan {
 static final long INTERVAL_NANOS=5L*60*1_000_000_000L;
 /** A real input on the host window blocks a round that is due, or running, for this long. */
 static final long HUMAN_WINDOW_NANOS=5L*60*1_000_000_000L;
 static final int SEGMENT_TICKS=40;
 enum Direction{NONE,FORWARD,RIGHT,BACKWARD,LEFT}
 enum Event{NONE,STARTED,FINISHED,INTERRUPTED,SKIPPED}
 /** direction is what the player input should hold this tick. After FINISHED, INTERRUPTED and SKIPPED it is NONE. */
 record Decision(Direction direction,Event event,String reason){
  static final Decision IDLE=new Decision(Direction.NONE,Event.NONE,null);
 }
 private static final Direction[] ORDER={Direction.FORWARD,Direction.RIGHT,Direction.BACKWARD,Direction.LEFT};
 private final LongSupplier clock;
 private volatile long lastHuman;private volatile boolean humanSeen;
 private long due;private boolean walking;private int walked;
 IdleWalkPlan(LongSupplier clock){this.clock=clock;due=clock.getAsLong()+INTERVAL_NANOS;}
 /** Called from the GLFW thread. */
 void noteHumanInput(){lastHuman=clock.getAsLong();humanSeen=true;}
 boolean walking(){return walking;}
 private boolean humanRecent(long now){return humanSeen&&now-lastHuman<HUMAN_WINDOW_NANOS;}
 private void scheduleNext(long now){due+=INTERVAL_NANOS;if(due<=now)due=now+INTERVAL_NANOS;}
 /** @param blocked why the game cannot be walked right now (no player, dead, loading screen, viewer control), or null. */
 Decision tick(String blocked){
  long now=clock.getAsLong();
  String reason=blocked!=null?blocked:humanRecent(now)?"real input on the host window":null;
  if(walking){
   if(reason!=null){walking=false;scheduleNext(now);return new Decision(Direction.NONE,Event.INTERRUPTED,reason);}
   if(walked>=ORDER.length*SEGMENT_TICKS){walking=false;scheduleNext(now);return new Decision(Direction.NONE,Event.FINISHED,null);}
   return new Decision(ORDER[walked++/SEGMENT_TICKS],Event.NONE,null);
  }
  if(now-due<0)return Decision.IDLE;
  if(reason!=null){scheduleNext(now);return new Decision(Direction.NONE,Event.SKIPPED,reason);}
  walking=true;walked=1;
  return new Decision(ORDER[0],Event.STARTED,null);
 }
 /** Ends a running round at once, for example right before a viewer is granted control. Returns whether a round was running. */
 boolean abort(){
  if(!walking)return false;
  walking=false;scheduleNext(clock.getAsLong());return true;
 }
}
