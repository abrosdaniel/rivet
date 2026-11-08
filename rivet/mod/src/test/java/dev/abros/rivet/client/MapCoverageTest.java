package dev.abros.rivet.client;

import dev.abros.rivet.core.map.MapLayer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapCoverageTest {
 private final MapCoverage cache=new MapCoverage();
 @Test void stationaryViewReusesImmutableCoarseFirstCoverage(){
  var layer=MapLayer.surface("minecraft:overworld");
  var key=new MapRegionTextures.Key(layer,-1,-1,0);
  var input=new HashSet<>(Set.of(key));var covered=cache.get(input);
  assertEquals(5,covered.size());assertEquals(4,covered.iterator().next().level());
  for(int level=0;level<=4;level++)assertTrue(covered.contains(new MapRegionTextures.Key(layer,-1,-1,level)));
  assertSame(covered,cache.get(new HashSet<>(input)));
  assertThrows(UnsupportedOperationException.class,covered::clear);
  input.clear();assertTrue(cache.get(input).isEmpty());
  assertEquals(5,covered.size(),"A changed viewport must not mutate the previous frame's coverage");
 }
 @Test void layersAndDimensionsCannotReuseAnotherViewsCoverage(){
  var surface=MapLayer.surface("minecraft:overworld");var cave=MapLayer.cave(surface.dimension(),32);
  var before=cache.get(Set.of(new MapRegionTextures.Key(surface,0,0,0)));
  var after=cache.get(Set.of(new MapRegionTextures.Key(cave,0,0,0)));
  assertNotSame(before,after);assertTrue(after.stream().allMatch(k->k.layer().equals(cave)));
  var nether=MapLayer.surface("minecraft:the_nether");
  after=cache.get(Set.of(new MapRegionTextures.Key(nether,0,0,0)));
  assertTrue(after.stream().allMatch(k->k.layer().equals(nether)));
 }
}
