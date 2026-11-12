package tw.handoff.relay.core;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
/** Decides whether a joining viewer may take control. Game-thread confined. */
public final class JoinGate {
 public static final long MAX_RTT_NANOS=1_500_000_000L;
 private final String nonce;private final Set<String> hostMissing=new LinkedHashSet<>();
 private boolean acked;private long rttNanos=-1;private String viewerFailed;
 public JoinGate(){byte[] b=new byte[16];new SecureRandom().nextBytes(b);nonce=HexFormat.of().formatHex(b);}
 public String nonce(){return nonce;}
 public void pong(String n,long rtt){if(nonce.equals(n)&&rtt>=0)rttNanos=rtt;}
 public void hostMissing(Set<String> pieces){hostMissing.clear();hostMissing.addAll(pieces);}
 public void viewerAck(){acked=true;}
 public void viewerFailed(String piece){if(viewerFailed==null)viewerFailed=piece;}
 public boolean mayGrant(){return blocker()==null;}
 public String blocker(){
  if(viewerFailed!=null)return viewerFailed;
  if(!hostMissing.isEmpty())return hostMissing.iterator().next();
  if(rttNanos<0||rttNanos>MAX_RTT_NANOS)return "latency";
  if(!acked)return "pending";
  return null;
 }
}
