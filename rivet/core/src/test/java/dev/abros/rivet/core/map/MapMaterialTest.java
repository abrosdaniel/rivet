package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MapMaterialTest {
 @TempDir Path root;
 @Test void originalLayersAndBiomeSurviveDiskAndSnapshots()throws Exception{
  var tile=new MapTile();var layers=new ArrayList<>(List.of(new MapOverlay(0x80ff0000,15,15),new MapOverlay(0x800000ff,0,15)));tile.set(3,4,0xf0c8c8c8,66,63,0,false,layers);tile.biome(3,4,"minecraft:river");var snapshot=tile.copy();layers.clear();tile.biome(3,4,"minecraft:plains");
  var repo=new MapRepository(root,UUID.randomUUID(),UUID.randomUUID());repo.write(MapLayer.cave("minecraft:overworld",70),-1,2,snapshot);var restored=repo.read(MapLayer.cave("minecraft:overworld",70),-1,2).orElseThrow();assertEquals(snapshot.layers(3,4),restored.layers(3,4));assertEquals("minecraft:river",restored.biome(3,4));assertThrows(UnsupportedOperationException.class,()->restored.layers(3,4).clear());assertNull(restored.layers(0,0));
 }
 @Test void distinctMaterialsRetainTheirOrderAndAbsorbLight(){
  var tile=new MapTile();tile.set(0,0,0xf0c8c8c8,66,63,0,false,List.of(new MapOverlay(0x80ff0000,15,15),new MapOverlay(0x800000ff,0,15)));
  assertEquals(0xff921229,MapColors.render(tile,0,0,63,63,MapLayer.SURFACE,30,384,false,new MapColors.Style(true,false,0)));
  int unlit=MapColors.render(tile,0,0,63,63,MapLayer.SURFACE,30,384,false,new MapColors.Style(false,false,0));assertEquals(0xffb13170,unlit);
 }
 @Test void changingDepthLightingReusesOriginalLayers(){
  var tile=new MapTile();tile.set(0,0,0xf0808080,24,20,0,false,List.of(new MapOverlay(0xbf2040ff,0,8)));var copy=tile.copy();int dark=MapColors.render(tile,0,0,20,20,30,30,384,false,MapColors.Style.DEFAULT);int lit=MapColors.render(tile,0,0,20,20,30,30,384,true,MapColors.Style.DEFAULT);assertNotEquals(dark,lit);assertEquals(copy.layers(0,0),tile.layers(0,0));assertEquals(copy.color(0,0),tile.color(0,0));
 }
 @Test void readsLegacyVersionTwoWithoutDiscardingTerrain()throws Exception{
  var repo=new MapRepository(root,UUID.randomUUID(),UUID.randomUUID());repo.write("minecraft:overworld",0,0,new MapTile());Path file;try(var files=Files.walk(root)){file=files.filter(p->p.toString().endsWith(".tile")).findFirst().orElseThrow();}
  try(var out=new java.io.DataOutputStream(Files.newOutputStream(file))){out.writeInt(0x524d4150);out.writeInt(2);for(int i=0;i<256;i++){out.writeInt(0xff123456);out.writeInt(64);out.writeInt(63);out.writeInt(0xff000000);out.writeBoolean(false);}}
  var tile=repo.read("minecraft:overworld",0,0).orElseThrow();assertEquals(0xff123456,tile.color(0,0));assertNull(tile.layers(0,0));assertEquals("",tile.biome(0,0));repo.write("minecraft:overworld",0,0,tile);assertEquals(0xff123456,repo.read("minecraft:overworld",0,0).orElseThrow().color(0,0));
 }
}
