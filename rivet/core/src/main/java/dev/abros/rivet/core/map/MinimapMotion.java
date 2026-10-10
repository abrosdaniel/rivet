package dev.abros.rivet.core.map;

/** Frame-rate independent, bounded speed response without changing the saved zoom. */
public final class MinimapMotion {
 private double zoom=Double.NaN;private long last;
 public double update(double base,double speed,long now,boolean animate){
  double acceleration=Math.clamp((speed-.215)/.3,0,1);
  double target=base/(1+.3*acceleration);
  if(!Double.isFinite(zoom)||now<last||now-last>1000||!animate)zoom=target;
  else zoom+=(target-zoom)*(1-Math.pow(.8,Math.clamp(now-last,0,100)*.06));
  last=now;return zoom;
 }
 public void reset(){zoom=Double.NaN;last=0;}
}
