package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import java.util.*;
import dev.abros.rivet.core.NativeLayout.Box;
import static org.junit.jupiter.api.Assertions.*;
class HudDropLayoutTest {
 @Test void snapsToCentersAndEdges(){var bounds=new Box(8,8,200,100);assertEquals(new Box(88,38,40,40),HudDropLayout.snap(new Box(91,35,40,40),bounds,8));assertEquals(new Box(8,68,40,40),HudDropLayout.snap(new Box(10,65,40,40),bounds,8));}
 @Test void keepsFreePositionAndIgnoresInvisibleWidgets(){var wanted=new Box(50,50,40,30);assertEquals(wanted,HudDropLayout.place(wanted,new Box(4,4,300,200),List.of(new Box(50,50,0,10)),4).orElseThrow());}
 @Test void movesOnlyDraggedWidgetAndKeepsGap(){var occupied=new Box(40,40,80,80);var wanted=new Box(65,65,20,20);var result=HudDropLayout.place(wanted,new Box(4,4,196,196),List.of(occupied),4).orElseThrow();assertFalse(HudDropLayout.overlaps(result,occupied,4));assertEquals(new Box(16,65,20,20),result);}
 @Test void crowdedScreenRejectsDrop(){assertTrue(HudDropLayout.place(new Box(5,5,80,80),new Box(4,4,100,100),List.of(new Box(4,4,100,100)),4).isEmpty());assertTrue(HudDropLayout.place(new Box(0,0,200,200),new Box(4,4,100,100),List.of(),4).isEmpty());}
 @Test void clampsToViewportAndHandlesMultipleObstacles(){var bounds=new Box(4,4,300,180);var blocks=List.of(new Box(4,4,120,120),new Box(150,80,120,100));var result=HudDropLayout.place(new Box(-50,300,40,40),bounds,blocks,4).orElseThrow();assertTrue(result.x()>=bounds.x()&&result.y()>=bounds.y()&&result.right()<=bounds.right()&&result.bottom()<=bounds.bottom());for(var block:blocks)assertFalse(HudDropLayout.overlaps(result,block,4));}
}
