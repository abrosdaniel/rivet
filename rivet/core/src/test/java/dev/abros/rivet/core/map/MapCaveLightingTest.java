package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapCaveLightingTest {
 @Test void selectedTopAndInclusiveDepth(){assertEquals(1f,MapColors.caveDepth(31,31,30));assertEquals(1f/30,MapColors.caveDepth(2,31,30));assertEquals(0f,MapColors.caveDepth(1,31,30));assertEquals(.5f,MapColors.caveDepth(-30,-15,30));}
 @Test void fullCaveDepthFoldsAt64Blocks(){assertEquals(17f/80,MapColors.caveDepth(0,MapLayer.FULL,30));assertEquals(1f,MapColors.caveDepth(63,MapLayer.FULL,30));assertEquals(1f,MapColors.caveDepth(64,MapLayer.FULL,30));assertEquals(17f/80,MapColors.caveDepth(127,MapLayer.FULL,30));assertEquals(1f,MapColors.caveDepth(-64,MapLayer.FULL,30));}
 @Test void knownEmptyColumnsAreOpaqueAndUnknownStayUnknown(){assertEquals(0xff010101,MapColors.cave(0xff010101,0xff000000,-64,-64,-64,-64,20,30,384,false,false));assertEquals(0,MapColors.cave(0,0,0,0,0,0,20,30,384,false,true));}
 @Test void lightingUsesExactTopWithinBand(){int a=MapColors.cave(0xf0808080,0xff000000,10,10,10,10,15,30,384,false,true),b=MapColors.cave(0xf0808080,0xff000000,10,10,10,10,14,30,384,false,true);assertTrue((b&255)>(a&255));}
}
