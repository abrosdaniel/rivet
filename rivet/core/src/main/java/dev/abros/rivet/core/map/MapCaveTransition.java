package dev.abros.rivet.core.map;

/** Minimum interval between surface/cave switches. Height tracking has no delay. */
public final class MapCaveTransition {
 private int current=MapLayer.SURFACE;private long lastToggle;private boolean toggled;
 public int current(){return current;}
 public void reset(){current=MapLayer.SURFACE;lastToggle=0;toggled=false;}
 public int update(int target,long now){return update(target,now,1000);}
 public int update(int target,long now,long delay){
  boolean changing=(target==MapLayer.SURFACE)!=(current==MapLayer.SURFACE);
  if(!changing||!toggled||now-lastToggle>=delay){if(changing){lastToggle=now;toggled=true;}current=target;}
  return current;
 }
}
