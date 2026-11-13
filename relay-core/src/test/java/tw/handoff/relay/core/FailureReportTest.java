package tw.handoff.relay.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import tw.handoff.relay.core.FailureReport.Kind;

class FailureReportTest {
    private static final long MINUTE=FailureReport.LINE_INTERVAL_NANOS;

    @Test void keepsSafeMessage(){
        assertEquals("IllegalArgumentException: Unregistered id 12",FailureReport.reason(new IllegalArgumentException("Unregistered id 12")));
    }
    @Test void nullOrBlankMessageLeavesClassName(){
        assertEquals("IllegalStateException",FailureReport.reason(new IllegalStateException()));
        assertEquals("IllegalStateException",FailureReport.reason(new IllegalStateException("  ")));
    }
    @Test void unsafeCharactersLeaveClassName(){
        assertEquals("IllegalArgumentException",FailureReport.reason(new IllegalArgumentException("token=abc\nline2")));
        assertEquals("IllegalArgumentException",FailureReport.reason(new IllegalArgumentException("--accessToken=x")));
        assertEquals("IllegalArgumentException",FailureReport.reason(new IllegalArgumentException("C:\\Users\\x;rm")));
    }
    @Test void tooLongMessageLeavesClassName(){
        assertEquals("IllegalArgumentException",FailureReport.reason(new IllegalArgumentException("a".repeat(FailureReport.MAX_DETAIL+1))));
        assertTrue(FailureReport.reason(new IllegalArgumentException("a".repeat(FailureReport.MAX_DETAIL))).endsWith("a".repeat(FailureReport.MAX_DETAIL)));
    }
    @Test void firstIsFullThenSkippedInsideInterval(){
        AtomicLong clock=new AtomicLong();
        FailureReport report=new FailureReport(clock::get,FailureReport.MAX_KEYS,MINUTE);
        assertEquals(Kind.FULL,report.decide("tick",make()).kind());
        assertEquals(Kind.SKIP,report.decide("tick",make()).kind());
        assertEquals(Kind.SKIP,report.decide("tick",make()).kind());
        assertEquals(Kind.FULL,report.decide("snapshot",make()).kind());
        assertEquals(Kind.FULL,report.decide("tick",new IllegalStateException("other")).kind());
    }
    @Test void lineAfterIntervalCarriesSkippedCountThenRestarts(){
        AtomicLong clock=new AtomicLong();
        FailureReport report=new FailureReport(clock::get,FailureReport.MAX_KEYS,MINUTE);
        report.decide("tick",make());
        report.decide("tick",make());
        report.decide("tick",make());
        clock.set(MINUTE-1);
        assertEquals(Kind.SKIP,report.decide("tick",make()).kind());
        clock.set(MINUTE);
        FailureReport.Outcome line=report.decide("tick",make());
        assertEquals(Kind.LINE,line.kind());
        assertEquals(3,line.suppressed());
        assertEquals(Kind.SKIP,report.decide("tick",make()).kind());
        clock.set(2*MINUTE);
        assertEquals(1,report.decide("tick",make()).suppressed());
    }
    @Test void rememberedShapesAreCapped(){
        FailureReport report=new FailureReport(()->0,3,MINUTE);
        for(int i=0;i<3;i++)assertEquals(Kind.FULL,report.decide("label"+i,make()).kind());
        for(int i=3;i<50;i++)assertNotEquals(Kind.FULL,report.decide("label"+i,make()).kind());
        assertTrue(report.size()<=4);
        assertEquals(Kind.SKIP,report.decide("label0",make()).kind());
    }
    private static RuntimeException make(){return new IllegalArgumentException("x");}
}
