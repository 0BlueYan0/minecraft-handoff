package tw.handoff.sao;
public final class CooldownAge {
 public static double remaining(double seconds,long elapsedNanos){if(!Double.isFinite(seconds)||seconds<0||elapsedNanos<0)throw new IllegalArgumentException("Invalid cooldown age");return Math.max(0,seconds-elapsedNanos/1_000_000_000.0);}
 private CooldownAge(){}
}
