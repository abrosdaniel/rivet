package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MapStorageTest {
 @TempDir Path root;
 @Test void clearsBothTerrainSourcesAndPreservesMetadataAndOtherWorlds()throws Exception{
  UUID world=UUID.randomUUID(),player=UUID.randomUUID(),other=UUID.randomUUID();
  for(String base:List.of("maps","maps-received")){
   var repo=new MapRepository(root.resolve(base),world,player);repo.write("minecraft:overworld",0,0,new MapTile());
   var dir=repo.storageDirectory();Files.writeString(dir.resolve("markers.json"),"personal");Files.createDirectories(dir.resolve("backups"));Files.writeString(dir.resolve("backups/old.json"),"backup");
   repo.previews().write(MapLayer.surface("minecraft:overworld"),0,0,0,new int[4096]);
  }
  var otherRepo=new MapRepository(root.resolve("maps"),other,player);otherRepo.write("minecraft:overworld",0,0,new MapTile());
  var storage=new MapStorage(root);storage.remember(world,player,"Test server","Owner");var before=storage.scan().stream().filter(e->e.world().equals(world)).findFirst().orElseThrow();assertEquals("Test server",before.name());assertEquals("Owner",before.playerName());assertEquals(2,before.tiles());assertTrue(before.bytes()>before.terrainBytes());
  storage.clearTerrain(world,player);storage.clearTerrain(world,player);
  var after=storage.scan().stream().filter(e->e.world().equals(world)).findFirst().orElseThrow();assertEquals(0,after.terrainBytes());assertEquals(0,after.tiles());
  for(String base:List.of("maps","maps-received")){var dir=root.resolve(base).resolve(world.toString()).resolve(player.toString());assertEquals("personal",Files.readString(dir.resolve("markers.json")));assertEquals("backup",Files.readString(dir.resolve("backups/old.json")));}
  assertTrue(otherRepo.read("minecraft:overworld",0,0).isPresent());
 }
 @Test void unknownFoldersAreNotRemoved()throws Exception{
  UUID world=UUID.randomUUID(),player=UUID.randomUUID();var dir=root.resolve("maps").resolve(world.toString()).resolve(player.toString());Files.createDirectories(dir.resolve("custom"));Files.writeString(dir.resolve("custom/data"),"keep");
  var storage=new MapStorage(root);storage.clearTerrain(world,player);assertEquals("keep",Files.readString(dir.resolve("custom/data")));assertEquals(0,storage.scan().getFirst().terrainBytes());
 }
 @Test @org.junit.jupiter.api.condition.EnabledOnOs({org.junit.jupiter.api.condition.OS.MAC,org.junit.jupiter.api.condition.OS.LINUX})
 void linksNeverReachFilesOutsideTheSelectedMap()throws Exception{
  UUID world=UUID.randomUUID(),player=UUID.randomUUID();Path outside=root.resolve("outside");Files.createDirectories(outside);Files.writeString(outside.resolve("keep"),"untouched");
  Path owner=root.resolve("maps").resolve(world.toString()).resolve(player.toString());Files.createDirectories(owner);Files.createSymbolicLink(owner.resolve("a".repeat(64)),outside);
  var storage=new MapStorage(root);storage.clearTerrain(world,player);assertEquals("untouched",Files.readString(outside.resolve("keep")));assertTrue(Files.isSymbolicLink(owner.resolve("a".repeat(64))));
  Files.delete(owner.resolve("a".repeat(64)));Files.delete(owner);Files.createSymbolicLink(owner,outside);
  assertThrows(java.io.IOException.class,()->storage.clearTerrain(world,player));assertEquals("untouched",Files.readString(outside.resolve("keep")));
 }

 @Test void cleanupDiscardsOldIndexHandlesBeforeReopening()throws Exception{
  UUID world=UUID.randomUUID(),player=UUID.randomUUID();var layer=MapLayer.surface("minecraft:overworld");
  var before=new MapRepository(root.resolve("maps"),world,player);before.write(layer,0,0,new MapTile());before.prepareIndex(layer);assertTrue(before.region(layer,0,0).get(0));
  new MapStorage(root).clearTerrain(world,player);
  var after=new MapRepository(root.resolve("maps"),world,player);after.write(layer,1,0,new MapTile());after.prepareIndex(layer);var region=after.region(layer,0,0);assertFalse(region.get(0));assertTrue(region.get(1));
 }

}
