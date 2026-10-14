package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class MapMarkerLinkTest {
 @Test void snapshotPreservesUnicodeAndLocationWithoutPrivateMetadata(){
  var original=new MapMarker(UUID.randomUUID(),"minecraft:overworld","Дом у моря",-30000000,-64,30000000,0x123456,"home",0,UUID.randomUUID().toString(),false,false);
  var copy=MapMarkerLink.decode(MapMarkerLink.encode(original));
  assertEquals(original.name(),copy.name());assertEquals(original.x(),copy.x());assertEquals(original.y(),copy.y());assertEquals(original.z(),copy.z());assertEquals(original.color(),copy.color());assertEquals(original.icon(),copy.icon());assertNotEquals(original.id(),copy.id());assertEquals("",copy.category());assertTrue(copy.mapVisible());assertTrue(copy.minimapVisible());
 }
 @Test void rejectsCommandsUnknownFieldsAndFractionalCoordinates(){
  assertThrows(IllegalArgumentException.class,()->MapMarkerLink.decode("/op player"));
  assertThrows(IllegalArgumentException.class,()->MapMarkerLink.decode("a".repeat(2049)));
  var marker=new MapMarker(UUID.randomUUID(),"minecraft:overworld","Test",1,64,2,0xffffff,"pin");
  var data=MapMarkerLink.json(marker);data.addProperty("command","op player");assertThrows(IllegalArgumentException.class,()->MapMarkerLink.read(data));
  data.remove("command");data.addProperty("x",1.5);assertThrows(ArithmeticException.class,()->MapMarkerLink.read(data));
 }
}
