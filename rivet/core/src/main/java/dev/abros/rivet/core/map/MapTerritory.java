package dev.abros.rivet.core.map;

import java.util.*;

/** Map annotation geometry, not a block-protection claim. Vertices lie on block boundaries. */
public record MapTerritory(UUID id,String dimension,String name,int color,List<Point> points) {
 public record Point(int x,int z) {
  public Point {if(Math.abs((long)x)>30_000_000||Math.abs((long)z)>30_000_000)throw new IllegalArgumentException("Invalid vertex");}
 }
 public MapTerritory {
  Objects.requireNonNull(id);Objects.requireNonNull(dimension);Objects.requireNonNull(name);
  name=name.strip();points=List.copyOf(points);
  if(!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||dimension.length()>256||name.isEmpty()||name.length()>100||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid territory");
  if(points.size()<3||points.size()>128||new HashSet<>(points).size()!=points.size())throw new IllegalArgumentException("Invalid polygon");
  long area=0;int n=points.size();
  for(int i=0;i<n;i++){
   Point a=points.get(i),b=points.get((i+1)%n),previous=points.get((i+n-1)%n);
   if(cross(previous,a,b)==0&&((long)(previous.x-a.x)*(b.x-a.x)+(long)(previous.z-a.z)*(b.z-a.z))>0)throw new IllegalArgumentException("Overlapping edges");
   area+=(long)a.x*b.z-(long)b.x*a.z;
   for(int j=i+1;j<n;j++)if(j!=i+1&&!(i==0&&j==n-1)&&intersects(a,b,points.get(j),points.get((j+1)%n)))throw new IllegalArgumentException("Intersecting edges");
  }
  if(area==0)throw new IllegalArgumentException("Empty polygon");color=0xff000000|(color&0xffffff);
 }
 private static long cross(Point a,Point b,Point c){return (long)(b.x-a.x)*(c.z-a.z)-(long)(b.z-a.z)*(c.x-a.x);}
 private static boolean on(Point a,Point b,Point p){return cross(a,b,p)==0&&p.x>=Math.min(a.x,b.x)&&p.x<=Math.max(a.x,b.x)&&p.z>=Math.min(a.z,b.z)&&p.z<=Math.max(a.z,b.z);}
 private static boolean intersects(Point a,Point b,Point c,Point d){long u=cross(a,b,c),v=cross(a,b,d),s=cross(c,d,a),t=cross(c,d,b);return (Long.signum(u)*Long.signum(v)<0&&Long.signum(s)*Long.signum(t)<0)||on(a,b,c)||on(a,b,d)||on(c,d,a)||on(c,d,b);}
 public boolean contains(double x,double z){
  boolean inside=false;
  for(int i=0,j=points.size()-1;i<points.size();j=i++){
   var a=points.get(j);var b=points.get(i);
   double cross=(b.x-a.x)*(z-a.z)-(b.z-a.z)*(x-a.x);
   if(Math.abs(cross)<1e-7&&x>=Math.min(a.x,b.x)&&x<=Math.max(a.x,b.x)&&z>=Math.min(a.z,b.z)&&z<=Math.max(a.z,b.z))return true;
   if((a.z>z)!=(b.z>z)&&x<(double)(b.x-a.x)*(z-a.z)/(b.z-a.z)+a.x)inside=!inside;
  }return inside;
 }
}
