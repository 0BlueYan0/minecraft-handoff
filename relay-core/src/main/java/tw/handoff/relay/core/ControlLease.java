package tw.handoff.relay.core;
import java.util.Objects;
/** Game-thread confined. Epoch and sequence prevent queued input surviving a handoff. */
public final class ControlLease<T> {
 private T owner;private long epoch,sequence;
 public long grant(T peer,boolean ready){if(!ready||peer==null)return 0;owner=peer;sequence=0;return ++epoch;}
 public void release(T peer){if(ownedBy(peer)){owner=null;sequence=0;++epoch;}}
 public boolean ownedBy(T peer){return owner!=null&&Objects.equals(owner,peer);}
 public boolean accept(T peer,long generation,long next){if(!ownedBy(peer)||generation!=epoch||next<=sequence||next<=0)return false;sequence=next;return true;}
 public long epoch(){return epoch;}
 public T owner(){return owner;}
}
