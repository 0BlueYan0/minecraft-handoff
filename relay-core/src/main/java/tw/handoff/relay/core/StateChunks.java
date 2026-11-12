package tw.handoff.relay.core;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
/** Splits a snapshot document into control messages and rebuilds it with strict ordering and size limits. */
public final class StateChunks {
 public static List<String> encode(byte[] document){
  if(document==null)throw new IllegalArgumentException("Invalid document");
  String b64=Base64.getEncoder().encodeToString(document);
  int total=Math.max(1,(b64.length()+ControlWire.MAX_CHUNK_CHARS-1)/ControlWire.MAX_CHUNK_CHARS);
  if(total>ControlWire.MAX_CHUNKS)throw new IllegalArgumentException("Document too large");
  var out=new ArrayList<String>(total);
  for(int i=0;i<total;i++)out.add(ControlWire.chunk(i,total,b64.substring(i*ControlWire.MAX_CHUNK_CHARS,Math.min(b64.length(),(i+1)*ControlWire.MAX_CHUNK_CHARS))).encode());
  return out;
 }
 /** Not thread safe. After a rejected message the instance must be discarded. */
 public static final class Reassembler {
  private final int maxBytes;private final List<byte[]> parts=new ArrayList<>();private int total=-1,bytes;
  public Reassembler(int maxBytes){if(maxBytes<=0)throw new IllegalArgumentException("Invalid limit");this.maxBytes=maxBytes;}
  public Optional<byte[]> accept(String message){
   var c=ControlWire.parseChunk(message);
   if(c.index()!=parts.size()||(total>=0&&c.total()!=total))throw new IllegalArgumentException("Unexpected snapshot chunk");
   if(c.index()<c.total()-1&&(c.data().length()!=ControlWire.MAX_CHUNK_CHARS||c.data().indexOf('=')>=0))throw new IllegalArgumentException("Invalid snapshot chunk");
   byte[] raw;
   try{raw=Base64.getDecoder().decode(c.data());}catch(IllegalArgumentException bad){throw new IllegalArgumentException("Invalid snapshot chunk");}
   if(raw.length>maxBytes-bytes)throw new IllegalArgumentException("Snapshot too large");
   total=c.total();bytes+=raw.length;parts.add(raw);
   if(parts.size()<total)return Optional.empty();
   byte[] all=new byte[bytes];int at=0;for(byte[] p:parts){System.arraycopy(p,0,all,at,p.length);at+=p.length;}
   return Optional.of(all);
  }
 }
 private StateChunks(){}
}
