package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapColorsTest {
 @Test void biomeTintPreservesTextureBrightnessAndCoverage(){assertEquals(0x80802008,MapColors.tint(0x80ff8040,0x804020));assertEquals(0xff123456,MapColors.tint(0xff123456,0xffffff));}
 @Test void translucentOverlayKeepsTheGroundInsteadOfLeavingHoles(){assertEquals(0xff80007f,MapColors.over(0x80ff0000,0xff0000ff));assertEquals(0xff123456,MapColors.over(0,0xff123456));assertEquals(0,MapColors.over(0,0));assertEquals(0xff00ff00,MapColors.over(0xff00ff00,0xffff0000));}
 @Test void TerrainSlopesDependOnNeighborHeightsAndKeepUnknownPixelsUnknown(){assertEquals(0,MapColors.shade(0,0,100,100,false));assertEquals(0xff868686,MapColors.shade(0xff808080,10,10,10,false));assertTrue((MapColors.shade(0xff808080,80,79,79,false)&255)>(MapColors.shade(0xff808080,80,81,81,false)&255));assertEquals(255,MapColors.shade(0xffffffff,Integer.MIN_VALUE,Integer.MAX_VALUE,0,false)>>>24);}
 @Test void SteepTerrainKeepsAmbientTextureBrightness(){int color=MapColors.shade(0xff808080,100,101,101,false);assertTrue((color&255)>=89);assertEquals(255,color>>>24);}
}
