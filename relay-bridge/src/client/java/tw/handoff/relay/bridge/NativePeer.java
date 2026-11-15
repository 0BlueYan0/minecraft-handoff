package tw.handoff.relay.bridge;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.handshake.*;
import net.minecraft.network.protocol.login.*;
import net.minecraft.network.protocol.configuration.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.world.flag.FeatureFlags;
import tw.handoff.relay.core.PeerLifecycle;
import tw.handoff.relay.core.PacedOutbox;

final class NativePeer implements InvocationHandler {
    final String id=UUID.randomUUID().toString();
    final HostBridge host;final Connection connection;final Channel channel;
    final PeerLifecycle lifecycle=new PeerLifecycle();
    final tw.handoff.relay.core.JoinGate gate=new tw.handoff.relay.core.JoinGate();
    volatile JoinState join;
    final tw.handoff.relay.core.InputInbox inputs=new tw.handoff.relay.core.InputInbox(64);
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("minecraft-handoff-relay");
    /** In flight as before (2048 frames, 32 MB); a join snapshot may wait behind that up to 64 MB. */
    final PacedOutbox outbox=new PacedOutbox(2048,32L*1024*1024,65_536,64L*1024*1024);
    /** Frames and bytes handed to sendFrames, for the snapshot log line. */
    final java.util.concurrent.atomic.AtomicLong offeredFrames=new java.util.concurrent.atomic.AtomicLong(),offeredBytes=new java.util.concurrent.atomic.AtomicLong();
    final Object listener;
    volatile ConnectionProtocol protocol=ConnectionProtocol.HANDSHAKING;
    volatile boolean live,closed;
    long created=System.nanoTime(),lastKeepalive=created,keepalive;
    private long languageWindow;private int languageCount;
    /** SAOUI asks at most every 10 s and flushes up to two MISSING per pass; 5 per 10 s covers that and caps a misbehaving viewer. */
    boolean allowLanguageRequest(long now){if(now-languageWindow>10_000_000_000L){languageWindow=now;languageCount=0;}return ++languageCount<=5;}
    NativePeer(HostBridge host,Connection connection,Channel channel){
        this.host=host;this.connection=connection;this.channel=channel;
        listener=Proxy.newProxyInstance(ServerGamePacketListener.class.getClassLoader(),new Class<?>[]{ServerHandshakePacketListener.class,ServerLoginPacketListener.class,ServerConfigurationPacketListener.class,ServerGamePacketListener.class},this);
    }
    ServerHandshakePacketListener handshakeListener(){return (ServerHandshakePacketListener)listener;}
    public Object invoke(Object proxy,Method method,Object[] args){
        String name=method.getName();
        if(method.getDeclaringClass()==Object.class)return switch(name){case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->"HandoffPrivatePeer";};
        // Names are intermediary-obfuscated in a production game; the public interface types are stable.
        if(method.getReturnType()==PacketFlow.class)return PacketFlow.SERVERBOUND;
        if(method.getReturnType()==ConnectionProtocol.class)return protocol;
        if(method.getReturnType()==boolean.class)return !closed&&connection.isConnected();
        if(method.getReturnType()==DisconnectionDetails.class)return new DisconnectionDetails(Component.literal("Relay connection closed"));
        if(args!=null&&args.length==1&&args[0] instanceof DisconnectionDetails details){
            if(!closed)LOG.info("Relay viewer disconnected: {}",details.reason().getString());
            closed=true;live=false;lifecycle.close();host.remove(this);outbox.clear();return null;
        }
        if(args!=null&&args.length>1&&args[0] instanceof Packet<?> && args[1] instanceof Exception){close("Invalid relay packet");return null;}
        if(args==null||args.length==0||!(args[0] instanceof Packet<?> packet))return null;
        try{handle(packet);}catch(Exception failure){close("Unsupported relay state: "+failure.getClass().getSimpleName());}
        return null;
    }
    private void handle(Packet<?> packet){
        if(packet instanceof ClientIntentionPacket intention){
            if(intention.protocolVersion()!=SharedConstants.getCurrentVersion().getProtocolVersion()||intention.intention()==ClientIntent.STATUS){close("Use Minecraft 1.21.4 direct connect");return;}
            lifecycle.handshake();protocol=ConnectionProtocol.LOGIN;
            connection.setupOutboundProtocol(LoginProtocols.CLIENTBOUND);
            connection.setupInboundProtocol(LoginProtocols.SERVERBOUND,(ServerLoginPacketListener)listener);
        }else if(packet instanceof ServerboundHelloPacket){
            lifecycle.hello();connection.send(new ClientboundLoginFinishedPacket(host.profile()));
        }else if(packet instanceof ServerboundLoginAcknowledgedPacket){
            lifecycle.loginAcknowledged();protocol=ConnectionProtocol.CONFIGURATION;
            connection.setupOutboundProtocol(ConfigurationProtocols.CLIENTBOUND);
            connection.setupInboundProtocol(ConfigurationProtocols.SERVERBOUND,(ServerConfigurationPacketListener)listener);
            connection.send(new ClientboundUpdateEnabledFeaturesPacket(FeatureFlags.REGISTRY.toNames(host.features())));
            connection.send(new ClientboundSelectKnownPacks(List.of()));
        }else if(packet instanceof ServerboundSelectKnownPacks){
            lifecycle.packsSelected();
            // One read, so the registries sent here are the ones the play protocol binds below.
            registries=host.registries();
            RegistrySynchronization.packRegistries(registries.createSerializationContext(NbtOps.INSTANCE),registries,Set.of(),
                (key,entries)->connection.send(new ClientboundRegistryDataPacket(key,entries)));
            connection.send(RegistryTags.packet(registries));
            connection.send(ClientboundFinishConfigurationPacket.INSTANCE);
        }else if(packet instanceof ServerboundFinishConfigurationPacket){
            lifecycle.configurationFinished();protocol=ConnectionProtocol.PLAY;
            connection.setupOutboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries)));
            connection.setupInboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries)),(ServerGamePacketListener)listener);
            host.snapshot(this);
        }else if(packet instanceof ServerboundPlayerLoadedPacket){
            host.minecraft().execute(()->{if(!closed){lifecycle.playerLoaded();host.ready(this);}});
        }else if(packet instanceof ServerboundKeepAlivePacket){
            // Private frontend terminates its own liveness; never forward to SAO.
        }else if(protocol==ConnectionProtocol.PLAY&&lifecycle.active())host.input(this,packet);
    }
    private net.minecraft.core.RegistryAccess.Frozen registries;
    void send(Packet<?> packet){if(closed)return;sendFrames(host.codec().freeze(packet));}
    void sendFrames(List<byte[]> frames){
        if(closed)return;
        for(byte[] frame:frames){offeredFrames.incrementAndGet();offeredBytes.addAndGet(frame.length);}
        // The outbox and the writes it hands out stay on the viewer channel's loop, so a frame released by a finished write never overtakes one offered earlier.
        if(!channel.eventLoop().inEventLoop()){channel.eventLoop().execute(()->offer(frames));return;}
        offer(frames);
    }
    private void offer(List<byte[]> frames){
        for(byte[] frame:frames){
            if(closed)return;
            List<byte[]> now=outbox.offer(frame);
            if(now==null){close("Relay viewer is too slow; reconnect");return;}
            write(now);
        }
    }
    private void write(List<byte[]> frames){
        for(byte[] frame:frames){
            if(closed)return;
            try{channel.writeAndFlush(Unpooled.wrappedBuffer(frame)).addListener(future->written(frame.length));}
            catch(RuntimeException failure){close("Relay write failed");return;}
        }
    }
    private void written(int bytes){List<byte[]> next=outbox.written(bytes);if(!closed)write(next);}
    void tick(){
        if(closed)return;
        connection.tick();
        long now=System.nanoTime();
        if(!lifecycle.active()&&now-created>120_000_000_000L){close("Relay loading timed out");return;}
        if(protocol==ConnectionProtocol.PLAY&&now-lastKeepalive>10_000_000_000L){lastKeepalive=now;connection.send(new ClientboundKeepAlivePacket(++keepalive));}
    }
    void close(String reason){
        if(closed)return;closed=true;live=false;lifecycle.close();host.remove(this);
        // Before clear, so the line shows what was waiting. A viewer closed outside PLAY or with a full socket gets no reason, only this line (2026-11-16).
        LOG.info("Relay viewer closed: {} (in flight {} frames / {} bytes, queued {} frames / {} bytes)",reason,outbox.inFlightFrames(),outbox.inFlightBytes(),outbox.queuedFrames(),outbox.queuedBytes());
        outbox.clear();
        // Never wait on a viewer socket from the original server's event loop.
        if(protocol==ConnectionProtocol.PLAY&&channel.isActive()&&channel.isWritable()){
            connection.send(new ClientboundDisconnectPacket(Component.literal(reason)),PacketSendListener.thenRun(()->channel.close()));
        }else channel.close();
    }
}
