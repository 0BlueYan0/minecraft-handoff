package tw.handoff.relay.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Frames for one viewer, kept in order. At most the in-flight budget is handed to the socket; the rest waits here and goes out as earlier writes finish.
 * 2026-11-16: a join snapshot of 265 chunks plus entities went straight to the socket, overran the in-flight budget (2048 frames, 32 MB),
 * and the viewer was dropped with a bare "連線中斷" on every retry. The queue has its own cap so a viewer that stops reading is still dropped.
 */
public final class PacedOutbox {
    private final PendingBudget inFlight;private final long flightBytes;
    private final int maxQueuedFrames;private final long maxQueuedBytes;
    private final ArrayDeque<byte[]> queue=new ArrayDeque<>();private long queuedBytes;

    public PacedOutbox(int flightFrames,long flightBytes,int queuedFrames,long queuedBytes){
        if(queuedFrames<1||queuedBytes<1)throw new IllegalArgumentException("Invalid relay queue");
        inFlight=new PendingBudget(flightFrames,flightBytes);this.flightBytes=flightBytes;maxQueuedFrames=queuedFrames;maxQueuedBytes=queuedBytes;
    }

    /** Frames to write now, oldest first; empty when the frame waits behind others; null when the queue is full or the frame can never fit in flight. */
    public synchronized List<byte[]> offer(byte[] frame){
        if(frame.length>flightBytes)return null;
        if(queue.isEmpty()&&inFlight.reserve(frame.length))return List.of(frame);
        if(queue.size()>=maxQueuedFrames||frame.length>maxQueuedBytes-queuedBytes)return null;
        queue.add(frame);queuedBytes+=frame.length;
        return List.of();
    }

    /** A write handed out by offer or written finished; returns the queued frames that now fit, oldest first. */
    public synchronized List<byte[]> written(int bytes){
        inFlight.release(bytes);
        var out=new ArrayList<byte[]>();
        while(!queue.isEmpty()&&inFlight.reserve(queue.peek().length)){byte[] frame=queue.poll();queuedBytes-=frame.length;out.add(frame);}
        return out;
    }

    /** Drops the waiting frames of a closed viewer; writes already in flight still report through written. */
    public synchronized void clear(){queue.clear();queuedBytes=0;}

    public synchronized int inFlightFrames(){return inFlight.packets();}
    public synchronized long inFlightBytes(){return inFlight.bytes();}
    public synchronized int queuedFrames(){return queue.size();}
    public synchronized long queuedBytes(){return queuedBytes;}
}
