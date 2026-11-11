package tw.handoff.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ControlSessionTest {
    private final ControlSession session = new ControlSession();

    @Test void startsWithoutAController() {
        assertTrue(session.controller().isEmpty());
        assertFalse(session.acceptsInput("A", 0));
    }

    @Test void firstReadyTicketAcquiresEpochOne() {
        var ticket = session.prepare("A");
        assertTrue(session.controller().isEmpty());
        assertTrue(session.ready(ticket));
        assertEquals(new ControlSession.Lease("A", 1), session.controller().orElseThrow());
        assertTrue(session.acceptsInput("A", 1));
        assertFalse(session.acceptsInput("B", 1));
    }

    @Test void preparingReplacementPreservesController() {
        session.ready(session.prepare("A"));
        session.prepare("B");
        assertTrue(session.acceptsInput("A", 1));
        assertFalse(session.acceptsInput("B", 1));
    }

    @Test void failedPreparationPreservesController() {
        session.ready(session.prepare("A"));
        var b = session.prepare("B");
        session.cancel(b);
        assertTrue(session.acceptsInput("A", 1));
        assertEquals("A", session.controller().orElseThrow().clientId());
    }

    @Test void canceledTicketCannotAcquireControl() {
        var b = session.prepare("B");
        session.cancel(b);
        assertFalse(session.ready(b));
        assertTrue(session.controller().isEmpty());
    }

    @Test void oldLeaseRejectedAfterTransfer() {
        session.ready(session.prepare("A"));
        assertTrue(session.ready(session.prepare("B")));
        assertEquals(new ControlSession.Lease("B", 2), session.controller().orElseThrow());
        assertFalse(session.acceptsInput("A", 1));
        assertFalse(session.acceptsInput("B", 1));
        assertTrue(session.acceptsInput("B", 2));
    }

    @Test void readinessCannotBeReplayed() {
        var a = session.prepare("A");
        assertTrue(session.ready(a));
        assertFalse(session.ready(a));
        assertEquals(1, session.controller().orElseThrow().epoch());
    }

    @Test void obsoleteTicketCannotCancelNewPreparation() {
        var old = session.prepare("B");
        var current = session.prepare("C");
        session.cancel(old);
        assertFalse(session.ready(old));
        assertTrue(session.ready(current));
    }

    @Test void reconnectWithSameIdCannotReuseOldTicket() {
        var old = session.prepare("B");
        session.disconnect("B");
        var current = session.prepare("B");
        assertNotEquals(old, current);
        assertFalse(session.ready(old));
        assertTrue(session.ready(current));
    }

    @Test void disconnectingControllerRevokesItsLease() {
        session.ready(session.prepare("A"));
        session.disconnect("A");
        assertTrue(session.controller().isEmpty());
        assertFalse(session.acceptsInput("A", 1));
        session.ready(session.prepare("B"));
        assertEquals(2, session.controller().orElseThrow().epoch());
    }

    @Test void disconnectingNoncontrollerPreservesController() {
        session.ready(session.prepare("A"));
        session.disconnect("B");
        assertTrue(session.acceptsInput("A", 1));
    }

    @Test void disconnectedCandidateCannotAcquireControl() {
        session.ready(session.prepare("A"));
        var b = session.prepare("B");
        session.disconnect("B");
        assertFalse(session.ready(b));
        assertTrue(session.acceptsInput("A", 1));
    }

    @Test void candidateCanFinishAfterOldControllerDisconnects() {
        session.ready(session.prepare("A"));
        var b = session.prepare("B");
        session.disconnect("A");
        assertTrue(session.ready(b));
        assertTrue(session.acceptsInput("B", 2));
    }

    @Test void rejectsBlankClientIds() {
        assertThrows(IllegalArgumentException.class, () -> session.prepare(" "));
        assertThrows(IllegalArgumentException.class, () -> session.prepare(""));
        assertThrows(IllegalArgumentException.class, () -> session.prepare(null));
        assertFalse(session.acceptsInput(null, 1));
    }

    @Test void nullReadinessOrCancellationCannotChangeOwnership() {
        session.ready(session.prepare("A"));
        assertFalse(session.ready(null));
        session.cancel(null);
        assertTrue(session.acceptsInput("A", 1));
    }
}
