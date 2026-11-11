package tw.handoff.service;
import java.util.UUID;
public record Pairing(int version,String host,int port,String fingerprint,String token,String name,String uuid){
 public Pairing{
  if(version!=2||host==null||!host.matches("[A-Za-z0-9_.:\\-]{1,253}")||port<1||port>65535||fingerprint==null||!fingerprint.matches("[0-9a-f]{64}")||token==null||!token.matches("[A-Za-z0-9_-]{43}")||name==null||!name.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException("Invalid pairing file");
  UUID.fromString(uuid);
 }
 public String toString(){return "Pairing[credentials redacted]";}
}
