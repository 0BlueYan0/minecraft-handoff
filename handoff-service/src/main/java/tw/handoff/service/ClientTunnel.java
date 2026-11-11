package tw.handoff.service;
import java.net.*;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
final class ClientTunnel implements AutoCloseable {
 private final ServerSocket local;private final Pairing pairing;private volatile boolean closed;private final Set<Socket>sockets=ConcurrentHashMap.newKeySet();private final Semaphore slots=new Semaphore(2);
 ClientTunnel(Pairing pair)throws IOException{pairing=pair;local=new ServerSocket(0,2,InetAddress.getLoopbackAddress());}
 int port(){return local.getLocalPort();}
 void start(){Thread.startVirtualThread(()->{while(!closed)try{Socket socket=local.accept();if(!slots.tryAcquire()){socket.close();continue;}sockets.add(socket);Thread.startVirtualThread(()->{try(socket;var remote=connect(pairing)){sockets.add(remote);socket.setTcpNoDelay(true);try{SocketPump.bridge(socket,remote);}finally{sockets.remove(remote);}}catch(IOException failure){}finally{sockets.remove(socket);slots.release();}});}catch(IOException stopped){if(!closed)close();}});}
 static SSLSocket connect(Pairing pair)throws IOException{
  SSLSocket socket=null;try{
   X509TrustManager pinned=new X509TrustManager(){public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}public void checkClientTrusted(X509Certificate[] c,String a)throws CertificateException{throw new CertificateException("Client certificate not used");}
    public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException{try{if(chain==null||chain.length==0)throw new CertificateException();chain[0].checkValidity();byte[] actual=MessageDigest.getInstance("SHA-256").digest(chain[0].getEncoded());if(!MessageDigest.isEqual(actual,HexFormat.of().parseHex(pair.fingerprint())))throw new CertificateException("Host certificate does not match the pairing file");}catch(GeneralSecurityException error){throw new CertificateException("Host identity rejected",error);}}
   };
   SSLContext ctx=SSLContext.getInstance("TLS");ctx.init(null,new TrustManager[]{pinned},new SecureRandom());socket=(SSLSocket)ctx.getSocketFactory().createSocket();socket.setEnabledProtocols(new String[]{"TLSv1.3","TLSv1.2"});socket.connect(new InetSocketAddress(pair.host(),pair.port()),10000);socket.setSoTimeout(10000);socket.setTcpNoDelay(true);socket.startHandshake();socket.getOutputStream().write(pair.token().getBytes(java.nio.charset.StandardCharsets.US_ASCII));socket.getOutputStream().flush();if(socket.getInputStream().read()!=1)throw new IOException("Host pairing rejected");socket.setSoTimeout(0);return socket;
  }catch(GeneralSecurityException failure){if(socket!=null)SocketPump.close(socket);throw new IOException("TLS setup failed",failure);}catch(IOException|RuntimeException failure){if(socket!=null)SocketPump.close(socket);throw failure;}
 }
 public void close(){closed=true;try{local.close();}catch(IOException ignored){}for(Socket s:sockets)SocketPump.close(s);}
}
