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
    final tw.handoff.relay.core.SaoReplayTable table;
    private final int payloadId;
    private static final java.util.Set<String> TABLE_CHANNELS=java.util.Set.of("saocore:mux","capuodev:quest_hud","saoblessing:main");
    private final Channel upstream;
    private NativeFrontend frontend;
    private RemoteControl control;
    PacketLab lab;
    private HostBridge(Minecraft mc)throws Exception{
        minecraft=mc;if(mc.player==null||mc.level==null||mc.getConnection()==null)throw new IllegalStateException("Join the original server first");
        registries=mc.getConnection().registryAccess();codec=new FrameCodec(registries);
        payloadId=customPayloadId();table=new tw.handoff.relay.core.SaoReplayTable(payloadId,4*1024*1024);
        Channel found=null;
        for(Field f:Connection.class.getDeclaredFields())if(Channel.class.isAssignableFrom(f.getType())){f.setAccessible(true);found=(Channel)f.get(mc.getConnection().getConnection());break;}
        if(found==null)throw new IllegalStateException("Original transport unavailable");upstream=found;
    }
    /** Same way PacketLab.install finds the play custom payload packet id. */
    private int customPayloadId(){
        byte[] brand=codec.freeze(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(new net.minecraft.network.protocol.common.custom.BrandPayload("join-wire-id"))).getFirst();
        return tw.handoff.relay.core.OpaqueFrames.readVarInt(brand,new int[]{0});
    }
    public static String start(Object client,Path directory,int port,boolean generic)throws Exception{
        Minecraft mc=(Minecraft)client;
        if(!mc.gameDirectory.toPath().toRealPath().equals(directory.toRealPath()))throw new IllegalStateException("Wrong game instance");
        HostBridge host=new HostBridge(mc);PacketLab.Mode labMode=PacketLab.mode(mc,generic);LOG.info("Packet forwarding mode: {}",labMode==null?"OFF":labMode);if(labMode!=null)host.lab=new PacketLab(host,host.upstream,labMode);host.frontend=new NativeFrontend(host,port);host.control=new RemoteControl(host);
        if(host.lab!=null)host.lab.install();
        host.upstream.eventLoop().submit(()->host.upstream.pipeline().addBefore("packet_handler","handoff_relay_capture",new ChannelDuplexHandler(){
            public void channelRead(ChannelHandlerContext context,Object message)throws Exception{
                List<byte[]> frames=null;
                // The frame enters the table before the game thread handles it, whether or not a viewer is connected.
                if(message instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&TABLE_CHANNELS.contains(custom.payload().type().id().toString())){
                    // A frame the host cannot record leaves the replay short, so the table reports it as replay-skipped.
                    // When freeze fails there are no bytes to read the mux sub-channel from, so it is marked even if the table would have ignored that sub-channel. This is deliberately conservative.
                    if(custom.payload() instanceof net.minecraft.network.protocol.common.custom.DiscardedPayload)host.table.markSkipped();
                    else try{for(byte[] frame:host.codec.freeze(custom))host.table.observe(frame);}catch(RuntimeException|LinkageError unsupported){host.table.markSkipped();LOG.debug("Replay table could not record a payload");}
                }
                if(host.lab==null&&message instanceof Packet<?> packet&&host.peers.stream().anyMatch(peer->peer.live)&&MirrorRules.forward(packet)){
                    try{frames=host.codec.freeze(packet);}catch(RuntimeException|LinkageError unsupported){LOG.debug("Relay omitted unsupported packet type {}",packet.type().id());}
                }
                context.fireChannelRead(message);
                if(frames!=null)for(NativePeer peer:host.peers)if(peer.live)try{peer.sendFrames(frames);}catch(RuntimeException|LinkageError ignored){peer.close("Relay stream unavailable");}
            }
            public void write(ChannelHandlerContext ctx,Object msg,io.netty.channel.ChannelPromise promise)throws Exception{
                if(host.lab!=null&&host.control.remoteOwns()&&msg instanceof Packet<?> p&&!PacketLab.local(p.type().id().getPath())){
                    if(!(p instanceof net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket)){promise.setSuccess();return;}
                    // Original launcher/connection payloads remain owned by the original client.
                    String ns=((net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket)p).payload().type().id().getNamespace();
                    if(!java.util.Set.of("launcher","fabric","fabric-networking-api-v1","minecraft").contains(ns)){promise.setSuccess();return;}
                }ctx.write(msg,promise);
            }
        })).syncUninterruptibly();
        ClientTickEvents.START_CLIENT_TICK.register(clientTick->{try{host.control.tick();}catch(RuntimeException|LinkageError failure){host.control.release();LOG.warn("Relay input stopped ({})",failure.getClass().getSimpleName());}});
        ClientTickEvents.END_CLIENT_TICK.register(clientTick->{try{host.tick();host.control.pose();}catch(RuntimeException|LinkageError failure){LOG.warn("Relay tick failed ({})",failure.getClass().getSimpleName());}});
        mc.getWindow().setTitle("Minecraft Handoff - HOST (F8: local control)");
        LOG.info("Relay frontend ready on loopback port {}",host.frontend.port());
        return host.frontend.port()+":"+host.profile().getId()+":"+host.profile().getName();
    }
    Minecraft minecraft(){return minecraft;}
    RegistryAccess.Frozen registries(){return registries;}
    GameProfile profile(){return minecraft.player.getGameProfile();}
    FeatureFlagSet features(){return minecraft.level.enabledFeatures();}
    void add(NativePeer peer){peers.add(peer);}
    List<NativePeer> peers(){return List.copyOf(peers);}
    void remove(NativePeer peer){peers.remove(peer);}
    void tick(){for(NativePeer peer:peers)peer.tick();}
    void ready(NativePeer peer){if(!peer.closed&&peer.lifecycle.claimReadyNotification()){LOG.info("Relay viewer world ready");control.ready(peer);}}
    void input(NativePeer peer,Packet<?> packet){control.input(peer,packet);}
    void snapshot(NativePeer peer){
        upstream.eventLoop().execute(()->{
            if(!upstream.config().isAutoRead()){peer.close("Original connection is changing state");return;}
            upstream.config().setAutoRead(false);
            ReadPause pause=new ReadPause(()->upstream.eventLoop().execute(()->{if(upstream.isActive())upstream.config().setAutoRead(true);}));
            var timeout=upstream.eventLoop().schedule(()->{if(pause.active()){peer.close("Host snapshot is busy; reconnect");pause.close();}},2,java.util.concurrent.TimeUnit.SECONDS);
            var join=new JoinState(peer.gate,table,payloadId,new JoinState.Ports(
                ()->{SnapshotPackets.send(this,peer);if(lab!=null&&lab.hasJournal())lab.replay(peer);}, // PRODUCTION keeps no journal, so full history is never replayed.
                ()->net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.getSendable().stream().map(Object::toString).toList(),
                frame->peer.sendFrames(List.of(frame)),
                text->peer.send(RemoteControl.message(text)),
                now->tw.handoff.sao.SaoSnapshot.capture(minecraft.getClass().getClassLoader(),now),
                System::nanoTime,
                ()->{peer.lifecycle.snapshotSent();peer.live=true;ready(peer);}));
            peer.join=join;
            join.start(minecraft::execute,()->pause.active()&&!peer.closed,failure->{peer.close("Snapshot unavailable: "+failure.getClass().getSimpleName());LOG.warn("Relay snapshot failed ({})",failure.getClass().getSimpleName());},()->{timeout.cancel(false);pause.close();});
        });
    }
}
