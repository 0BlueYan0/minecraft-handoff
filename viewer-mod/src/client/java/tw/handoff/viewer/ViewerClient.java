package tw.handoff.viewer;
import java.net.InetSocketAddress;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.LoggerFactory;
import tw.handoff.relay.core.EndpointPolicy;

public final class ViewerClient implements ClientModInitializer {
    private static final EndpointPolicy ENDPOINT=new EndpointPolicy(Integer.parseInt(System.getProperty("handoff.viewerPort","0")));
    public static void check(InetSocketAddress address){if(!ENDPOINT.allows(address))throw new IllegalStateException("Viewer can only connect to its local handoff endpoint");}
    public void onInitializeClient(){
        LoggerFactory.getLogger("handoff-viewer").info("Viewer destination guard enabled");
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->client.getWindow().setTitle("Minecraft Handoff Viewer"));
    }
}
