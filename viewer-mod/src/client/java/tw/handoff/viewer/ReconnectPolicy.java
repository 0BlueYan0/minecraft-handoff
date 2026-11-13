package tw.handoff.viewer;

import java.net.InetSocketAddress;
import java.util.Optional;
import tw.handoff.relay.core.EndpointPolicy;

/** Decides whether the disconnect screen offers a reconnect button and where it connects. Uses no Minecraft classes. */
public final class ReconnectPolicy {
    public static final String HOST = "127.0.0.1";

    private ReconnectPolicy() {
    }

    /** The local endpoint as host:port, present only for a viewer port that EndpointPolicy accepts. */
    public static Optional<String> target(String viewerPort) {
        if (viewerPort == null) return Optional.empty();
        int port;
        try {
            port = Integer.parseInt(viewerPort.trim());
        } catch (NumberFormatException notNumber) {
            return Optional.empty();
        }
        if (port < 1 || port > 65535) return Optional.empty();
        if (!new EndpointPolicy(port).allows(new InetSocketAddress(HOST, port))) return Optional.empty();
        return Optional.of(HOST + ":" + port);
    }

    public static Optional<String> target() {
        return target(System.getProperty("handoff.viewerPort"));
    }
}
