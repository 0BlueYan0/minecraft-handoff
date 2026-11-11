package tw.handoff.relay.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PendingBudgetTest {
    @Test void rejectsSlowConsumerWithoutReservingExcessBytes() {
        var budget=new PendingBudget(2,100);
        assertTrue(budget.reserve(80));assertFalse(budget.reserve(21));
        assertTrue(budget.reserve(20));assertFalse(budget.reserve(0));
        budget.release(80);assertTrue(budget.reserve(50));
        budget.release(20);budget.release(50);assertEquals(0,budget.bytes());
    }
    @Test void invalidSizesCannotCorruptBudget() {
        var budget=new PendingBudget(2,100);
        assertThrows(IllegalArgumentException.class,()->budget.reserve(-1));
        assertThrows(IllegalStateException.class,()->budget.release(1));
        assertEquals(0,budget.bytes());
    }
}
