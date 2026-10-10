package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MapViewsTest {
 @TempDir Path root;
 private int render(MapTile tile,int mode){return MapColors.render(tile,0,0,64,64,MapLayer.SURFACE,30,384,false,new MapColors.Style(true,true,2,mode));}
 private MapTile sample(){var t=new MapTile();t.set(0,0,0xffaabbcc,64,64,0xff000000,false,List.of());t.biome(0,0,"minecraft:plains");return t;}
 @Test void unknownCellsRemainTransparentInEveryView(){for(int view=0;view<4;view++)assertEquals(0,render(new MapTile(),view));}
 @Test void nightUsesRecordedBlockLightAndPreservesSource(){var t=sample();t.blockLight(0,0,0);long revision=t.revision();int original=t.color(0,0);int dark=render(t,1);assertNotEquals(render(t,0),dark);assertEquals(revision,t.revision());assertEquals(original,t.color(0,0));t.blockLight(0,0,15);assertEquals(render(t,0),render(t,1));assertNotEquals(dark,render(t,1));}
 @Test void emissiveLayersRemainVisibleAtNight(){var t=sample();t.blockLight(0,0,0);t.set(0,0,t.color(0,0),64,64,0,false,List.of(new MapOverlay(0x80ff9900,15,0)));assertEquals(render(t,0),render(t,1));}
 @Test void biomesUseRecordedIdsAndDeterministicModdedPalette(){var t=sample();int plains=render(t,2);t.biome(0,0,"minecraft:desert");assertNotEquals(plains,render(t,2));assertEquals(MapViews.biome("mod:marsh"),MapViews.biome("mod:marsh"));assertNotEquals(MapViews.biome("mod:marsh"),MapViews.biome("mod:volcano"));t.biome(0,0,"");assertEquals(0xff777777,render(t,2));}
 @Test void reliefReflectsHeightRatherThanBlockColour(){var t=sample();int low=render(t,3);t.set(0,0,0xff112233,64);assertEquals(low,render(t,3));t.set(0,0,0xff112233,220);assertNotEquals(low,render(t,3));}
 @Test void latestFormatAndSnapshotsRetainLightAndViews()throws Exception{var t=sample();t.blockLight(0,0,7);var copy=t.copy();t.blockLight(0,0,15);var repo=new MapRepository(root,UUID.randomUUID(),UUID.randomUUID());repo.write("minecraft:overworld",0,0,copy);var read=repo.read("minecraft:overworld",0,0).orElseThrow();assertEquals(7,read.blockLight(0,0));assertEquals(-1,read.blockLight(1,1));for(int mode=0;mode<4;mode++)assertEquals(render(copy,mode),render(read,mode));assertThrows(IllegalArgumentException.class,()->t.blockLight(0,0,16));}
 @Test void legacyV3KeepsTerrainWithoutInventingTorchLight()throws Exception{var repo=new MapRepository(root,UUID.randomUUID(),UUID.randomUUID());repo.write("minecraft:overworld",0,0,sample());Path file;try(var files=Files.walk(root)){file=files.filter(p->p.toString().endsWith(".tile")).findFirst().orElseThrow();}try(var out=new java.io.DataOutputStream(Files.newOutputStream(file))){out.writeInt(0x524d4150);out.writeInt(3);for(int n=0;n<256;n++){out.writeInt(0xffaabbcc);out.writeInt(64);out.writeInt(64);out.writeInt(0xff000000);out.writeBoolean(false);out.writeByte(0);out.writeUTF("minecraft:plains");}}var tile=repo.read("minecraft:overworld",0,0).orElseThrow();assertEquals(-1,tile.blockLight(0,0));assertEquals("minecraft:plains",tile.biome(0,0));assertNotEquals(render(tile,0),render(tile,1));repo.write("minecraft:overworld",0,0,tile);assertEquals(-1,repo.read("minecraft:overworld",0,0).orElseThrow().blockLight(0,0));}
}
