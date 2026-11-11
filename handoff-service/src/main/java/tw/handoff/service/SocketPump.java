package tw.handoff.service;
import java.net.Socket;
import java.io.IOException;
final class SocketPump {
 static void close(Socket s){try{s.close();}catch(IOException ignored){}}
 static void bridge(Socket left,Socket right){
  Thread.startVirtualThread(()->copy(left,right));copy(right,left);
 }
 private static void copy(Socket from,Socket to){try{byte[] bytes=new byte[32*1024];int n;var in=from.getInputStream();var out=to.getOutputStream();while((n=in.read(bytes))!=-1){out.write(bytes,0,n);}}catch(IOException ignored){}finally{close(from);close(to);}}
}
