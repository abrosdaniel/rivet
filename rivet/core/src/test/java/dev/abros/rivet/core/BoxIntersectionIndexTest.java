package dev.abros.rivet.core;

import java.util.*;
import dev.abros.rivet.core.NativeLayout.Box;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoxIntersectionIndexTest {
 @Test void viewportIndexPreservesVisibleLabelsWithOffscreenObstacles(){
  var random=new Random(1847);var domain=new Box(-64,-64,768,640);var index=new BoxIntersectionIndex(domain);var boxes=new ArrayList<Box>();
  for(int n=0;n<4096;n++){var b=new Box(random.nextInt(10000)-5000,random.nextInt(10000)-5000,18,18);boxes.add(b);index.add(b);}
  for(int n=0;n<2048;n++){var q=new Box(random.nextInt(500),random.nextInt(450)-32,140,14);boolean expected=linear(boxes,q);assertEquals(expected,index.intersects(q));if(!expected){boxes.add(q);index.add(q);}}
 }
 private static boolean linear(List<Box> boxes,Box q){return boxes.stream().anyMatch(b->b.x()<q.right()&&b.right()>q.x()&&b.y()<q.bottom()&&b.bottom()>q.y());}
 @Test void matchesLinearAtEdgesAndNegativeCoordinates(){var index=new BoxIntersectionIndex();var boxes=List.of(new Box(-64,-64,64,64),new Box(64,64,64,64),new Box(0,0,0,0));boxes.forEach(index::add);for(int x:new int[]{-129,-65,-64,-63,-1,0,1,63,64,65,127,128})for(int y:new int[]{-65,-64,-1,0,63,64,127,128})for(int w:new int[]{0,1,64,130}){var q=new Box(x,y,w,w);assertEquals(linear(boxes,q),index.intersects(q),q.toString());}}
 @Test void preservesAcceptedLabelsInOrderForDenseMovingLayouts(){var random=new Random(1847);for(int shift:new int[]{-10000,0,31,64,10000}){var index=new BoxIntersectionIndex();var boxes=new ArrayList<Box>();for(int n=0;n<4096;n++){var b=new Box(random.nextInt(2048)-1024+shift,random.nextInt(2048)-1024,18,18);boxes.add(b);index.add(b);}for(int n=0;n<2048;n++){var q=new Box(random.nextInt(2048)-1024+shift,random.nextInt(2048)-1024,10+random.nextInt(140),14);boolean expected=linear(boxes,q);assertEquals(expected,index.intersects(q));if(!expected){boxes.add(q);index.add(q);}}}}
 @Test void boundsHugeRectangleWorkAndPreservesIntegerOverflowSemantics(){var index=new BoxIntersectionIndex();var boxes=List.of(new Box(-1000000,-1000000,2000000,2000000),new Box(Integer.MAX_VALUE-2,0,10,10),new Box(Integer.MIN_VALUE,0,1,1));boxes.forEach(index::add);for(var q:List.of(new Box(-3,-3,6,6),new Box(2000000,0,1,1),new Box(Integer.MAX_VALUE-1,0,10,10),new Box(Integer.MIN_VALUE,0,0,1),new Box(-2000000,-2000000,4000000,4000000)))assertEquals(linear(boxes,q),index.intersects(q));}
 @Test void emptyAndTouchingEdgesDoNotCollide(){var index=new BoxIntersectionIndex();assertFalse(index.intersects(new Box(0,0,1,1)));index.add(new Box(0,0,64,64));assertFalse(index.intersects(new Box(64,0,1,1)));assertFalse(index.intersects(new Box(0,64,1,1)));assertTrue(index.intersects(new Box(63,63,1,1)));}
}
