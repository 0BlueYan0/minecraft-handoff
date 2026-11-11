package tw.handoff.relay.core;
import java.util.*;
public final class PacketJournal {
 private final int maxFrames,maxBytes;private final List<byte[]> frames=new ArrayList<>();private int bytes;private boolean invalid;
 public PacketJournal(int maxFrames,int maxBytes){if(maxFrames<1||maxBytes<1)throw new IllegalArgumentException();this.maxFrames=maxFrames;this.maxBytes=maxBytes;}
 public synchronized boolean append(byte[] frame){if(invalid)return false;if(frames.size()>=maxFrames||frame.length>maxBytes-bytes){invalid=true;frames.clear();bytes=0;return false;}frames.add(frame.clone());bytes+=frame.length;return true;}
 public synchronized List<byte[]> snapshot(){if(invalid)throw new IllegalStateException("Payload history exceeded catch-up budget");return frames.stream().map(byte[]::clone).toList();}
}
