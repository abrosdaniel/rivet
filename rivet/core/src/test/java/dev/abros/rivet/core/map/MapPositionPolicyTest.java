package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapPositionPolicyTest {
 @Test void defaultsAreFullMapForEveryone(){var p=MapPositionPolicy.defaults();assertEquals("full",p.mode());assertTrue(p.audienceAllows(false));assertTrue(p.distanceAllows("nether",100000,0,0,"overworld",0,0,0,128));}
 @Test void nearbyUsesInclusiveSphericalRadiusAndSameDimension(){var p=new MapPositionPolicy("nearby","all");assertTrue(p.distanceAllows("world",128,0,0,"world",0,0,0,128));assertFalse(p.distanceAllows("world",128,1,0,"world",0,0,0,128));assertFalse(p.distanceAllows("world",0,129,0,"world",0,0,0,128));assertFalse(p.distanceAllows("other",0,0,0,"world",0,0,0,128));}
 @Test void hiddenAndGroupsFailClosed(){assertFalse(new MapPositionPolicy("hidden","all").audienceAllows(true));assertFalse(new MapPositionPolicy("full","none").audienceAllows(true));var p=new MapPositionPolicy("full","groups");assertFalse(p.audienceAllows(false));assertTrue(p.audienceAllows(true));assertThrows(IllegalArgumentException.class,()->new MapPositionPolicy("invalid","all"));}
}
