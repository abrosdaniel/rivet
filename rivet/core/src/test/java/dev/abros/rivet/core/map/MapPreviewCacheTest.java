package dev.abros.rivet.core.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MapPreviewCacheTest {
 @TempDir Path root;
 @Test void survivesNewRepositoryAndSeparatesWorldPlayerLayerAndScale()throws Exception{
  UUID world=UUID.randomUUID(),player=UUID.randomUUID();var repo=new MapRepository(root,world,player);var layer=MapLayer.surface("minecraft:overworld");int[] pixels=new int[4096];Arrays.fill(pixels,0xff123456);
  repo.previews().write(layer,-1,2,3,pixels);var reopened=new MapRepository(root,world,player);
  assertArrayEquals(pixels,reopened.previews().read(layer,-1,2,3).orElseThrow());
  assertTrue(reopened.previews().read(layer,-1,2,2).isEmpty());
  assertTrue(reopened.previews().read(MapLayer.cave(layer.dimension(),32),-1,2,3).isEmpty());
  assertTrue(new MapRepository(root,UUID.randomUUID(),player).previews().read(layer,-1,2,3).isEmpty());
  assertTrue(new MapRepository(root,world,UUID.randomUUID()).previews().read(layer,-1,2,3).isEmpty());
  assertTrue(reopened.chunks(layer).isEmpty(),"Previews must never discover terrain");
 }
 @Test void corruptedAndTruncatedPreviewsAreIgnored()throws Exception{
  var cache=new MapPreviewCache(root);var layer=MapLayer.surface("minecraft:overworld");cache.write(layer,0,0,0,new int[4096]);Path file;try(var paths=Files.list(root.resolve("previews-v1"))){file=paths.findFirst().orElseThrow();}
  byte[] bytes=Files.readAllBytes(file);bytes[10]^=1;Files.write(file,bytes);assertTrue(cache.read(layer,0,0,0).isEmpty());
  Files.write(file,new byte[8]);assertTrue(cache.read(layer,0,0,0).isEmpty());
 }
 @Test void oldPreviewsAreBoundedWithoutDeletingTiles()throws Exception{
  var cache=new MapPreviewCache(root);var layer=MapLayer.surface("minecraft:overworld");Files.writeString(root.resolve("kept.tile"),"terrain");
  for(int x=0;x<2081;x++)cache.write(layer,x,0,0,new int[4096]);
  try(var files=Files.list(root.resolve("previews-v1"))){assertEquals(2048,files.count());}
  assertEquals("terrain",Files.readString(root.resolve("kept.tile")));
 }
}
