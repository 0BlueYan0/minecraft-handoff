package tw.handoff.relay.core;
public final class PendingBudget {
    private final int maxPackets;private final long maxBytes;
    private int packets;private long pending;
    public PendingBudget(int packets,long bytes){if(packets<1||bytes<1)throw new IllegalArgumentException("Invalid relay budget");maxPackets=packets;maxBytes=bytes;}
    public synchronized boolean reserve(int bytes){
        if(bytes<0)throw new IllegalArgumentException("Negative frame size");
        if(packets>=maxPackets||bytes>maxBytes-pending)return false;
        packets++;pending+=bytes;return true;
    }
    public synchronized void release(int bytes){if(bytes<0||packets<1||bytes>pending)throw new IllegalStateException("Relay budget underflow");packets--;pending-=bytes;}
    public synchronized long bytes(){return pending;}
}
