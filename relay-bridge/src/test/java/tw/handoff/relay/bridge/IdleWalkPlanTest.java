package tw.handoff.relay.bridge;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import tw.handoff.relay.bridge.IdleWalkPlan.Decision;
import tw.handoff.relay.bridge.IdleWalkPlan.Direction;
import tw.handoff.relay.bridge.IdleWalkPlan.Event;
import static org.junit.jupiter.api.Assertions.*;

class IdleWalkPlanTest {
    private static final long MIN=60L*1_000_000_000L;
    private final AtomicLong now=new AtomicLong(1_000);
    private final long start=now.get();
    private final IdleWalkPlan plan=new IdleWalkPlan(now::get);

    /** Runs one round from its STARTED tick to its FINISHED tick (the clock does not move) and returns every decision. */
    private List<Decision> round(){
        var all=new ArrayList<Decision>();
        Decision d=plan.tick(null);all.add(d);assertEquals(Event.STARTED,d.event());
        for(int i=0;i<1000&&d.event()!=Event.FINISHED;i++){d=plan.tick(null);all.add(d);}
        return all;
    }

    @Test void nothingHappensBeforeTheFirstInterval(){
        assertEquals(Decision.IDLE,plan.tick(null));
        now.set(start+5*MIN-1);assertEquals(Decision.IDLE,plan.tick(null));assertFalse(plan.walking());
        now.set(start+5*MIN);assertEquals(Event.STARTED,plan.tick(null).event());assertTrue(plan.walking());
    }
    @Test void roundIsFortyTicksEachOfWDSAThenFinished(){
        now.set(start+5*MIN);
        var all=round();
        var counts=new EnumMap<Direction,Integer>(Direction.class);
        List<Direction> order=new ArrayList<>();
        for(Decision d:all)if(d.direction()!=Direction.NONE){counts.merge(d.direction(),1,Integer::sum);if(order.isEmpty()||order.getLast()!=d.direction())order.add(d.direction());}
        assertEquals(List.of(Direction.FORWARD,Direction.RIGHT,Direction.BACKWARD,Direction.LEFT),order);
        for(Direction d:order)assertEquals(40,counts.get(d),d.toString());
        assertEquals(161,all.size());
        Decision last=all.getLast();assertEquals(Event.FINISHED,last.event());assertEquals(Direction.NONE,last.direction());
        assertEquals(Direction.FORWARD,all.getFirst().direction());assertFalse(plan.walking());
        // The segments change exactly at tick 41, 81 and 121.
        assertEquals(Direction.FORWARD,all.get(39).direction());assertEquals(Direction.RIGHT,all.get(40).direction());
        assertEquals(Direction.RIGHT,all.get(79).direction());assertEquals(Direction.BACKWARD,all.get(80).direction());
        assertEquals(Direction.BACKWARD,all.get(119).direction());assertEquals(Direction.LEFT,all.get(120).direction());
        assertEquals(Direction.LEFT,all.get(159).direction());
    }
    @Test void nextRoundIsFiveMinutesAfterThePreviousDueTime(){
        now.set(start+5*MIN);round();
        now.set(start+10*MIN-1);assertEquals(Decision.IDLE,plan.tick(null));
        now.set(start+10*MIN);assertEquals(Event.STARTED,plan.tick(null).event());
    }
    @Test void aLateClockDoesNotStartRoundsBackToBack(){
        now.set(start+12*MIN);round();
        now.set(start+12*MIN+5*MIN-1);assertEquals(Decision.IDLE,plan.tick(null));
        now.set(start+17*MIN);assertEquals(Event.STARTED,plan.tick(null).event());
    }
    @Test void everyBlockingReasonSkipsTheRoundUntilTheNextInterval(){
        long t=start;
        for(String reason:new String[]{"not in a world","player dead","loading screen","viewer has control"}){
            t+=5*MIN;now.set(t);
            Decision skipped=plan.tick(reason);
            assertEquals(Event.SKIPPED,skipped.event(),reason);assertEquals(reason,skipped.reason());assertEquals(Direction.NONE,skipped.direction());assertFalse(plan.walking());
            now.set(t+5*MIN-1);assertEquals(Decision.IDLE,plan.tick(null),"no catch-up before the next interval: "+reason);
        }
        now.set(t+5*MIN);assertEquals(Event.STARTED,plan.tick(null).event());
    }
    @Test void blockingAfterTheDueTimeOnlyAffectsTheTickItIsReportedOn(){
        now.set(start+5*MIN-1);assertEquals(Decision.IDLE,plan.tick("player dead"));
    }
    @Test void everyBlockingReasonInterruptsARunningRoundAndReleasesTheDirection(){
        for(String reason:new String[]{"not in a world","player dead","loading screen","viewer has control","player replaced"}){
            var p=new IdleWalkPlan(now::get);
            now.set(now.get()+5*MIN);
            assertEquals(Event.STARTED,p.tick(null).event());
            for(int i=0;i<50;i++)assertEquals(Event.NONE,p.tick(null).event());
            Decision stop=p.tick(reason);
            assertEquals(Event.INTERRUPTED,stop.event(),reason);assertEquals(Direction.NONE,stop.direction());assertEquals(reason,stop.reason());assertFalse(p.walking());
            assertEquals(Decision.IDLE,p.tick(null),"an interrupted round does not resume");
        }
    }
    @Test void interruptedRoundWaitsAFullIntervalForTheNext(){
        now.set(start+5*MIN);plan.tick(null);plan.tick("player dead");
        now.set(start+10*MIN-1);assertEquals(Decision.IDLE,plan.tick(null));
        now.set(start+10*MIN);assertEquals(Event.STARTED,plan.tick(null).event());
    }
    @Test void realInputWindowBoundary(){
        // Input at the very start ends exactly when the first round is due, which is no longer recent.
        plan.noteHumanInput();
        now.set(start+5*MIN);assertEquals(Event.STARTED,plan.tick(null).event());
    }
    @Test void realInputOneNanosecondInsideTheWindowSkipsTheRound(){
        now.set(start+1);plan.noteHumanInput();
        now.set(start+5*MIN);
        Decision d=plan.tick(null);assertEquals(Event.SKIPPED,d.event());assertEquals("real input on the host window",d.reason());
        now.set(start+10*MIN);assertEquals(Event.STARTED,plan.tick(null).event());
    }
    @Test void realInputBeforeTheWindowDoesNotBlock(){
        now.set(start+1*MIN);plan.noteHumanInput();
        now.set(start+6*MIN+1);
        assertEquals(Event.STARTED,plan.tick(null).event());
    }
    @Test void realInputDuringARoundInterruptsIt(){
        now.set(start+5*MIN);plan.tick(null);for(int i=0;i<10;i++)plan.tick(null);
        plan.noteHumanInput();
        Decision d=plan.tick(null);assertEquals(Event.INTERRUPTED,d.event());assertEquals(Direction.NONE,d.direction());assertEquals("real input on the host window",d.reason());
    }
    @Test void realInputWindowKeepsRoundsBlockedWhileInputContinues(){
        long t=start;
        for(int i=1;i<=4;i++){t+=5*MIN;now.set(t-1*MIN);plan.noteHumanInput();now.set(t);assertEquals(Event.SKIPPED,plan.tick(null).event(),"round "+i);}
        t+=5*MIN;now.set(t);assertEquals(Event.STARTED,plan.tick(null).event());
    }
    @Test void abortEndsARunningRoundAndSchedulesTheNext(){
        assertFalse(plan.abort());
        now.set(start+5*MIN);plan.tick(null);plan.tick(null);
        assertTrue(plan.abort());assertFalse(plan.walking());assertFalse(plan.abort());
        assertEquals(Decision.IDLE,plan.tick(null));
        now.set(start+10*MIN);assertEquals(Event.STARTED,plan.tick(null).event());
    }
    @Test void abortOutsideARoundKeepsTheSchedule(){
        plan.abort();
        now.set(start+5*MIN);assertEquals(Event.STARTED,plan.tick(null).event());
    }
}
