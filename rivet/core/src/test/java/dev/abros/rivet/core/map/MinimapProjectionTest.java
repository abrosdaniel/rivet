package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MinimapProjectionTest {
 @Test void northUpPreservesCompassAndNegativeCoordinates(){var p=new MinimapProjection(-100,30,2,90,false);assertEquals(20,p.screenX(-90,30),1e-9);assertEquals(-20,p.screenY(-100,20),1e-9);}
 @Test void rotatedPlayerForwardAlwaysPointsUp(){for(int yaw=-360;yaw<=360;yaw+=30){var p=new MinimapProjection(10,20,2,yaw,true);double wx=10-Math.sin(Math.toRadians(yaw))*10,wz=20+Math.cos(Math.toRadians(yaw))*10;assertEquals(0,p.screenX(wx,wz),1e-8);assertEquals(-20,p.screenY(wx,wz),1e-8);}}
 @Test void samplingAndHitTestingAreInverse(){for(boolean rotate:new boolean[]{false,true})for(double yaw:new double[]{-179,0,43,179})for(double zoom:new double[]{.25,1,4}){var p=new MinimapProjection(-105,-77,zoom,yaw,rotate);double sx=p.screenX(-93,-90),sy=p.screenY(-93,-90);assertEquals(-93,p.worldX(sx,sy),1e-9);assertEquals(-90,p.worldZ(sx,sy),1e-9);}}
 @Test void roundCornersExcludedSquareCornersIncluded(){assertFalse(MinimapProjection.contains(40,40,50,true));assertTrue(MinimapProjection.contains(40,40,50,false));assertTrue(MinimapProjection.contains(0,50,50,true));assertFalse(MinimapProjection.contains(51,0,50,false));}
 @Test void compassFollowsShapePerimeterThroughEveryRotation(){
  for(int yaw=0;yaw<360;yaw++)for(boolean round:new boolean[]{true,false}){
   var p=new MinimapProjection(10,20,2,yaw,true);double x=p.screenX(10,19),y=p.screenY(10,19);
   var c=MinimapProjection.compass(x,y,49,round);
   assertEquals(49,round?Math.hypot(c[0],c[1]):Math.max(Math.abs(c[0]),Math.abs(c[1])),1e-8);
   assertEquals(0,x*c[1]-y*c[0],1e-8);
  }
  assertArrayEquals(new double[]{49,49},MinimapProjection.compass(1,1,49,false),1e-8);
 }
 @Test void rejectsInvalidTransform(){assertThrows(IllegalArgumentException.class,()->new MinimapProjection(0,0,0,0,false));assertThrows(IllegalArgumentException.class,()->new MinimapProjection(0,0,Double.NaN,0,false));assertThrows(IllegalArgumentException.class,()->new MinimapProjection(0,0,1,Double.POSITIVE_INFINITY,true));}
 @org.junit.jupiter.api.Test void distantPinsStayOnBoundaryAndKeepBearing(){
  for(boolean round:new boolean[]{true,false})for(double[] point:new double[][]{{1000,0},{-1000,200},{10,-2000},{-50,-1000},{0,0},{10,5}}){
   var pin=MinimapProjection.pin(point[0],point[1],50,round);
   org.junit.jupiter.api.Assertions.assertTrue(MinimapProjection.contains(pin[0],pin[1],50.00001,round));
   org.junit.jupiter.api.Assertions.assertEquals(0,point[0]*pin[1]-point[1]*pin[0],.00001);
   if(MinimapProjection.contains(point[0],point[1],50,round))org.junit.jupiter.api.Assertions.assertArrayEquals(point,pin,.00001);
   else org.junit.jupiter.api.Assertions.assertEquals(50,round?Math.hypot(pin[0],pin[1]):Math.max(Math.abs(pin[0]),Math.abs(pin[1])),.00001);
  }
 }
}
