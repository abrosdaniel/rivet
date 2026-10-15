package dev.abros.rivet.core.api;
import dev.abros.rivet.api.*;
import dev.abros.rivet.core.ServerSettings;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static dev.abros.rivet.core.api.TerrainRendererTest.*;
import static dev.abros.rivet.core.api.RestApiTest.*;
class CaveRendererTest {
 @TempDir Path root;
 static final String DIM="minecraft:overworld";
 static TerrainProfiles.Spec spec(int min,int max){return new TerrainProfiles.Spec("caves","cave",List.of(),min,max);}
 static class Snapshot {
  int[] colors=new int[32*256],models=new int[32*256],tints=new int[32*256];byte[] flags=new byte[32*256];
  Snapshot(){Arrays.fill(flags,CaveRenderer.AIR);Arrays.fill(tints,0xffffff);}
  void block(int column,int y,int color,byte flag){colors[y*256+column]=color;flags[y*256+column]=flag;}
  CompletableFuture<Void> render(CaveRenderer renderer){return renderer.render(DIM,0,0,0,32,colors,List.of(),models,tints,flags);}
 }
 @Test void highestEnclosedFloorNeedsHeadroomAndNeverUsesSurfaceOrOutsideBand()throws Exception{
  try(var r=new CaveRenderer(root,UUID.randomUUID(),settings(List.of()),spec(0,15),null)){
   var s=new Snapshot();s.block(0,12,0xff808080,CaveRenderer.ROOF);s.block(0,8,0xff00ff00,CaveRenderer.ROOF);s.block(0,2,0xffff0000,CaveRenderer.ROOF);
   s.block(1,2,0xff00ff00,CaveRenderer.ROOF); // no roof above: exterior, not cave
   s.block(2,12,0xff808080,CaveRenderer.ROOF);s.block(2,10,0xff00ff00,CaveRenderer.ROOF); // one air block
   // Solid column below a one-block gap, to avoid a second valid cavity.
   for(int y=0;y<10;y++)s.block(2,y,0xff808080,CaveRenderer.ROOF);
   s.block(3,20,0xff808080,CaveRenderer.ROOF);s.block(3,2,0xff00ff00,CaveRenderer.ROOF); // roof outside configured band
   s.block(4,12,0xff808080,CaveRenderer.ROOF);s.block(4,8,0,(byte)0);s.block(4,2,0xff00ff00,CaveRenderer.ROOF); // unknown opaque floor never treated as air
   s.render(r).get();var image=ImageIO.read(new ByteArrayInputStream(r.tile(tile(0,0,0)).get().png()));assertEquals(0xff00ff00,image.getRGB(0,0));for(int x=1;x<5;x++)assertEquals(0,image.getRGB(x,0));assertEquals(new TerrainApi.Slice(0,15),r.slice().orElseThrow());assertEquals("cave",r.projection().profile());
  }
 }
 @Test void masksOverviewRestartAndChangedBandCannotExposeOldFloors()throws Exception{
  UUID world=UUID.randomUUID();String generation,tag;
  try(var r=new CaveRenderer(root,world,settings(List.of("0,0,0,0")),spec(0,15),null)){
   var s=new Snapshot();for(int column=0;column<256;column++){s.block(column,12,0xff808080,CaveRenderer.ROOF);s.block(column,8,0xff00ff00,CaveRenderer.ROOF);}s.render(r).get();generation=r.metadata().generation();tag=r.tile(tile(0,0,0)).get().etag();
   var image=ImageIO.read(new ByteArrayInputStream(r.tile(tile(0,0,0)).get().png()));assertEquals(0,image.getRGB(0,0));assertEquals(0xff00ff00,image.getRGB(1,0));assertEquals(0,ImageIO.read(new ByteArrayInputStream(r.tile(tile(1,0,0)).get().png())).getRGB(0,0));
   try(var paths=Files.walk(root)){assertTrue(paths.filter(Files::isRegularFile).allMatch(p->p.toString().endsWith(".png")));}
  }
  try(var r=new CaveRenderer(root,world,settings(List.of("0,0,0,0")),spec(0,15),null)){assertEquals(tag,r.tile(tile(0,0,0)).get().etag());}
  try(var r=new CaveRenderer(root,world,settings(List.of("0,0,0,0")),spec(16,31),null)){assertNotEquals(generation,r.metadata().generation());assertThrows(ExecutionException.class,()->r.tile(tile(0,0,0)).get());new Snapshot().render(r).get();assertEquals(0,ImageIO.read(new ByteArrayInputStream(r.tile(tile(0,0,0)).get().png())).getRGB(1,0));}
 }
 @Test void outsideDimensionBandIsEmptyAndShutdownFailsClosed()throws Exception{
  var r=new CaveRenderer(root,UUID.randomUUID(),settings(List.of()),spec(-64,-32),null);new Snapshot().render(r).get();assertEquals(0,ImageIO.read(new ByteArrayInputStream(r.tile(tile(0,0,0)).get().png())).getRGB(0,0));r.close();assertThrows(ApiException.class,r::metadata);assertThrows(ExecutionException.class,()->new Snapshot().render(r).get());
 }
 @Test void caveConfigurationIsExplicitBoundedAndHasNoSharedDefault()throws Exception{
  String template=ServerSettings.template();assertTrue(ServerSettings.parse(template).terrainProfiles().isEmpty());String profile="{id='caves', projection='cave', hidden=[], minY=-64, maxY=32}";
  var parsed=ServerSettings.parse(template.replace("profiles = []","profiles = ["+profile+"]"));assertEquals(-64,parsed.terrainProfiles().getFirst().minY());
  for(String bad:List.of(profile.replace(", minY=-64, maxY=32",""),profile.replace("minY=-64","minY=32"),profile.replace("maxY=32","maxY=1024"),profile.replace("minY=-64","minY=-2049"),profile.replace("maxY=32","maxY=32.5"),profile.replace("projection='cave'","projection='surface'")))assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(template.replace("profiles = []","profiles = ["+bad+"]")));
 }
 @Test void caveApiNeedsExactScopeEvenForCachedImageAndMetadata()throws Exception{
  class Gate implements RestApi.Authorization {boolean cave;public Object authorize(String token,String scope){return this;}public void revalidate(Object grant,String scope){if(scope.equals("map.tiles.profile.caves")&&!cave)throw new ApiException(ApiException.Code.FORBIDDEN,"denied");}}
  var gate=new Gate();try(var r=new CaveRenderer(root,UUID.randomUUID(),settings(List.of()),spec(0,15),null);var feed=feed()){
   new Snapshot().render(r).get();TerrainApi api=new TerrainApi(){public TerrainApi profile(String name){return r;}public Metadata metadata(){return r.metadata();}public ApiCursor cursor(){return r.cursor();}public Changes changes(ApiCursor c){return r.changes(c);}public CompletableFuture<Image> tile(Tile t){return r.tile(t);}};
   var rest=new RestApi(feed,gate,RestApiTest.settings(Set.of()),new TerrainRest(gate,()->api,(a,b,c)->null));String url="/api/v1/map/terrain/tile.png?profile=caves&dimension="+DIM+"&zoom=0&x=0&z=0";
   assertEquals(403,rest.handle(request("GET",url,"Authorization","Bearer key","If-None-Match","*")).status());gate.cave=true;var response=rest.handle(request("GET",url,"Authorization","Bearer key"));assertEquals(200,response.status());assertEquals("private, no-store",response.headers().get("Cache-Control"));assertEquals(0,com.google.gson.JsonParser.parseString(rest.handle(request("GET","/api/v1/map/terrain?profile=caves","Authorization","Bearer key")).body()).getAsJsonObject().getAsJsonObject("slice").get("minY").getAsInt());gate.cave=false;assertEquals(403,rest.handle(request("GET",url,"Authorization","Bearer key","If-None-Match",response.headers().get("ETag"))).status());
  }
 }
}
