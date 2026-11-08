package dev.abros.rivet.core;

import java.util.*;
import dev.abros.rivet.core.NativeLayout.Box;

/** Find the nearest free drop position without moving the other HUD elements. */
public final class HudDropLayout {
 public static Box snap(Box box,Box viewport,int threshold){
  int x=box.x(),y=box.y();int centerX=viewport.x()+(viewport.width()-box.width())/2,centerY=viewport.y()+(viewport.height()-box.height())/2;
  if(Math.abs(x-centerX)<=threshold)x=centerX;
  else if(Math.abs(x-viewport.x())<=threshold)x=viewport.x();else if(Math.abs(x-(viewport.right()-box.width()))<=threshold)x=viewport.right()-box.width();
  if(Math.abs(y-centerY)<=threshold)y=centerY;
  else if(Math.abs(y-viewport.y())<=threshold)y=viewport.y();else if(Math.abs(y-(viewport.bottom()-box.height()))<=threshold)y=viewport.bottom()-box.height();
  return new Box(x,y,box.width(),box.height());
 }
 public static Optional<Box> place(Box desired,Box viewport,Collection<Box> obstacles,int gap){
  if(gap<0)throw new IllegalArgumentException("Negative gap");
  if(desired.width()>viewport.width()||desired.height()>viewport.height())return Optional.empty();
  var solid=obstacles.stream().filter(b->b.width()>0&&b.height()>0).toList();
  int maxX=viewport.right()-desired.width(),maxY=viewport.bottom()-desired.height();
  var xs=new TreeSet<Integer>();var ys=new TreeSet<Integer>();
  xs.add(Math.clamp(desired.x(),viewport.x(),maxX));xs.add(viewport.x());xs.add(maxX);
  ys.add(Math.clamp(desired.y(),viewport.y(),maxY));ys.add(viewport.y());ys.add(maxY);
  for(var b:solid){xs.add(b.x()-gap-desired.width());xs.add(b.right()+gap);ys.add(b.y()-gap-desired.height());ys.add(b.bottom()+gap);}
  Box best=null;long distance=Long.MAX_VALUE;
  for(int x:xs)for(int y:ys){
   if(x<viewport.x()||x>maxX||y<viewport.y()||y>maxY)continue;
   var candidate=new Box(x,y,desired.width(),desired.height());
   if(solid.stream().anyMatch(b->overlaps(candidate,b,gap)))continue;
   long dx=(long)x-desired.x(),dy=(long)y-desired.y(),d=dx*dx+dy*dy;
   if(d<distance){distance=d;best=candidate;}
  }
  return Optional.ofNullable(best);
 }
 public static boolean overlaps(Box a,Box b,int gap){return a.width()>0&&a.height()>0&&b.width()>0&&b.height()>0&&a.x()<b.right()+gap&&a.right()+gap>b.x()&&a.y()<b.bottom()+gap&&a.bottom()+gap>b.y();}
 private HudDropLayout(){}
}
