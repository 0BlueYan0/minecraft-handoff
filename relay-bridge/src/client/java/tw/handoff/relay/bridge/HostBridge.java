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
import tw.handoff.relay.core.FailureReport;
import tw.handoff.relay.core.ReadPause;

public final class HostBridge {
    private static final Logger LOG=LoggerFactory.getLogger("minecraft-handoff-relay");
    private final Minecraft minecraft;
    private final List<NativePeer> peers=new CopyOnWriteArrayList<>();
    // A server switch behind the proxy gives the client new registries; encoding with the old ones fails on the next login packet.
    private volatile RegistryAccess.Frozen registries;
    private volatile FrameCodec codec;
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
    public static String start(Object client,Path directory,int port,boolean generic)throws Exception{return start(client,directory,port,generic,false);}
    /** idleWalk turns on the timed walk that keeps an unattended host from idling; it is a parameter, not a JVM property, because the game JVM belongs to the user. */
    public static String start(Object client,Path directory,int port,boolean generic,boolean idleWalk)throws Exception{
        Minecraft mc=(Minecraft)client;
        if(!mc.gameDirectory.toPath().toRealPath().equals(directory.toRealPath()))throw new IllegalStateException("Wrong game instance");
        HostBridge host=new HostBridge(mc);
        try{
        PacketLab.Mode labMode=PacketLab.mode(mc,generic);LOG.info("Packet forwarding mode: {}",labMode==null?"OFF":labMode);LOG.info("Idle walk: {}",idleWalk?"ON":"OFF");if(labMode!=null)host.lab=new PacketLab(host,host.upstream,labMode);host.frontend=new NativeFrontend(host,port);host.control=new RemoteControl(host,idleWalk);
        if(host.lab!=null)host.lab.install();
        host.upstream.eventLoop().submit(()->host.upstream.pipeline().addBefore("packet_handler",CAPTURE,new ChannelDuplexHandler(){
            public void channelRead(ChannelHandlerContext context,Object message)throws Exception{
                // A stopped bridge whose handler could not be removed only passes traffic on.
                if(host.stopped){context.fireChannelRead(message);return;}
                List<byte[]> frames=null;
                // The frame enters the table before the game thread handles it, whether or not a viewer is connected.
                if(message instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&TABLE_CHANNELS.contains(custom.payload().type().id().toString())){
                    // A frame the host cannot record leaves the replay short, so the table reports it as replay-skipped.
                    // When freeze fails there are no bytes to read the mux sub-channel from, so it is marked even if the table would have ignored that sub-channel. This is deliberately conservative.
                    if(custom.payload() instanceof net.minecraft.network.protocol.common.custom.DiscardedPayload)host.table.markSkipped();
                    else try{for(byte[] frame:host.codec().freeze(custom))host.table.observe(frame);}catch(RuntimeException|LinkageError unsupported){host.table.markSkipped();report("Replay table could not record a payload",unsupported,true);}
                }
                if(host.lab==null&&message instanceof Packet<?> packet&&host.peers.stream().anyMatch(peer->peer.live)&&MirrorRules.forward(packet)){
                    try{frames=host.codec().freeze(packet);}catch(RuntimeException|LinkageError unsupported){LOG.debug("Relay omitted unsupported packet type {}",packet.type().id());}
                }
                context.fireChannelRead(message);
                if(frames!=null)for(NativePeer peer:host.peers)if(peer.live)try{peer.sendFrames(frames);}catch(RuntimeException|LinkageError ignored){peer.close("Relay stream unavailable");}
            }
            public void write(ChannelHandlerContext ctx,Object msg,io.netty.channel.ChannelPromise promise)throws Exception{
                if(host.stopped){ctx.write(msg,promise);return;}
                if(host.lab!=null&&host.control.remoteOwns()&&msg instanceof Packet<?> p&&!PacketLab.local(p.type().id().getPath())){
                    if(!(p instanceof net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket)){promise.setSuccess();return;}
                    // Original launcher/connection payloads remain owned by the original client.
                    String ns=((net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket)p).payload().type().id().getNamespace();
                    if(!java.util.Set.of("launcher","fabric","fabric-networking-api-v1","minecraft").contains(ns)){promise.setSuccess();return;}
                }ctx.write(msg,promise);
            }
        })).syncUninterruptibly();
        // Fabric has no way to remove a listener, so a stopped bridge's listeners stay registered and return at once.
        ClientTickEvents.START_CLIENT_TICK.register(clientTick->{if(host.stopped)return;try{host.control.tick();}catch(RuntimeException|LinkageError failure){host.control.release();report("Relay input stopped",failure);}});
        ClientTickEvents.END_CLIENT_TICK.register(clientTick->{if(host.stopped)return;try{host.tick();host.control.pose();}catch(RuntimeException|LinkageError failure){report("Relay tick failed",failure);}});
        host.titleSet=true;mc.getWindow().setTitle("Minecraft Handoff - HOST (F8: local control)");
        LOG.info("Relay frontend ready on loopback port {}",host.frontend.port());
        current=host;
        return host.frontend.port()+":"+host.profile().getId()+":"+host.profile().getName();
        }catch(Exception|LinkageError failure){
            // A start that fails halfway must not leave a handler, a listening socket or a window callback behind.
            try{host.shutdown("Host failed to start");}catch(Exception|LinkageError cleanup){failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    private static final String CAPTURE="handoff_relay_capture";
    private static volatile HostBridge current;
    private volatile boolean stopped;private boolean stopDone,titleSet;private Exception stopFailure;
    // Read pauses of snapshots still running, so a stop cannot leave the original connection with auto read off.
    private final java.util.Set<ReadPause> pauses=java.util.concurrent.ConcurrentHashMap.newKeySet();
    /**
     * Stops the bridge started by this class loader's start and undoes everything it installed (game thread). Every step runs even if an earlier one fails; the first failure is then thrown,
     * so the caller does not load a new bridge over a half-stopped one. Calling it again after a clean stop does nothing.
     */
    public static void stop(String reason)throws Exception{HostBridge host=current;if(host!=null)host.shutdown(reason);}
    private interface Step{void run()throws Exception;}
    private void step(String what,Step step){
        try{step.run();}catch(Exception|LinkageError failure){
            report("Relay stop step failed: "+what,failure);
            Exception wrapped=failure instanceof Exception e?e:new IllegalStateException(what,failure);
            if(stopFailure==null)stopFailure=wrapped;else stopFailure.addSuppressed(wrapped);
        }
    }
    private synchronized void shutdown(String reason)throws Exception{
        if(stopDone)return;
        stopped=true;stopFailure=null;LOG.info("Relay bridge stopping: {}",reason);
        // Viewer control first: it announces the release to the viewers that are still connected, and puts the original player input and key state back.
        if(control!=null)step("control",control::shutdown);
        if(frontend!=null)step("frontend",frontend::close);
        for(NativePeer peer:peers())step("peer",()->peer.close(reason));
        step("capture handler",()->removeHandler(upstream,CAPTURE));
        if(lab!=null)step("lab handler",lab::uninstall);
        for(ReadPause pause:pauses)step("read pause",pause::close);
        if(titleSet)step("window title",()->{minecraft.updateTitle();titleSet=false;});
        if(stopFailure!=null)throw stopFailure;
        stopDone=true;if(current==this)current=null;
    }
    /** Removes a handler on the channel's own thread and waits for it, so no packet reaches the stopped bridge afterwards. */
    static void removeHandler(Channel channel,String name){
        Runnable remove=()->{if(channel.pipeline().get(name)!=null)channel.pipeline().remove(name);};
        if(channel.eventLoop().inEventLoop()){remove.run();return;}
        if(!channel.isRegistered())return; // A closed connection has no pipeline left to clean.
        var done=channel.eventLoop().submit(remove);
        if(!done.awaitUninterruptibly(5000))throw new IllegalStateException("Pipeline change timed out");
        done.syncUninterruptibly();
    }
    private static final FailureReport FAILURES=new FailureReport();
    /** The first failure of each kind logs its full stack trace and cause chain. Later ones log one line, so a failing tick does not flood the log. */
    private static void report(String what,Throwable failure){report(what,failure,false);}
    /** quietRepeats sends repeats to debug instead of the throttled warn line. */
    private static void report(String what,Throwable failure,boolean quietRepeats){
        FailureReport.Outcome outcome=FAILURES.decide(what,failure);
        switch(outcome.kind()){
            case FULL->LOG.warn(what,failure);
            case LINE->{
                if(quietRepeats)LOG.debug("{} ({})",what,failure.getClass().getSimpleName());
                else LOG.warn("{} ({}), {} similar skipped in the last interval",what,failure.getClass().getSimpleName(),outcome.suppressed());
            }
            case SKIP->{}
        }
    }
    Minecraft minecraft(){return minecraft;}
    RegistryAccess.Frozen registries(){return registries;}
    FrameCodec codec(){return codec;}
    GameProfile profile(){return minecraft.player.getGameProfile();}
    FeatureFlagSet features(){return minecraft.level.enabledFeatures();}
    /** A viewer accepted just before the frontend closed must not join a stopped bridge: nothing would ever tick it. */
    void add(NativePeer peer){
        if(stopped){peer.close("Host updating; reconnect");return;}
        peers.add(peer);
        // shutdown may have set stopped and copied peers() between the check above and the add, so it would never see this peer.
        if(stopped)peer.close("Host updating; reconnect");
    }
    List<NativePeer> peers(){return List.copyOf(peers);}
    void remove(NativePeer peer){peers.remove(peer);}
    void tick(){refreshRegistries();for(NativePeer peer:peers)peer.tick();}
    /** Viewers were built from the old registries too, so they are disconnected and resync on reconnect. */
    private void refreshRegistries(){
        var connection=minecraft.getConnection();if(connection==null)return;
        RegistryAccess.Frozen now=connection.registryAccess();if(now==registries)return;
        codec=new FrameCodec(now);registries=now;
        LOG.info("Host registries changed; disconnecting {} viewer(s) to resync",peers.size());
        for(NativePeer peer:peers)peer.close("Host changed server; reconnect");
    }
    void ready(NativePeer peer){if(stopped)return;if(!peer.closed&&peer.lifecycle.claimReadyNotification()){LOG.info("Relay viewer world ready");control.ready(peer);}}
    void input(NativePeer peer,Packet<?> packet){control.input(peer,packet);}
    void snapshot(NativePeer peer){
        if(stopped){peer.close("Host updating; reconnect");return;}
        upstream.eventLoop().execute(()->{
            if(stopped){peer.close("Host updating; reconnect");return;}
            if(!upstream.config().isAutoRead()){peer.close("Original connection is changing state");return;}
            upstream.config().setAutoRead(false);
            ReadPause pause=new ReadPause(()->upstream.eventLoop().execute(()->{if(upstream.isActive())upstream.config().setAutoRead(true);}));pauses.add(pause);if(stopped)pause.close(); // shutdown may have iterated pauses just before this one was added
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
            join.start(minecraft::execute,()->pause.active()&&!peer.closed,failure->{peer.close("Snapshot unavailable: "+FailureReport.reason(failure));report("Relay snapshot failed",failure);},()->{timeout.cancel(false);pause.close();pauses.remove(pause);});
        });
    }
}
