package tw.handoff.relay.bridge;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.net.InetAddress;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;

final class NativeFrontend implements AutoCloseable {
    private final Channel server;
    NativeFrontend(HostBridge host,int port)throws Exception{
        server=new ServerBootstrap().group(Connection.NETWORK_WORKER_GROUP.get()).channel(NioServerSocketChannel.class)
            .childOption(ChannelOption.TCP_NODELAY,true)
            .childHandler(new ChannelInitializer<Channel>(){
                protected void initChannel(Channel channel){
                    channel.pipeline().addLast("timeout",new ReadTimeoutHandler(60));
                    Connection.configureSerialization(channel.pipeline(),PacketFlow.SERVERBOUND,false,null);
                    Connection connection=new Connection(PacketFlow.SERVERBOUND);
                    connection.configurePacketHandler(channel.pipeline());
                    NativePeer peer=new NativePeer(host,connection,channel);
                    connection.setListenerForServerboundHandshake(peer.handshakeListener());
                    host.add(peer);
                }
            }).bind(InetAddress.getLoopbackAddress(),port).syncUninterruptibly().channel();
    }
    int port(){return ((java.net.InetSocketAddress)server.localAddress()).getPort();}
    /** Waits until the listening socket is closed, so a stopped bridge no longer accepts viewers when this returns. */
    public void close(){if(!server.close().awaitUninterruptibly(5000))throw new IllegalStateException("Frontend close timed out");}
}
