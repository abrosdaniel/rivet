package dev.abros.rivet.core.api;
import dev.abros.rivet.api.*;
import dev.abros.rivet.core.ServerSettings;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static dev.abros.rivet.core.api.RestApiTest.*;
class TerrainPrivacyTest {
 @TempDir Path root;
 static int[] filledVolume(){int[] data=new int[4096];Arrays.fill(data,0xff40a060);return data;}
 static final String DIM="minecraft:overworld";
 @Test void independentMasksParentsCacheAndReplaySurviveRestart()throws Exception{
  UUID world=UUID.randomUUID();var base=TerrainRendererTest.settings(List.of("0,0,0,0"));
  var specs=List.of(new TerrainProfiles.Spec("staff","surface",List.of()),new TerrainProfiles.Spec("builders","surface",List.of("1,0,1,0")));
  String staffTag,staffGeneration;ApiCursor cursor;
  try(var views=new PrivateTerrainViews(root,world,base,specs,null)){
   views.render(DIM,0,0,TerrainRendererTest.pixels(0xff40a060),new String[256],new int[256],0,16,filledVolume(),List.of(),new int[4096],new int[4096]).get();
   var staff=views.profile("staff");var builders=views.profile("builders");cursor=staff.cursor();staffGeneration=staff.metadata().generation();
   var tile=TerrainRendererTest.tile(0,0,0);var image=staff.tile(tile).get();staffTag=image.etag();var decoded=ImageIO.read(new ByteArrayInputStream(image.png()));assertEquals(0,decoded.getRGB(0,0));assertEquals(0xff40a060,decoded.getRGB(24,8));
   var other=ImageIO.read(new ByteArrayInputStream(builders.tile(tile).get().png()));assertEquals(0,other.getRGB(24,8));
   var overview=ImageIO.read(new ByteArrayInputStream(staff.tile(TerrainRendererTest.tile(1,0,0)).get().png()));assertEquals(0,overview.getRGB(0,0));
   assertNotEquals(staff.metadata().generation(),builders.metadata().generation());assertNotEquals(staffTag,builders.tile(tile).get().etag());
   assertEquals(ApiException.Code.RESYNC_REQUIRED,assertThrows(ApiException.class,()->builders.changes(cursor)).code());
  }
  try(var views=new PrivateTerrainViews(root,world,base,specs,null)){assertEquals(staffTag,views.profile("staff").tile(TerrainRendererTest.tile(0,0,0)).get().etag());}
  var stricter=List.of(new TerrainProfiles.Spec("staff","surface",List.of("0,0,15,15")));
  try(var views=new PrivateTerrainViews(root,world,base,stricter,null)){assertNotEquals(staffGeneration,views.profile("staff").metadata().generation());assertThrows(ExecutionException.class,()->views.profile("staff").tile(TerrainRendererTest.tile(0,0,0)).get());assertThrows(ApiException.class,()->views.profile("builders"));}
 }
 @Test void isometricMasksBeforeVoxelPersistenceAndProjection()throws Exception{
  var base=TerrainRendererTest.settings(List.of());var spec=List.of(new TerrainProfiles.Spec("staff","isometric",List.of("0,0,15,15")));
  try(var views=new PrivateTerrainViews(root,UUID.randomUUID(),base,spec,null)){
   int[] colors=new int[4096];Arrays.fill(colors,0xffff0000);
   views.render(DIM,0,0,new int[256],new String[256],new int[256],0,16,colors,List.of(),new int[4096],new int[4096]).get();
   try(var paths=Files.walk(root)){for(var path:paths.filter(p->p.toString().endsWith(".png")).toList()){var image=ImageIO.read(path.toFile());for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)assertEquals(0,image.getRGB(x,y));}}
   try(var paths=Files.walk(root)){var vox=paths.filter(p->p.toString().endsWith(".vox")).findFirst().orElseThrow();try(var in=new DataInputStream(new java.util.zip.GZIPInputStream(Files.newInputStream(vox)))){in.readInt();int height=in.readInt();assertEquals(0,in.readInt());for(int i=0;i<height*256*3;i++)assertEquals(0,in.readInt());}}
  }
 }
 @Test void identicalPixelsHaveSeparateProfileValidators()throws Exception{
  var specs=List.of(new TerrainProfiles.Spec("alpha","surface",List.of()),new TerrainProfiles.Spec("beta","surface",List.of()));
  try(var views=new PrivateTerrainViews(root,UUID.randomUUID(),TerrainRendererTest.settings(List.of()),specs,null)){
   views.render(DIM,0,0,TerrainRendererTest.pixels(0xff123456),new String[256],new int[256],0,16,filledVolume(),List.of(),new int[4096],new int[4096]).get();
   var a=views.profile("alpha").tile(TerrainRendererTest.tile(0,0,0)).get();var b=views.profile("beta").tile(TerrainRendererTest.tile(0,0,0)).get();assertArrayEquals(a.png(),b.png());assertNotEquals(a.etag(),b.etag());
  }
 }
 @Test void permissionsPrecedeMetadataPngConditionalCacheAndDiscovery()throws Exception{
  class Gate implements RestApi.Authorization {boolean allowed=true,revoked;public Object authorize(String token,String scope){if(revoked)throw new ApiException(ApiException.Code.UNAUTHENTICATED,"revoked");return this;}public void revalidate(Object grant,String scope){authorize("",scope);if(scope.equals("map.tiles.profile.staff")&&!allowed)throw new ApiException(ApiException.Code.FORBIDDEN,"denied");}}
  var gate=new Gate();try(var renderer=new TerrainRenderer(root,UUID.randomUUID(),TerrainRendererTest.settings(List.of()));var feed=feed()){
   renderer.renderChunk(DIM,0,0,TerrainRendererTest.pixels(0xff123456)).get();
   TerrainApi multi=new TerrainApi(){public List<String> profiles(){return List.of("surface","staff");}public TerrainApi profile(String name){return renderer;}public Metadata metadata(){return renderer.metadata();}public ApiCursor cursor(){return renderer.cursor();}public Changes changes(ApiCursor c){return renderer.changes(c);}public CompletableFuture<Image> tile(Tile t){return renderer.tile(t);}};
   var api=new RestApi(feed,gate,settings(Set.of()),new TerrainRest(gate,()->multi,(r,p,q)->null));String url="/api/v1/map/terrain/tile.png?profile=staff&dimension="+DIM+"&zoom=0&x=0&z=0";
   var ok=api.handle(request("GET",url,"Authorization","Bearer secret"));assertEquals(200,ok.status());assertEquals("private, no-store",ok.headers().get("Cache-Control"));assertEquals("Authorization",ok.headers().get("Vary"));
   gate.allowed=false;assertEquals(403,api.handle(request("GET",url,"Authorization","Bearer secret","If-None-Match",ok.headers().get("ETag"))).status());
   assertEquals(403,api.handle(request("GET","/api/v1/map/terrain?profile=staff","Authorization","Bearer secret")).status());assertFalse(api.handle(request("GET","/api/v1/map/terrain/profiles","Authorization","Bearer secret")).body().contains("staff"));
   assertEquals(403,api.handle(request("GET","/api/v1/map/terrain/events?profile=staff&stream="+renderer.cursor().stream()+"&after=0","Authorization","Bearer secret")).status());
   gate.revoked=true;assertEquals(401,api.handle(request("GET",url,"Authorization","Bearer secret")).status());
  }
 }
 @Test void configuredProfilesAreBoundedAndMasksValidated()throws Exception{
  String template=ServerSettings.template();String row="{id='staff', projection='surface', hidden=['0,0,15,15']}";
  var config=ServerSettings.parse(template.replace("profiles = []","profiles = ["+row+"]"));assertEquals("staff",config.terrainProfiles().getFirst().id());
  for(String bad:List.of(row+","+row,row.replace("staff","../staff"),row.replace("staff","surface"),row.replace("surface","cave"),row.replace("0,0,15,15","15,0,0,0")))assertThrows(IllegalArgumentException.class,()->ServerSettings.parse(template.replace("profiles = []","profiles = ["+bad+"]")));
 }
}
