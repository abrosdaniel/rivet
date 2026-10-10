package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapCaveTransitionTest {
 @Test void firstEntryIsImmediateAndSwitchesHaveMinimumInterval(){var t=new MapCaveTransition();assertEquals(42,t.update(42,1000));assertEquals(43,t.update(43,1100));assertEquals(43,t.update(MapLayer.SURFACE,1999));assertEquals(MapLayer.SURFACE,t.update(MapLayer.SURFACE,2000));assertEquals(MapLayer.SURFACE,t.update(12,2999));assertEquals(12,t.update(12,3000));}
 @Test void intermittentCeilingDoesNotRestartInterval(){var t=new MapCaveTransition();t.update(10,0);t.update(MapLayer.SURFACE,300);t.update(11,400);assertEquals(11,t.update(MapLayer.SURFACE,999));assertEquals(MapLayer.SURFACE,t.update(MapLayer.SURFACE,1000));t.reset();assertEquals(8,t.update(8,1001));}
 @Test void zeroAndCustomDelay(){var t=new MapCaveTransition();t.update(8,0,0);assertEquals(MapLayer.SURFACE,t.update(MapLayer.SURFACE,0,0));t.update(8,5000,2000);assertEquals(8,t.update(MapLayer.SURFACE,6999,2000));assertEquals(MapLayer.SURFACE,t.update(MapLayer.SURFACE,7000,2000));}
}
