package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MinimapMotionTest {
 @Test void walkingKeepsZoomAndAccelerationReturnsSmoothly(){var m=new MinimapMotion();assertEquals(2,m.update(2,.2,0,true));double running=m.update(2,.29,16,true);assertTrue(running<2&&running>1.9);for(int t=32;t<=2000;t+=16)running=m.update(2,.29,t,true);assertTrue(running<1.9);double slowing=m.update(2,0,2016,true);assertTrue(slowing>running&&slowing<2);}
 @Test void transitionIsIndependentOfFrameRate(){var a=new MinimapMotion();var b=new MinimapMotion();a.update(2,0,0,true);b.update(2,0,0,true);double x=0,y=0;for(int t=10;t<=500;t+=10)x=a.update(2,.4,t,true);for(int t=20;t<=500;t+=20)y=b.update(2,.4,t,true);assertEquals(x,y,1e-10);}
 @Test void boundedAtExtremeSpeedAndResetDoesNotKeepOldWorldZoom(){var m=new MinimapMotion();assertEquals(2/1.3,m.update(2,100,0,false),1e-10);m.reset();assertEquals(4,m.update(4,0,5,true));}
}
