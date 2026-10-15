package dev.abros.rivet.core.api;
import dev.abros.rivet.api.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.abros.rivet.core.api.RestApiTest.*;
class TerrainRestTest {
 @TempDir Path root;
 @Test void profileDiscoveryRechecksPrivatePermissionAfterReadingMetadata(){try(var feed=feed()){
  var revoked=new java.util.concurrent.atomic.AtomicBoolean();
  RestApi.Authorization auth=new RestApi.Authorization(){public Object authorize(String token,String scope){return this;}public void revalidate(Object grant,String scope){if(scope.equals("map.tiles.profile.staff")&&revoked.get())throw new ApiException(ApiException.Code.FORBIDDEN,"Revoked");}};
  TerrainApi terrain=new TerrainApi(){
   public Metadata metadata(){throw new UnsupportedOperationException();}public ApiCursor cursor(){throw new UnsupportedOperationException();}public Changes changes(ApiCursor c){throw new UnsupportedOperationException();}public java.util.concurrent.CompletableFuture<Image> tile(Tile t){throw new UnsupportedOperationException();}
   public List<String> profiles(){return List.of("staff");}public TerrainApi profile(String name){return this;}
   public Projection projection(){revoked.set(true);return new Projection("staff","x","z",256);}
  };
  var api=new RestApi(feed,auth,settings(Set.of()),new TerrainRest(auth,()->terrain,(r,p,q)->null));
  var response=api.handle(request("GET","/api/v1/map/terrain/profiles","Authorization","Bearer secret"));
  assertEquals(403,response.status());assertFalse(response.body().contains("staff"));
 }}
 @Test void tileRequiresDedicatedScopeAndRevocationPrecedesConditionalGet()throws Exception{
  var auth=new Auth();try(var renderer=new TerrainRenderer(root,UUID.randomUUID(),TerrainRendererTest.settings(List.of()));var feed=feed()){
   renderer.renderChunk("minecraft:overworld",0,0,TerrainRendererTest.pixels(0xff112233)).get();
   var api=new RestApi(feed,auth,settings(Set.of()),new TerrainRest(auth,()->renderer,(r,p,q)->null));String url="/api/v1/map/terrain/tile.png?dimension=minecraft:overworld&zoom=0&x=0&z=0";
   assertEquals(401,api.handle(request("GET",url)).status());auth.onlyRead=true;assertEquals(403,api.handle(request("GET",url,"Authorization","Bearer secret")).status());auth.onlyRead=false;
   var image=api.handle(request("GET",url,"Authorization","Bearer secret"));assertEquals(200,image.status());assertEquals("image/png",image.headers().get("Content-Type"));assertEquals((byte)137,image.payload()[0]);String tag=image.headers().get("ETag");
   assertEquals(304,api.handle(request("GET",url,"Authorization","Bearer secret","If-None-Match",tag)).status());
   assertEquals(403,api.handle(request("GET",url,"Authorization","Bearer secret","Origin","https://site.example")).status());
   assertEquals(404,api.handle(request("GET",url.replace("x=0","x=12"),"Authorization","Bearer secret")).status());
   assertEquals(400,api.handle(request("GET",url+"&key=secret","Authorization","Bearer secret")).status());
   auth.revoked=true;assertEquals(401,api.handle(request("GET",url,"Authorization","Bearer secret","If-None-Match",tag)).status());
  }
 }
 @Test void disabledRendererDoesNotDisableStatusOrExposeCapability(){try(var feed=feed()){
  var auth=new Auth();var api=new RestApi(feed,auth,settings(Set.of()),new TerrainRest(auth,()->{throw new ApiException(ApiException.Code.MODULE_DISABLED,"off");},(r,p,q)->null));
  assertEquals(200,api.handle(request("GET","/api/v1/server/status","Authorization","Bearer secret")).status());
  assertEquals(503,api.handle(request("GET","/api/v1/map/terrain","Authorization","Bearer secret")).status());
  assertFalse(api.handle(request("GET","/api/v1/capabilities","Authorization","Bearer secret")).body().contains("map.terrain"));
 }}
 @Test void malformedJobCoordinatesAreClientErrors()throws Exception{try(var feed=feed();var renderer=new TerrainRenderer(root,UUID.randomUUID(),TerrainRendererTest.settings(List.of()))){
  var auth=new Auth();var api=new RestApi(feed,auth,settings(Set.of()),new TerrainRest(auth,()->renderer,(r,p,q)->null));
  for(String number:List.of("1.5","2147483648","null","\"0\"")){
   String body="{\"dimension\":\"minecraft:overworld\",\"minChunkX\":"+number+",\"minChunkZ\":0,\"maxChunkX\":1,\"maxChunkZ\":1}";
   assertEquals(400,api.handle(new RestApi.Request("POST","/api/v1/map/terrain/jobs",Map.of("authorization",List.of("Bearer secret")),body)).status());
  }
 }}
 @Test void savedJobIsExplicitAuthenticatedAndRejectsUnexpectedBody(){try(var feed=feed()){
  var auth=new Auth();var calls=new java.util.concurrent.atomic.AtomicInteger();var job=new TerrainApi.Job(UUID.randomUUID(),"READING",-1,0,0);
  TerrainApi terrain=new TerrainApi(){
   public Metadata metadata(){throw new UnsupportedOperationException();}public ApiCursor cursor(){throw new UnsupportedOperationException();}public Changes changes(ApiCursor c){throw new UnsupportedOperationException();}public java.util.concurrent.CompletableFuture<Image> tile(Tile t){throw new UnsupportedOperationException();}
   public java.util.concurrent.CompletableFuture<Job> renderSaved(){calls.incrementAndGet();return java.util.concurrent.CompletableFuture.completedFuture(job);}
  };
  var api=new RestApi(feed,auth,settings(Set.of()),new TerrainRest(auth,()->terrain,(r,p,q)->null));String url="/api/v1/map/terrain/jobs/saved";
  assertEquals(401,api.handle(new RestApi.Request("POST",url,Map.of(),"{}")).status());auth.onlyRead=true;
  assertEquals(403,api.handle(new RestApi.Request("POST",url,Map.of("authorization",List.of("Bearer secret")),"{}")).status());auth.onlyRead=false;
  assertEquals(400,api.handle(new RestApi.Request("POST",url,Map.of("authorization",List.of("Bearer secret")),"{\"generate\":true}")).status());
  assertEquals(0,calls.get());var result=api.handle(new RestApi.Request("POST",url,Map.of("authorization",List.of("Bearer secret")),"{}"));assertEquals(200,result.status());assertTrue(result.body().contains("READING"));assertEquals(1,calls.get());
 }}
}
