package tw.handoff.relay.core;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PacedOutboxTest {
    private static byte[] frame(int id,int size){byte[] f=new byte[size];f[0]=(byte)id;return f;}

    /** Drives the outbox like a socket that finishes one write at a time; returns the ids in write order. */
    private static List<Integer> drain(PacedOutbox box,List<byte[]> first){
        var order=new ArrayList<Integer>();var pending=new ArrayList<byte[]>(first);
        while(!pending.isEmpty()){byte[] done=pending.removeFirst();order.add((int)done[0]);pending.addAll(box.written(done.length));}
        return order;
    }

    /** The 2026-11-16 join: more bytes than the in-flight budget used to drop the viewer; now the rest waits and goes out in order. */
    @Test void byteStarvedSnapshotWaitsInsteadOfFailing(){
        var box=new PacedOutbox(100,100,1000,10_000);
        var written=new ArrayList<byte[]>();
        for(int i=0;i<30;i++){var now=box.offer(frame(i,40));assertNotNull(now,"frame "+i);written.addAll(now);}
        assertEquals(2,written.size());assertEquals(28,box.queuedFrames());assertEquals(80,box.inFlightBytes());
        var order=drain(box,written);
        for(int i=0;i<30;i++)assertEquals(i,order.get(i));
        assertEquals(0,box.queuedFrames());assertEquals(0,box.inFlightBytes());assertEquals(0,box.inFlightFrames());
    }

    /** Same with many small frames (chunks plus entities) against the frame-count limit. */
    @Test void countStarvedSnapshotWaitsInsteadOfFailing(){
        var box=new PacedOutbox(3,1_000_000,1000,1_000_000);
        var written=new ArrayList<byte[]>();
        for(int i=0;i<50;i++){var now=box.offer(frame(i,2));assertNotNull(now,"frame "+i);written.addAll(now);}
        assertEquals(3,written.size());assertEquals(3,box.inFlightFrames());assertEquals(47,box.queuedFrames());
        var order=drain(box,written);
        for(int i=0;i<50;i++)assertEquals(i,order.get(i));
    }

    /** A small frame must not overtake queued ones even when it would fit in flight. */
    @Test void laterSmallFrameWaitsBehindQueue(){
        var box=new PacedOutbox(10,100,10,1000);
        assertEquals(1,box.offer(frame(0,90)).size());
        assertTrue(box.offer(frame(1,50)).isEmpty());
        assertTrue(box.offer(frame(2,5)).isEmpty(),"fits in flight but must wait behind frame 1");
        assertEquals(List.of(1,2),box.written(90).stream().map(f->(int)f[0]).toList());
    }

    @Test void fullQueueStillDropsAViewerThatStopsReading(){
        var box=new PacedOutbox(1,10,2,100);
        assertNotNull(box.offer(frame(0,10)));
        assertNotNull(box.offer(frame(1,10)));assertNotNull(box.offer(frame(2,10)));
        assertNull(box.offer(frame(3,10)),"frame cap");
        var bytes=new PacedOutbox(1,60,10,100);
        assertNotNull(bytes.offer(frame(0,60)));assertNotNull(bytes.offer(frame(1,60)));
        assertNull(bytes.offer(frame(2,60)),"byte cap");
    }

    @Test void frameLargerThanFlightBudgetIsRefusedNotStuck(){
        var box=new PacedOutbox(10,100,10,1000);
        assertNull(box.offer(frame(0,101)));
        assertEquals(0,box.queuedFrames());
    }

    @Test void clearDropsWaitingFramesButKeepsFlightAccounting(){
        var box=new PacedOutbox(1,100,10,1000);
        assertEquals(1,box.offer(frame(0,10)).size());box.offer(frame(1,10));box.offer(frame(2,10));
        box.clear();
        assertEquals(0,box.queuedFrames());assertEquals(0,box.queuedBytes());
        assertTrue(box.written(10).isEmpty());assertEquals(0,box.inFlightFrames());
    }
}
