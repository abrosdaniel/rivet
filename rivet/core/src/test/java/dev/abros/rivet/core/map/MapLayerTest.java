package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class MapLayerTest {
 @TempDir Path folder;
 @Test void negativeBandsAndSentinels(){assertEquals(-1,MapLayer.cave("minecraft:overworld",-1).band());assertEquals(-2,MapLayer.cave("minecraft:overworld",-17).band());assertEquals(MapLayer.cave("minecraft:overworld",16),MapLayer.cave("minecraft:overworld",31));assertNotEquals(MapLayer.surface("minecraft:overworld"),new MapLayer("minecraft:overworld",MapLayer.FULL));assertThrows(IllegalArgumentException.class,()->new MapLayer("bad",0));}
 @Test void surfaceAndCavesPersistIndependently()throws Exception{
  var world=UUID.randomUUID();var player=UUID.randomUUID();var repo=new MapRepository(folder,world,player);String dim="minecraft:overworld";
  var layers=new MapLayer[]{MapLayer.surface(dim),MapLayer.cave(dim,-20),MapLayer.cave(dim,40),new MapLayer(dim,MapLayer.FULL),MapLayer.cave("minecraft:the_nether",40)};
  for(int i=0;i<layers.length;i++){var tile=new MapTile();tile.set(1,2,0xff000001+i,i*10);repo.write(layers[i],-2,3,tile);}
  repo=new MapRepository(folder,world,player);
  for(int i=0;i<layers.length;i++){assertEquals(0xff000001+i,repo.read(layers[i],-2,3).orElseThrow().color(1,2));assertEquals(1,repo.chunks(layers[i]).size());}
  assertEquals(repo.read(dim,-2,3).orElseThrow().color(1,2),repo.read(layers[0],-2,3).orElseThrow().color(1,2));assertTrue(repo.read(MapLayer.cave(dim,100),-2,3).isEmpty());
 }
}
