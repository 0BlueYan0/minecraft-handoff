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
import tw.handoff.relay.core.PendingBudget;

final class NativePeer implements InvocationHandler {
    final String id=UUID.randomUUID().toString();
    final HostBridge host;final Connection connection;final Channel channel;
    final PeerLifecycle lifecycle=new PeerLifecycle();
    final PendingBudget budget=new PendingBudget(2048,32L*1024*1024);
    final Object listener;
    volatile ConnectionProtocol protocol=ConnectionProtocol.HANDSHAKING;
    volatile boolean live,closed;
    long created=System.nanoTime(),lastKeepalive=created,keepalive;
    NativePeer(HostBridge host,Connection connection,Channel channel){
        this.host=host;this.connection=connection;this.channel=channel;
        listener=Proxy.newProxyInstance(ServerGamePacketListener.class.getClassLoader(),new Class<?>[]{ServerHandshakePacketListener.class,ServerLoginPacketListener.class,ServerConfigurationPacketListener.class,ServerGamePacketListener.class},this);
    }
    ServerHandshakePacketListener handshakeListener(){return (ServerHandshakePacketListener)listener;}
    public Object invoke(Object proxy,Method method,Object[] args){
        String name=method.getName();
        if(method.getDeclaringClass()==Object.class)return switch(name){case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->"HandoffPrivatePeer";};
        if(name.equals("flow"))return PacketFlow.SERVERBOUND;
        if(name.equals("protocol"))return protocol;
        if(name.equals("isAcceptingMessages")||name.equals("shouldHandleMessage"))return !closed&&connection.isConnected();
        if(name.equals("createDisconnectionInfo"))return new DisconnectionDetails(Component.literal("Relay connection closed"));
        if(name.equals("onDisconnect")){closed=true;lifecycle.close();host.remove(this);return null;}
        if(name.equals("onPacketError")){close("Invalid relay packet");return null;}
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
            RegistrySynchronization.packRegistries(host.registries().createSerializationContext(NbtOps.INSTANCE),host.registries(),Set.of(),
                (key,entries)->connection.send(new ClientboundRegistryDataPacket(key,entries)));
            connection.send(RegistryTags.packet(host.registries()));
            connection.send(ClientboundFinishConfigurationPacket.INSTANCE);
        }else if(packet instanceof ServerboundFinishConfigurationPacket){
            lifecycle.configurationFinished();protocol=ConnectionProtocol.PLAY;
            connection.setupOutboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(host.registries())));
            connection.setupInboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(host.registries())),(ServerGamePacketListener)listener);
            host.snapshot(this);
        }else if(packet instanceof ServerboundPlayerLoadedPacket){
            host.minecraft().execute(()->{if(!closed){lifecycle.playerLoaded();host.ready(this);}});
        }else if(packet instanceof ServerboundKeepAlivePacket){
            // Private frontend terminates its own liveness; never forward to SAO.
        }else if(protocol==ConnectionProtocol.PLAY&&lifecycle.active())host.input(this,packet);
    }
    void send(Packet<?> packet){if(closed)return;sendFrames(host.codec.freeze(packet));}
    void sendFrames(List<byte[]> frames){
        if(closed)return;
        for(byte[] frame:frames){
            if(!budget.reserve(frame.length)){close("Relay viewer is too slow; reconnect");return;}
            try{channel.writeAndFlush(Unpooled.wrappedBuffer(frame)).addListener(future->budget.release(frame.length));}
            catch(RuntimeException failure){budget.release(frame.length);close("Relay write failed");return;}
        }
    }
    void tick(){
        if(closed)return;
        connection.tick();
        long now=System.nanoTime();
        if(!lifecycle.active()&&now-created>120_000_000_000L){close("Relay loading timed out");return;}
        if(protocol==ConnectionProtocol.PLAY&&now-lastKeepalive>10_000_000_000L){lastKeepalive=now;connection.send(new ClientboundKeepAlivePacket(++keepalive));}
    }
    void close(String reason){
        if(closed)return;closed=true;live=false;lifecycle.close();host.remove(this);
        // Never wait on a viewer socket from the original server's event loop.
        if(protocol==ConnectionProtocol.PLAY&&channel.isActive()&&channel.isWritable()){
            connection.send(new ClientboundDisconnectPacket(Component.literal(reason)),PacketSendListener.thenRun(()->channel.close()));
        }else channel.close();
    }
}
