package dev.abros.rivet.core.map;

import dev.abros.rivet.core.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MapWaypointImportTest {
 @TempDir Path dir;
 private static final String XAERO="waypoint:Дом§§ Север:Д:-128:64:32:12:false:0:gui.xaero_default:false:0:0:false";
 private MapWaypointImport.Entry entry(){return new MapWaypointImport.Entry("file","minecraft:overworld","Дом",1,64,-2,0xff123456,true);}
 @Test void xaeroPreservesUnicodeColonCoordinatesPaletteAndDisabled()throws Exception {
  var p=MapWaypointImport.xaero("#waypoints\r\nsets:gui.xaero_default\r\n"+XAERO+"\r\n"+XAERO.replace("false:0:gui","true:0:gui"),"waypoints.txt");
  assertTrue(p.issues().isEmpty());assertEquals(2,p.entries().size());var e=p.entries().getFirst();assertEquals("Дом: Север",e.name());assertEquals(-128,e.x());assertEquals(0xffff5555,e.color());assertEquals("",e.dimension());assertFalse(p.entries().getLast().visible());
 }
 @Test void doesNotInventHeightOrTurnDeathsIntoPermanentMarkers()throws Exception {
  var p=MapWaypointImport.xaero(XAERO.replace(":64:",":~:")+"\n"+XAERO.replace(":false:0:",":false:1:")+"\n"+XAERO.replace(":12:",":99:"),"file");assertTrue(p.entries().isEmpty());assertEquals(3,p.issues().size());
 }
 @Test void unknownDimensionRequiresExplicitSelectionAndNeverScalesNetherCoordinates()throws Exception {
  var e=MapWaypointImport.xaero(XAERO,"file").entries();assertThrows(IllegalArgumentException.class,()->MapWaypointImport.plan(e,Map.of(),List.of()));var p=MapWaypointImport.plan(e,Map.of("?file","minecraft:the_nether"),List.of());assertEquals(-128,p.additions().getFirst().x());assertEquals("minecraft:the_nether",p.additions().getFirst().dimension());
 }
 @Test void repeatsWithinBatchAndExistingAreSkippedWithoutOverwriting() {
  var e=entry();var m=e.marker(e.dimension());var p=MapWaypointImport.plan(List.of(e,e),Map.of(),List.of());assertEquals(1,p.additions().size());assertEquals(1,p.duplicates());assertTrue(MapWaypointImport.plan(List.of(e),Map.of(),List.of(m)).additions().isEmpty());assertEquals(1,MapWaypointImport.plan(List.of(e),Map.of(e.dimension(),"minecraft:the_end"),List.of(m)).additions().size());
 }
 @Test void currentJourneySchemaKeepsPrimaryDimensionAndVisibility()throws Exception {
  var json=Json.parse("""
   {"waypoints":{"id":{"version":"1","name":"Дом","pos":{"x":-4,"y":80,"z":9,"dimension":"minecraft:the_nether"},"color":1193046,"settings":{"enable":1,"showOnMap":0}}},"groups":{}}
   """);var p=MapWaypointImport.journey(json,"WaypointData.dat");assertTrue(p.issues().isEmpty());var e=p.entries().getFirst();assertEquals("minecraft:the_nether",e.dimension());assertEquals(0xff123456,e.color());assertFalse(e.visible());
 }
 @Test void malformedNumbersNeverTruncateOrEscapeCoordinates()throws Exception {
  String base="""
   {"waypoints":{"id":{"version":"1","name":"X","pos":{"x":1,"y":64,"z":2,"dimension":"minecraft:overworld"},"color":123}}}
   """;
  for(String v:List.of("1.5","30000001","4294967297")){var p=MapWaypointImport.journey(Json.parse(base.replace("\"x\":1","\"x\":"+v)),"file");assertTrue(p.entries().isEmpty());assertEquals(1,p.issues().size());}
 }
 @Test void fileReadingIsBoundedDoesNotFollowLinksOrReadNestedWorlds()throws Exception {
  Files.writeString(dir.resolve("waypoints.txt"),XAERO);Files.createDirectories(dir.resolve("other-world"));Files.writeString(dir.resolve("other-world/waypoints.txt"),XAERO);var p=MapWaypointImport.read(List.of(dir),bytes->{throw new AssertionError();});assertEquals(1,p.entries().size());assertEquals(XAERO,Files.readString(dir.resolve("waypoints.txt")));
  Files.write(dir.resolve("large.txt"),new byte[MapWaypointImport.MAX_BYTES+1]);assertThrows(java.io.IOException.class,()->MapWaypointImport.read(List.of(dir),bytes->null));
 }
 @Test void importCreatesExactBackupAndRejectsStalePreview()throws Exception {
  var world=UUID.randomUUID();var player=UUID.randomUUID();var repo=new MapRepository(dir,world,player);var old=entry().marker("minecraft:the_end");repo.markers(List.of(old));Path root=dir.resolve(world.toString()).resolve(player.toString());String before=Files.readString(root.resolve("markers.json"));var added=entry().marker("minecraft:overworld");repo.importMarkers(List.of(old),List.of(added));assertEquals(List.of(old,added),repo.markers());try(var files=Files.list(root.resolve("backups"))){assertEquals(before,Files.readString(files.findFirst().orElseThrow()));}
  assertThrows(java.io.IOException.class,()->repo.importMarkers(List.of(old),List.of(entry().marker("minecraft:overworld"))));assertEquals(List.of(old,added),repo.markers());
 }
 @Test void backupFailureDoesNotModifyMarkers()throws Exception {
  var world=UUID.randomUUID();var player=UUID.randomUUID();var repo=new MapRepository(dir,world,player);var old=entry().marker("minecraft:the_end");repo.markers(List.of(old));Files.writeString(dir.resolve(world.toString()).resolve(player.toString()).resolve("backups"),"blocked");assertThrows(java.io.IOException.class,()->repo.importMarkers(List.of(old),List.of(entry().marker("minecraft:overworld"))));assertEquals(List.of(old),repo.markers());
 }
 @Test void limitRejectsEntireBatch() {
  var list=new ArrayList<MapMarker>();for(int i=0;i<4096;i++)list.add(new MapMarker(UUID.randomUUID(),"minecraft:overworld","M"+i,i,64,0,0,"pin"));assertThrows(IllegalArgumentException.class,()->MapWaypointImport.plan(List.of(entry()),Map.of(),list));
 }
 @Test void legacyJsonIsNotImported()throws Exception {
  Path file=dir.resolve("old.json");Files.writeString(file,"{}");var p=MapWaypointImport.read(List.of(file),bytes->{throw new AssertionError();});assertTrue(p.entries().isEmpty());assertEquals(1,p.issues().size());
 }
 @Test void oversizedSerializedBatchDoesNotReplaceReadableStore()throws Exception {
  var repo=new MapRepository(dir,UUID.randomUUID(),UUID.randomUUID());var old=entry().marker("minecraft:overworld");repo.markers(List.of(old));var additions=new ArrayList<MapMarker>();for(int i=0;i<4095;i++)additions.add(new MapMarker(UUID.randomUUID(),"minecraft:overworld","𐀀".repeat(80),i,64,0,0,"pin"));assertThrows(java.io.IOException.class,()->repo.importMarkers(List.of(old),additions));assertEquals(List.of(old),repo.markers());
 }

 @Test void separateXaeroFilesCanTargetDifferentDimensions()throws Exception {
  var a=MapWaypointImport.xaero(XAERO,"nether/waypoints.txt").entries().getFirst();var b=MapWaypointImport.xaero(XAERO,"world/waypoints.txt").entries().getFirst();var p=MapWaypointImport.plan(List.of(a,b),Map.of(a.mappingKey(),"minecraft:the_nether",b.mappingKey(),"minecraft:overworld"),List.of());assertEquals(2,p.additions().size());assertEquals("minecraft:the_nether",p.additions().getFirst().dimension());assertEquals("minecraft:overworld",p.additions().getLast().dimension());
 }

}
