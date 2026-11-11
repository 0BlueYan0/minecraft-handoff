package tw.handoff.relay.bridge;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.flag.FeatureFlagSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tw.handoff.relay.core.ReadPause;

public final class HostBridge {
    private static final Logger LOG=LoggerFactory.getLogger("minecraft-handoff-relay");
    private final Minecraft minecraft;
    private final List<NativePeer> peers=new CopyOnWriteArrayList<>();
    private final RegistryAccess.Frozen registries;
    final FrameCodec codec;
    private final Channel upstream;
    private NativeFrontend frontend;
    private HostBridge(Minecraft mc)throws Exception{
        minecraft=mc;if(mc.player==null||mc.level==null||mc.getConnection()==null)throw new IllegalStateException("Join the original server first");
        registries=mc.getConnection().registryAccess();codec=new FrameCodec(registries);
        Channel found=null;
        for(Field f:Connection.class.getDeclaredFields())if(Channel.class.isAssignableFrom(f.getType())){f.setAccessible(true);found=(Channel)f.get(mc.getConnection().getConnection());break;}
        if(found==null)throw new IllegalStateException("Original transport unavailable");upstream=found;
    }
    public static String start(Object client,Path directory,int port)throws Exception{
        Minecraft mc=(Minecraft)client;
        if(!mc.gameDirectory.toPath().toRealPath().equals(directory.toRealPath()))throw new IllegalStateException("Wrong game instance");
        HostBridge host=new HostBridge(mc);host.frontend=new NativeFrontend(host,port);
        host.upstream.eventLoop().submit(()->host.upstream.pipeline().addBefore("packet_handler","handoff_relay_capture",new ChannelDuplexHandler(){
            public void channelRead(ChannelHandlerContext context,Object message)throws Exception{
                List<byte[]> frames=null;
                if(message instanceof Packet<?> packet&&host.peers.stream().anyMatch(peer->peer.live)&&MirrorRules.forward(packet)){
                    try{frames=host.codec.freeze(packet);}catch(RuntimeException unsupported){LOG.debug("Relay omitted unsupported packet type {}",packet.type().id());}
                }
                context.fireChannelRead(message);
                if(frames!=null)for(NativePeer peer:host.peers)if(peer.live)peer.sendFrames(frames);
            }
        })).syncUninterruptibly();
        ClientTickEvents.END_CLIENT_TICK.register(clientTick->{try{host.tick();}catch(RuntimeException failure){LOG.warn("Relay tick failed ({})",failure.getClass().getSimpleName());}});
        LOG.info("Relay frontend ready on loopback port {}",host.frontend.port());
        return Integer.toString(host.frontend.port());
    }
    Minecraft minecraft(){return minecraft;}
    RegistryAccess.Frozen registries(){return registries;}
    GameProfile profile(){return minecraft.player.getGameProfile();}
    FeatureFlagSet features(){return minecraft.level.enabledFeatures();}
    void add(NativePeer peer){peers.add(peer);}
    void remove(NativePeer peer){peers.remove(peer);}
    void tick(){for(NativePeer peer:peers)peer.tick();}
    void ready(NativePeer peer){if(peer.lifecycle.active()&&!peer.closed)LOG.info("Relay viewer world ready");}
    void input(NativePeer peer,Packet<?> packet){ /* Input ownership is added after the snapshot path is validated. */ }
    void snapshot(NativePeer peer){
        upstream.eventLoop().execute(()->{
            if(!upstream.config().isAutoRead()){peer.close("Original connection is changing state");return;}
            upstream.config().setAutoRead(false);
            ReadPause pause=new ReadPause(()->upstream.eventLoop().execute(()->{if(upstream.isActive())upstream.config().setAutoRead(true);}));
            var timeout=upstream.eventLoop().schedule(()->{if(pause.active()){peer.close("Host snapshot is busy; reconnect");pause.close();}},2,java.util.concurrent.TimeUnit.SECONDS);
            minecraft.execute(()->{
                try{
                    if(!pause.active())return;
                    if(!peer.closed){SnapshotPackets.send(this,peer);peer.lifecycle.snapshotSent();peer.live=true;ready(peer);}
                }catch(Exception failure){peer.close("Snapshot unavailable: "+failure.getClass().getSimpleName());LOG.warn("Relay snapshot failed ({})",failure.getClass().getSimpleName());}
                finally{timeout.cancel(false);pause.close();}
            });
        });
    }
}
