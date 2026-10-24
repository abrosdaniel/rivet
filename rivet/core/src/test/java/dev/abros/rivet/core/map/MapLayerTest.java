package dev.abros.rivet.core.map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class MapLayerTest {
 @TempDir Path folder;
 @Test void dimensionValidationPreservesResourceIdGrammar(){
  var grammar=java.util.regex.Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
  var values=new java.util.ArrayList<>(java.util.List.of("minecraft:overworld","mod_name:folder/path","a:b","a/b:c",":b","a:","a:b:c","A:b","a:b\n","a:\u0000","a:мир","", "a:../b"));
  var random=new java.util.Random(42);String alphabet="ab09_.-/: A\n\u0000я";
  for(int n=0;n<10000;n++){var value=new StringBuilder();for(int i=0,size=random.nextInt(40);i<size;i++)value.append(alphabet.charAt(random.nextInt(alphabet.length())));values.add(value.toString());}
  for(var value:values)if(grammar.matcher(value).matches())assertDoesNotThrow(()->MapLayer.surface(value));else assertThrows(IllegalArgumentException.class,()->MapLayer.surface(value));
  assertThrows(IllegalArgumentException.class,()->MapLayer.surface(null));
  for(int band:new int[]{-129,128,-1000,1000})assertThrows(IllegalArgumentException.class,()->new MapLayer("a:b",band));
 }
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
