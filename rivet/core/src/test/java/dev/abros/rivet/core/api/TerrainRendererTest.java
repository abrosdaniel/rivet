package dev.abros.rivet.core.api;
import dev.abros.rivet.api.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class TerrainRendererTest {
 @TempDir Path root;
 static TerrainSettings settings(List<String> hidden){return new TerrainSettings(true,List.of("minecraft:overworld"),-2048,-2048,2047,2047,2,64,64,1000,4,hidden);}
 static int[] pixels(int color){int[] p=new int[256];Arrays.fill(p,color);return p;}
 static TerrainApi.Tile tile(int zoom,int x,int z){return new TerrainApi.Tile("minecraft:overworld",zoom,x,z);}
 @Test void negativeCoordinatesMaskAndPyramidPersistWithoutRerender()throws Exception{
  UUID world=UUID.randomUUID();String generation;
  try(var r=new TerrainRenderer(root,world,settings(List.of("-16,-16,-16,-16")))){
   var start=r.cursor();r.renderChunk("minecraft:overworld",-1,-1,pixels(0xff20a060)).get(3,TimeUnit.SECONDS);generation=r.metadata().generation();
   var base=ImageIO.read(new ByteArrayInputStream(r.tile(tile(0,-1,-1)).get().png()));assertEquals(0,base.getRGB(0,0));assertEquals(0xff20a060,base.getRGB(1,0));
   var parent=ImageIO.read(new ByteArrayInputStream(r.tile(tile(1,-1,-1)).get().png()));assertEquals(0,parent.getRGB(8,8));assertEquals(0xff20a060,parent.getRGB(9,8));assertEquals(0,parent.getRGB(0,0));
   assertEquals(3,r.changes(start).items().size());var cursor=r.cursor();r.renderChunk("minecraft:overworld",-1,-1,pixels(0xff20a060)).get();assertTrue(r.changes(cursor).items().isEmpty());
  }
  try(var r=new TerrainRenderer(root,world,settings(List.of("-16,-16,-16,-16")))){assertEquals(generation,r.metadata().generation());assertTrue(r.tile(tile(0,-1,-1)).get().png().length>0);}
  try(var r=new TerrainRenderer(root,world,settings(List.of("-16,-16,-15,-15")))){assertNotEquals(generation,r.metadata().generation());assertThrows(ExecutionException.class,()->r.tile(tile(0,-1,-1)).get());}
 }
 @Test void quotaReplayAndShutdownAreBounded()throws Exception{
  var r=new TerrainRenderer(root,UUID.randomUUID(),settings(List.of()));var old=r.cursor();
  for(int i=0;i<100;i++)r.renderChunk("minecraft:overworld",i,0,pixels(0xff224466)).get();
  try(var files=Files.list(root)){assertTrue(files.count()<=64);}
  assertEquals(ApiException.Code.RESYNC_REQUIRED,assertThrows(ApiException.class,()->r.changes(old)).code());
  assertThrows(ExecutionException.class,()->r.tile(new TerrainApi.Tile("../../x",0,0,0)).get());
  r.close();assertEquals(ApiException.Code.UNAVAILABLE,assertThrows(ApiException.class,r::metadata).code());assertThrows(ExecutionException.class,()->r.tile(tile(0,0,0)).get());
 }
 @Test void pngAndRequestsAreDefensivelyCopied()throws Exception{
  try(var r=new TerrainRenderer(root,UUID.randomUUID(),settings(List.of()))){int[] pixels=pixels(0xffabcdef);var write=r.renderChunk("minecraft:overworld",0,0,pixels);Arrays.fill(pixels,0);write.get();var image=r.tile(tile(0,0,0)).get();byte[] changed=image.png();Arrays.fill(changed,(byte)0);assertNotEquals(0,image.png()[0]);assertEquals(0xffabcdef,ImageIO.read(new ByteArrayInputStream(image.png())).getRGB(1,1));}
 }
}
