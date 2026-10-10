package dev.abros.rivet.core.map;

/** Shared inverse transforms for terrain sampling, markers and cursor hit testing. */
public record MinimapProjection(double x,double z,double zoom,double yaw,boolean rotate) {
 public MinimapProjection {if(!Double.isFinite(x)||!Double.isFinite(z)||!Double.isFinite(yaw)||!Double.isFinite(zoom)||zoom<=0)throw new IllegalArgumentException("Invalid minimap projection");}
 private double angle(){return rotate?Math.toRadians(-yaw-180):0;}
 public double screenX(double wx,double wz){double dx=(wx-x)*zoom,dz=(wz-z)*zoom,a=angle();return dx*Math.cos(a)-dz*Math.sin(a);}
 public double screenY(double wx,double wz){double dx=(wx-x)*zoom,dz=(wz-z)*zoom,a=angle();return dx*Math.sin(a)+dz*Math.cos(a);}
 public double worldX(double sx,double sy){double a=angle();return x+(sx*Math.cos(a)+sy*Math.sin(a))/zoom;}
 public double worldZ(double sx,double sy){double a=angle();return z+(-sx*Math.sin(a)+sy*Math.cos(a))/zoom;}
 /** Pins outside the viewport retain their bearing at its inset boundary. */
 public static double[] pin(double x,double y,double radius,boolean round){
  double distance=round?Math.hypot(x,y):Math.max(Math.abs(x),Math.abs(y));
  double factor=distance>radius?radius/distance:1;
  return new double[]{x*factor,y*factor};
 }
 /** Project a compass bearing onto the inset perimeter, including square corners. */
 public static double[] compass(double x,double y,double radius,boolean round){
  double length=round?Math.hypot(x,y):Math.max(Math.abs(x),Math.abs(y));
  return length==0?new double[]{0,0}:new double[]{x*radius/length,y*radius/length};
 }
 public static boolean contains(double x,double y,double radius,boolean round){return round?x*x+y*y<=radius*radius:Math.abs(x)<=radius&&Math.abs(y)<=radius;}
}
