package tw.handoff.service;
import java.net.*;
import java.io.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.*;
import javax.net.ssl.*;
final class HostTunnel implements AutoCloseable {
 private final SSLServerSocket server;private final int backend;private final byte[] token;private final Semaphore slots=new Semaphore(8);private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();private volatile boolean closed;
 HostTunnel(SSLContext tls,InetAddress bind,int port,int backend,String token)throws IOException{
  this.backend=backend;this.token=token.getBytes(StandardCharsets.US_ASCII);
  server=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket();server.setReuseAddress(true);server.bind(new InetSocketAddress(bind,port),8);server.setEnabledProtocols(new String[]{"TLSv1.3","TLSv1.2"});
 }
 int port(){return server.getLocalPort();}
 void start(){Thread.startVirtualThread(()->{while(!closed)try{SSLSocket socket=(SSLSocket)server.accept();if(!slots.tryAcquire()){socket.close();continue;}sockets.add(socket);Thread.startVirtualThread(()->serve(socket));}catch(IOException stopped){if(!closed)close();}});}
 private void serve(SSLSocket socket){try(socket){
  socket.setSoTimeout(10000);socket.setTcpNoDelay(true);socket.startHandshake();
  DataInputStream input=new DataInputStream(socket.getInputStream());byte[] supplied=input.readNBytes(43);
  if(supplied.length!=43||!MessageDigest.isEqual(supplied,token))return;
  Socket local=new Socket();try(local){local.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),backend),5000);local.setTcpNoDelay(true);sockets.add(local);socket.getOutputStream().write(1);socket.getOutputStream().flush();socket.setSoTimeout(0);SocketPump.bridge(socket,local);}finally{sockets.remove(local);}
 }catch(IOException invalid){}finally{sockets.remove(socket);slots.release();}}
 public void close(){closed=true;try{server.close();}catch(IOException ignored){}for(Socket s:sockets)SocketPump.close(s);}
}
