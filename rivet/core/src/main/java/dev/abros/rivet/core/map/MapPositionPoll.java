package dev.abros.rivet.core.map;

import java.util.UUID;

/** Spreads initial polls and bounded retries without delaying normal one-second refreshes. */
public final class MapPositionPoll {
 private int failures;
 public static long initialDelay(UUID player){return jitter(player,0);}
 public long failed(UUID player){int attempt=failures;failures=Math.min(3,failures+1);return (1000L<<Math.min(2,attempt))+jitter(player,attempt+1);}
 public void reset(){failures=0;}
 private static long jitter(UUID player,int attempt){
  long value=player.getMostSignificantBits()^Long.rotateLeft(player.getLeastSignificantBits(),23)^((long)attempt*0x9e3779b97f4a7c15L);
  value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;value=(value^(value>>>27))*0x94d049bb133111ebL;
  return Math.floorMod(value^(value>>>31),1000L);
 }
}
