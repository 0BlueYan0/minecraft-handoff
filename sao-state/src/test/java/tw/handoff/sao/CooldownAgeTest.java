package tw.handoff.sao;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CooldownAgeTest {
 @Test void elapsedTimeAgesRemainingButPreservesTotalDuration(){assertEquals(6.5,CooldownAge.remaining(10,3_500_000_000L));assertEquals(0,CooldownAge.remaining(2,3_000_000_000L));assertThrows(IllegalArgumentException.class,()->CooldownAge.remaining(2,-1));assertThrows(IllegalArgumentException.class,()->CooldownAge.remaining(Double.POSITIVE_INFINITY,0));}
}
