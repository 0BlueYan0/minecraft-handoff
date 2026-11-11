package tw.handoff.service;
import java.nio.file.*;
import java.net.*;
import java.io.*;
import java.util.*;
import javax.net.ssl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class TunnelTest {
 @TempDir Path temp;
 @Test void pinnedAuthenticatedTunnelCarriesBytesAndBadTokenNeverReachesBackend()throws Exception{
  var identity=TlsIdentity.open(temp);
  try(var backend=new ServerSocket(0,4,InetAddress.getLoopbackAddress());var host=new HostTunnel(identity.context(),InetAddress.getLoopbackAddress(),0,backend.getLocalPort(),"a".repeat(43))){
   backend.setSoTimeout(1000);host.start();
   var pair=new Pairing(2,"127.0.0.1",host.port(),identity.fingerprint(),"a".repeat(43),"HandoffHost",UUID.randomUUID().toString());
   try(var socket=ClientTunnel.connect(pair)){try(var target=backend.accept()){socket.getOutputStream().write(new byte[]{3,4,5});assertArrayEquals(new byte[]{3,4,5},target.getInputStream().readNBytes(3));target.getOutputStream().write(9);assertEquals(9,socket.getInputStream().read());}}
   var bad=new Pairing(2,pair.host(),pair.port(),pair.fingerprint(),"b".repeat(43),pair.name(),pair.uuid());
   assertThrows(IOException.class,()->{try(var ignored=ClientTunnel.connect(bad)) {}});
   assertThrows(SocketTimeoutException.class,backend::accept);
  }
 }
 @Test void wrongCertificatePinFailsBeforePairingTokenIsSent()throws Exception{
  var identity=TlsIdentity.open(temp);
  try(var host=new HostTunnel(identity.context(),InetAddress.getLoopbackAddress(),0,1,"a".repeat(43))){host.start();
   var pair=new Pairing(2,"127.0.0.1",host.port(),"0".repeat(64),"a".repeat(43),"HandoffHost",UUID.randomUUID().toString());
   assertThrows(IOException.class,()->{try(var ignored=ClientTunnel.connect(pair)) {}});
  }
 }
 @Test void pairingIsValidatedAndRedacted(){assertThrows(IllegalArgumentException.class,()->new Pairing(2,"https://bad",1,"0".repeat(64),"a".repeat(43),"X",UUID.randomUUID().toString()));var p=new Pairing(2,"localhost",1,"0".repeat(64),"a".repeat(43),"X",UUID.randomUUID().toString());assertFalse(p.toString().contains(p.token()));}
}
