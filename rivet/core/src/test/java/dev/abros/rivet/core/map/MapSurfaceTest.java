package dev.abros.rivet.core.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MapSurfaceTest {
 @TempDir Path root;
 @Test void directionalLightMatchesDefaultSurfaceReference(){
  assertEquals(0xff959595,MapColors.shade(0xff808080,80,80,80,false));
  assertEquals(0xffaeaeae,MapColors.shade(0xff808080,80,79,79,false));
  assertEquals(0xff595959,MapColors.shade(0xff808080,80,81,81,false));
  assertEquals(0xff868686,MapColors.shade(0xff808080,0,0,0,false));
 }
 @Test void overlaysKeepTheirOwnLightAndOnlyTheGroundReceivesSlopeShading(){
  assertEquals(0xff2525a5,MapColors.surface(0xff808080,0x40000080,80,80,80,false));
  assertEquals(0xff161696,MapColors.surface(0xff808080,0x40000080,80,81,81,false));
  assertEquals(0xff123456,MapColors.surface(0xff808080,0x00123456,80,80,80,false));
  assertEquals(0,MapColors.surface(0,0,80,80,80,false));
 }
 @Test void glowingTerrainDoesNotDarkenBelowSeaLevelOrInShadow(){
  assertEquals(0xff808080,MapColors.shade(0xff808080,-20,-19,-19,true));
  assertEquals(0xffffffff,MapColors.shade(0xffffffff,80,79,79,true));
 }
 @Test void layersAndGroundHeightSurviveSnapshotsAndReload()throws Exception {
  var repo=new MapRepository(root,UUID.randomUUID(),UUID.randomUUID());var tile=new MapTile();
  tile.set(2,3,0xffc3b176,64,48,0x40203080,true);var snapshot=tile.copy();tile.set(2,3,0xff000000,0);
  repo.write("minecraft:overworld",0,0,snapshot);var restored=repo.read("minecraft:overworld",0,0).orElseThrow();
  assertEquals(64,restored.height(2,3));assertEquals(48,restored.groundHeight(2,3));assertEquals(0x40203080,restored.overlay(2,3));assertTrue(restored.glowing(2,3));
 }
 @Test void legacyExplorationUpgradesWithoutLosingUnknownPixelsOrMarkers()throws Exception {
  var repo=new MapRepository(root,UUID.randomUUID(),UUID.randomUUID());var marker=new MapMarker(UUID.randomUUID(),"minecraft:overworld","Дом",1,64,2,0x123456,"home");repo.markers(List.of(marker));
  repo.write("minecraft:overworld",0,0,new MapTile());Path file;
  try(var files=Files.walk(root)){file=files.filter(p->p.toString().endsWith(".tile")).findFirst().orElseThrow();}
  try(var out=new java.io.DataOutputStream(Files.newOutputStream(file))){out.writeInt(0x524d4150);out.writeInt(1);for(int i=0;i<256;i++){out.writeInt(i==0?0xff123456:0);out.writeInt(64);}}
  var tile=repo.read("minecraft:overworld",0,0).orElseThrow();assertEquals(0xff123456,tile.color(0,0));assertEquals(0,tile.color(1,0));assertEquals(64,tile.groundHeight(0,0));assertEquals(0xff000000,tile.overlay(0,0));
  repo.write("minecraft:overworld",0,0,tile);assertEquals(5640,Files.size(file));assertEquals(0xff123456,repo.read("minecraft:overworld",0,0).orElseThrow().color(0,0));assertEquals(List.of(marker),repo.markers());
 }
}
