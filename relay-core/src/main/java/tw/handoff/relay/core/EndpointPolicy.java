package tw.handoff.relay.core;
import java.net.InetSocketAddress;
public final class EndpointPolicy {
    private final int port;
    public EndpointPolicy(int port){if(port<1||port>65535)throw new IllegalArgumentException("Invalid viewer endpoint");this.port=port;}
    public boolean allows(InetSocketAddress address){return address!=null&&!address.isUnresolved()&&address.getAddress().isLoopbackAddress()&&address.getPort()==port;}
}
