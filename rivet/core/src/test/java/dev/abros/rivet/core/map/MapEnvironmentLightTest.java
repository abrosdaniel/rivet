package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MapEnvironmentLightTest {
 @TempDir Path root;
 private MapTile tile(int block,int sky){var t=new MapTile();t.set(0,0,0xff808080,64,64,0xff000000,false,List.of());t.blockLight(0,0,block);t.skyLight(0,0,sky);return t;}
 private int color(MapTile t,int dark,boolean on,float ambient){return MapColors.render(t,0,0,64,64,MapLayer.SURFACE,30,384,false,new MapColors.Style(on,true,2,0,dark,ambient));}
 @Test void daylightRainNightAndDawnRecolourWithoutResampling(){var t=tile(0,15);long revision=t.revision();int day=color(t,0,true,0),rain=color(t,5,true,0),night=color(t,11,true,0);assertTrue((day&255)>(rain&255));assertTrue((rain&255)>(night&255));assertEquals(day,color(t,0,true,0));assertEquals(revision,t.revision());}
 @Test void lightToggleAndDimensionAmbientAreRespected(){var t=tile(0,15);assertEquals(color(t,0,false,0),color(t,11,false,0));assertTrue((color(t,11,true,.5f)&255)>(color(t,11,true,0)&255));}
 @Test void TorchLitGroundDoesNotBecomeDarkAtNight(){var t=tile(15,15);assertEquals(color(t,0,true,0),color(t,11,true,0));}
 @Test void CaveWithoutSkyDoesNotFollowOutsideDaylight(){var t=tile(4,0);assertEquals(color(t,0,true,0),color(t,11,true,0));}
 @Test void TransparentMaterialKeepsItsOwnLight(){var t=tile(0,0);t.set(0,0,0xff808080,64,64,0,false,List.of(new MapOverlay(0x80ff0000,15,0,0)));int night=color(t,11,true,0);assertTrue((night>>>16&255)>(night&255)*2);assertEquals(color(t,0,true,0),night);}
 @Test void LightingOffOverridesLegibleCaveLighting(){var t=tile(0,0);var style=new MapColors.Style(false,true,2);assertEquals(MapColors.render(t,0,0,64,64,80,30,384,false,style),MapColors.render(t,0,0,64,64,80,30,384,true,style));}
 @Test void SkyAndBlockLightSurviveSnapshotsAndV5Storage()throws Exception{var t=tile(4,12);t.set(0,0,t.color(0,0),64,64,0,false,List.of(new MapOverlay(0x80808080,3,2,10)));var copy=t.copy();t.skyLight(0,0,0);var repo=new MapRepository(root,UUID.randomUUID(),UUID.randomUUID());repo.write("minecraft:overworld",0,0,copy);var read=repo.read("minecraft:overworld",0,0).orElseThrow();assertEquals(12,read.skyLight(0,0));assertEquals(4,read.blockLight(0,0));assertEquals(10,read.layers(0,0).getFirst().skyLight());assertEquals(color(copy,11,true,0),color(read,11,true,0));}
}
