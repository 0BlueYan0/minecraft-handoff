package tw.handoff.relay.core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Builds a disconnect reason that is safe to show a viewer and decides how much of a repeated failure goes into the log. */
public final class FailureReport {
    public static final int MAX_DETAIL=200;
    public static final int MAX_KEYS=256;
    public static final long LINE_INTERVAL_NANOS=TimeUnit.SECONDS.toNanos(60);
    private static final String OVERFLOW="*overflow*";

    public enum Kind{
        /** First time this failure shape is seen: log the full stack trace. */
        FULL,
        /** Seen before and the interval has passed: log one line with the number of skipped repeats. */
        LINE,
        /** Seen before and inside the interval: log nothing. */
        SKIP
    }
    public record Outcome(Kind kind,int suppressed){}

    private final LongSupplier nanos;
    private final int maxKeys;
    private final long intervalNanos;
    private final Map<String,long[]> state=new HashMap<>(); // key -> {last logged nanos, skipped count}
    private int normalKeys;

    public FailureReport(){this(System::nanoTime,MAX_KEYS,LINE_INTERVAL_NANOS);}
    public FailureReport(LongSupplier nanos,int maxKeys,long intervalNanos){this.nanos=nanos;this.maxKeys=maxKeys;this.intervalNanos=intervalNanos;}

    /** Class name, plus the message when it is non-blank, at most MAX_DETAIL characters and made only of safe characters. Anything else leaves only the class name. */
    public static String reason(Throwable failure){
        String name=failure.getClass().getSimpleName();
        String message=failure.getMessage();
        if(message==null||message.isBlank()||message.length()>MAX_DETAIL||!message.matches("[A-Za-z0-9_.$#:, /<>\\[\\]-]+"))return name;
        return name+": "+message;
    }

    /**
     * FULL the first time a label and failure shape (class plus top stack frame) is seen. Later repeats are SKIP,
     * except one LINE per interval that carries the skipped count. At most maxKeys shapes are remembered, the rest
     * share one overflow entry that never returns FULL. The state holds at most maxKeys+1 entries.
     */
    public synchronized Outcome decide(String label,Throwable failure){
        StackTraceElement[] trace=failure.getStackTrace();
        String key=label+"|"+failure.getClass().getName()+"|"+(trace.length==0?"":trace[0].toString());
        long now=nanos.getAsLong();
        long[] entry=state.get(key);
        if(entry==null){
            if(normalKeys<maxKeys){state.put(key,new long[]{now,0});normalKeys++;return new Outcome(Kind.FULL,0);}
            key=OVERFLOW;entry=state.get(key);
            if(entry==null){state.put(key,new long[]{now,0});return new Outcome(Kind.LINE,0);}
        }
        if(now-entry[0]>=intervalNanos){int skipped=(int)entry[1];entry[0]=now;entry[1]=0;return new Outcome(Kind.LINE,skipped);}
        entry[1]++;
        return new Outcome(Kind.SKIP,0);
    }

    synchronized int size(){return state.size();}
}
