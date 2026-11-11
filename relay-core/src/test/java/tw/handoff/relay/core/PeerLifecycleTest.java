package tw.handoff.relay.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PeerLifecycleTest {
    @Test void cannotAcquireControlUntilBothSnapshotAndPlayerReadiness() {
        var peer = new PeerLifecycle();
        peer.handshake(); peer.hello(); peer.loginAcknowledged(); peer.packsSelected(); peer.configurationFinished();
        assertFalse(peer.active());
        peer.playerLoaded();
        assertFalse(peer.active());
        peer.snapshotSent();
        assertTrue(peer.active());
    }
    @Test void snapshotCanArriveBeforePlayerReadiness() {
        var peer = new PeerLifecycle();
        peer.handshake(); peer.hello(); peer.loginAcknowledged(); peer.packsSelected(); peer.configurationFinished();
        peer.snapshotSent(); assertFalse(peer.active());
        peer.playerLoaded(); assertTrue(peer.active());
    }
    @Test void invalidPhaseCannotAdvanceOrBeReplayed() {
        var peer = new PeerLifecycle();
        assertThrows(IllegalStateException.class, peer::configurationFinished);
        assertEquals(PeerLifecycle.Phase.HANDSHAKE, peer.phase());
        peer.handshake();peer.hello();
        assertThrows(IllegalStateException.class, peer::hello);
        assertEquals(PeerLifecycle.Phase.LOGIN_ACK,peer.phase());
        peer.close();peer.close();assertEquals(PeerLifecycle.Phase.CLOSED,peer.phase());
        assertThrows(IllegalStateException.class,peer::loginAcknowledged);
    }
}
