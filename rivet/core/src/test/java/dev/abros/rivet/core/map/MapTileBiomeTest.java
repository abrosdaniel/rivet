package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MapTileBiomeTest {
 @Test void unchangedBiomeDoesNotInvalidateTile(){
  var tile=new MapTile();assertFalse(tile.biome(0,0,""));assertEquals(0,tile.revision());
  assertTrue(tile.biome(0,0,"minecraft:plains"));long revision=tile.revision();
  assertFalse(tile.biome(0,0,new String("minecraft:plains")));assertEquals(revision,tile.revision());
  assertTrue(tile.biome(0,0,"mod:custom/path"));assertTrue(tile.biome(0,0,""));
 }
 @Test void malformedBiomeCannotChangeStoredSample(){
  var tile=new MapTile();tile.biome(0,0,"minecraft:plains");long revision=tile.revision();
  for(String value:new String[]{null,"plain","Minecraft:plains","minecraft:","m:"+"a".repeat(255)})
   assertThrows(IllegalArgumentException.class,()->tile.biome(0,0,value));
  assertEquals("minecraft:plains",tile.biome(0,0));assertEquals(revision,tile.revision());
 }
}
