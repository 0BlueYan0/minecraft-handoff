package tw.handoff.relay.core;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EndpointPolicyTest {
    @Test void onlyTheAssignedLoopbackPortIsAllowed()throws Exception{
        var policy=new EndpointPolicy(25587);
        assertTrue(policy.allows(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}),25587)));
        assertTrue(policy.allows(new InetSocketAddress(InetAddress.getByName("::1"),25587)));
        assertFalse(policy.allows(new InetSocketAddress(InetAddress.getByAddress(new byte[]{127,0,0,1}),25588)));
        assertFalse(policy.allows(new InetSocketAddress(InetAddress.getByAddress(new byte[]{(byte)192,0,2,1}),25587)));
        assertFalse(policy.allows(new InetSocketAddress(InetAddress.getByAddress(new byte[]{0,0,0,0}),25587)));
        assertFalse(policy.allows(InetSocketAddress.createUnresolved("localhost",25587)));
        assertFalse(policy.allows(null));
    }
    @Test void invalidPortsFailClosed(){assertThrows(IllegalArgumentException.class,()->new EndpointPolicy(0));assertThrows(IllegalArgumentException.class,()->new EndpointPolicy(65536));}
}
