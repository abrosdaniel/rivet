package dev.abros.rivet.core.map;
/** A death transition creates one point; the same position can have later deaths. */
public final class MapDeathTracker {
 private boolean dead;
 public boolean observe(boolean dying){boolean record=dying&&!dead;dead=dying;return record;}
 public void reset(){dead=false;}
}
