package dev.abros.rivet.core;

/** Server deadline translated once to the client's monotonic clock. */
public final class RestartCountdown {
 private boolean active;private long deadline;
 public void update(long restartAt,long serverNow,long monotonicNow){active=restartAt>0;deadline=monotonicNow+Math.clamp(restartAt-serverNow,0,86_400_000L);}
 public long seconds(long monotonicNow){return active?Math.max(0,(deadline-monotonicNow+999)/1000):-1;}
 public void clear(){active=false;deadline=0;}
}
