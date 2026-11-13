package tw.handoff.viewer;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import tw.handoff.relay.core.EndpointPolicy;

class ReconnectPolicyTest {
    @Test
    void noButtonWithoutPort() {
        assertTrue(ReconnectPolicy.target(null).isEmpty());
        assertTrue(ReconnectPolicy.target("").isEmpty());
        assertTrue(ReconnectPolicy.target("0").isEmpty());
    }

    @Test
    void noButtonForInvalidPort() {
        assertTrue(ReconnectPolicy.target("-5").isEmpty());
        assertTrue(ReconnectPolicy.target("65536").isEmpty());
        assertTrue(ReconnectPolicy.target("abc").isEmpty());
    }

    @Test
    void targetIsLoopbackAndPassesEndpointPolicy() {
        var target = ReconnectPolicy.target("25570");
        assertEquals("127.0.0.1:25570", target.orElseThrow());
        assertTrue(new EndpointPolicy(25570).allows(new InetSocketAddress("127.0.0.1", 25570)));
    }
}
