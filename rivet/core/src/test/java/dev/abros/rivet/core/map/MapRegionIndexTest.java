package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MapRegionIndexTest {
 @TempDir Path folder;
 private void tile(int x,int z)throws Exception{Files.write(folder.resolve(x+"_"+z+".tile"),new byte[0]);}
 private static boolean contains(MapRegionIndex index,int x,int z)throws Exception{return index.region(Math.floorDiv(x,64),Math.floorDiv(z,64)).get(Math.floorMod(z,64)*64+Math.floorMod(x,64));}
 @Test void largeLayerCrossesFormerLimitAndReloadsWithoutEnumeratingTiles()throws Exception{
  for(int n=0;n<100001;n++)tile(n%1000-500,n/1000-50);
  var index=new MapRegionIndex(folder);index.prepare(()->false);
  assertTrue(contains(index,499,49));assertTrue(contains(index,-500,50));assertFalse(contains(index,-499,50));
  // An unrelated malformed filename must not prevent loading any healthy region.
  Files.writeString(folder.resolve("99999999999999999999_0.tile"),"");
  var reopened=new MapRegionIndex(folder);reopened.prepare(()->{throw new AssertionError("Reopening scanned source files");});
  assertTrue(contains(reopened,-500,50));try(var generations=Files.list(folder.resolve(".index-v1"))){assertEquals(1,generations.filter(p->Files.isDirectory(p)).count());}
 }
 @Test void corruptionRebuildsOnlyDisposableIndexAndPreservesSources()throws Exception{
  tile(-1,-65);tile(100,200);var index=new MapRegionIndex(folder);index.prepare(()->false);
  String generation=Files.readString(folder.resolve(".index-v1/current"));Files.writeString(folder.resolve(".index-v1").resolve(generation).resolve("-1_-2.idx"),"bad");
  assertThrows(java.io.IOException.class,()->index.region(-1,-2));index.prepare(()->false);
  assertTrue(contains(index,-1,-65));assertTrue(contains(index,100,200));assertTrue(Files.exists(folder.resolve("-1_-65.tile")));
 }
 @Test void interruptedCommitRebuildsOnRestartAndCancellationIsRecoverable()throws Exception{
  tile(0,0);var index=new MapRegionIndex(folder);index.prepare(()->false);
  index.beginningTile();tile(100,100); // Crash between authoritative commit and index update.
  var reopened=new MapRegionIndex(folder);reopened.prepare(()->false);assertTrue(contains(reopened,100,100));
  reopened.invalidate();assertThrows(java.io.InterruptedIOException.class,()->reopened.prepare(()->true));
  reopened.prepare(()->false);assertTrue(contains(reopened,0,0));assertTrue(contains(reopened,100,100));
 }
 @Test void newTileDuringRebuildIsNotLost()throws Exception{
  for(int x=0;x<100;x++)tile(x,0);var index=new MapRegionIndex(folder);int[] visits={0};
  index.prepare(()->{if(++visits[0]==10)try{tile(9999,9999);index.added(9999,9999);}catch(Exception e){throw new RuntimeException(e);}return false;});
  assertTrue(contains(index,9999,9999));assertTrue(contains(new MapRegionIndex(folder),9999,9999));
 }
 @Test void repositoryCommitsUpdatePreparedIndex()throws Exception{
  var repo=new MapRepository(folder,UUID.randomUUID(),UUID.randomUUID());var layer=MapLayer.surface("minecraft:overworld");repo.prepareIndex(layer);
  repo.write(layer,-1,-1,new MapTile());assertTrue(repo.region(layer,-1,-1).get(4095));
 }

 @Test void removedIndexPageIsRebuilt()throws Exception{
  Path source=Files.createDirectory(folder.resolve("missing"));Files.createFile(source.resolve("0_0.tile"));var index=new MapRegionIndex(source);index.prepare(()->false);
  Path generation=source.resolve(".index-v1").resolve(Files.readString(source.resolve(".index-v1/current")));Files.delete(generation.resolve("0_0.idx"));
  assertThrows(java.io.IOException.class,()->index.region(0,0));index.prepare(()->false);assertTrue(index.region(0,0).get(0));
 }
}
