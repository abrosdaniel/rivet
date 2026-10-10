package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapDeathTrackerTest {
 @Test void onlyOneRecordPerDeathAndRespawnAllowsAnother(){var t=new MapDeathTracker();assertFalse(t.observe(false));assertTrue(t.observe(true));for(int i=0;i<100;i++)assertFalse(t.observe(true));assertFalse(t.observe(false));assertTrue(t.observe(true));}
 @Test void newWorldDoesNotKeepPreviousDeathLatch(){var t=new MapDeathTracker();assertTrue(t.observe(true));t.reset();assertTrue(t.observe(true));}
}
