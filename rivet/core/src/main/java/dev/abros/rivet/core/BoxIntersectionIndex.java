package dev.abros.rivet.core;

import java.util.*;
import dev.abros.rivet.core.NativeLayout.Box;

/** Per-layout spatial lookup preserving the strict rectangle overlap predicate. */
public final class BoxIntersectionIndex {
 private static final int CELL=64,MAX_CELLS=256;
 private final Map<Long,List<Box>> cells=new HashMap<>();
 private final List<Box> all=new ArrayList<>(),large=new ArrayList<>();
 private final Box domain;
 public BoxIntersectionIndex(){domain=null;}
 /** Queries must be contained in domain; objects completely outside cannot block visible labels. */
 public BoxIntersectionIndex(Box domain){this.domain=Objects.requireNonNull(domain);}
 public void add(Box box){
  if(domain!=null&&(box.x()>=domain.right()||box.right()<=domain.x()||box.y()>=domain.bottom()||box.bottom()<=domain.y()))return;
  all.add(box);
  if(!bounded(box)){large.add(box);return;}
  for(int x=first(box.x());x<=last(box.x(),box.right());x++)for(int y=first(box.y());y<=last(box.y(),box.bottom());y++)
   cells.computeIfAbsent(key(x,y),ignored->new ArrayList<>()).add(box);
 }
 public boolean intersects(Box box){
  if(!bounded(box))return intersects(all,box);
  if(intersects(large,box))return true;
  for(int x=first(box.x());x<=last(box.x(),box.right());x++)for(int y=first(box.y());y<=last(box.y(),box.bottom());y++){
   var bucket=cells.get(key(x,y));if(bucket!=null&&intersects(bucket,box))return true;
  }
  return false;
 }
 private static boolean intersects(List<Box> values,Box box){for(var other:values)if(other.x()<box.right()&&other.right()>box.x()&&other.y()<box.bottom()&&other.bottom()>box.y())return true;return false;}
 private static int first(int start){return Math.floorDiv(start,CELL);}
 // Zero-sized boxes retain the same predicate as the original linear scan.
 private static int last(int start,int end){return (int)Math.floorDiv(Math.max((long)start,(long)end-1),CELL);}
 private static boolean bounded(Box box){return box.right()>=box.x()&&box.bottom()>=box.y()&&((long)last(box.x(),box.right())-first(box.x())+1)*((long)last(box.y(),box.bottom())-first(box.y())+1)<=MAX_CELLS;}
 private static long key(int x,int y){return ((long)x<<32)|(y&0xffffffffL);}
}
