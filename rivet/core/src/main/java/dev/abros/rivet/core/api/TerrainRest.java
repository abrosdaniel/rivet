package dev.abros.rivet.core.api;

import com.google.gson.JsonObject;
import dev.abros.rivet.api.*;
import dev.abros.rivet.core.Json;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Authenticated backend terrain endpoint. Reads never cause world loading or rendering. */
public final class TerrainRest implements RestApi.Extension {
 private static final String PREFIX="/api/v1/map/terrain";
 private final RestApi.Authorization auth;private final Supplier<TerrainApi> terrain;private final RestApi.Extension domains;
 public TerrainRest(RestApi.Authorization auth,Supplier<TerrainApi> terrain,RestApi.Extension domains){this.auth=auth;this.terrain=terrain;this.domains=domains;}
 @Override public Set<String> capabilities(){var result=new HashSet<>(domains.capabilities());try{terrain.get().metadata();result.add("map.terrain");try{terrain.get().profile("isometric").metadata();result.add("map.terrain.isometric");}catch(ApiException disabled){}}catch(ApiException unavailable){}return Set.copyOf(result);}
 private Object authorize(RestApi.Request r){return authorize(r,"map.tiles.read",false);}
 private Object authorize(RestApi.Request r,String scope,boolean command){
  if(!command&&(!r.method().equals("GET")||!r.body().isEmpty())||r.header("transfer-encoding")!=null||!command&&r.header("content-length")!=null&&!r.header("content-length").equals("0"))throw new ApiException(ApiException.Code.INVALID_ARGUMENT,"GET required");
  r.header("content-length");r.header("host");if(r.header("origin")!=null)throw new ApiException(ApiException.Code.FORBIDDEN,"Backend credential required");
  String value=r.header("authorization");if(value==null||!value.startsWith("Bearer ")||value.length()>128)throw new ApiException(ApiException.Code.UNAUTHENTICATED,"Credential required");
  try{return auth.authorize(value.substring(7),scope);}catch(ApiException e){throw e;}catch(Exception e){throw new ApiException(ApiException.Code.UNAVAILABLE,"Authorization unavailable");}
 }
 @Override public JsonObject handle(RestApi.Request r,String path,Map<String,String> query)throws Exception{
  if(path.equals(PREFIX+"/profiles")){
   Object grant=authorize(r);if(!query.isEmpty())throw ApiJson.invalid();var list=new com.google.gson.JsonArray();
   for(String name:terrain.get().profiles()){try{profileAccess(grant,name);}catch(ApiException denied){if(denied.code()==ApiException.Code.FORBIDDEN)continue;throw denied;}var item=new JsonObject();item.addProperty("id",name);item.addProperty("private",TerrainProfiles.privateName(name));item.add("projection",Json.GSON.toJsonTree(terrain.get().profile(name).projection()));terrain.get().profile(name).slice().ifPresent(slice->item.add("slice",Json.GSON.toJsonTree(slice)));list.add(item);}
   auth.revalidate(grant,"map.tiles.read");for(var item:list)profileAccess(grant,item.getAsJsonObject().get("id").getAsString());var result=new JsonObject();result.add("profiles",list);return result;
  }
  if(path.equals(PREFIX+"/jobs")||path.startsWith(PREFIX+"/jobs/")){
   Object grant=authorize(r,"map.render.manage",true);var selected=select(grant,query);query=withoutProfile(query);if(!query.isEmpty())throw ApiJson.invalid();CompletableFuture<TerrainApi.Job> future;
   if(path.equals(PREFIX+"/jobs")&&r.method().equals("GET")){if(!r.body().isEmpty())throw ApiJson.invalid();var list=selected.jobs().get(5,TimeUnit.SECONDS);auth.revalidate(grant,"map.render.manage");var reply=new JsonObject();reply.add("jobs",Json.GSON.toJsonTree(list));return reply;}
   if(path.equals(PREFIX+"/jobs/saved")){
    if(!r.method().equals("POST"))throw ApiJson.invalid();ApiJson.fields(ApiJson.object(r.body()));future=selected.renderSaved();
   }else if(path.equals(PREFIX+"/jobs")){
    if(!r.method().equals("POST"))throw ApiJson.invalid();var body=ApiJson.object(r.body());ApiJson.fields(body,"dimension","minChunkX","minChunkZ","maxChunkX","maxChunkZ");
    future=selected.render(new TerrainApi.Region(ApiJson.string(body,"dimension"),integer(body,"minChunkX"),integer(body,"minChunkZ"),integer(body,"maxChunkX"),integer(body,"maxChunkZ")));
   }else{if(!r.body().isEmpty())throw ApiJson.invalid();UUID id=UUID.fromString(path.substring((PREFIX+"/jobs/").length()));future=switch(r.method()){case "GET"->selected.job(id);case "DELETE"->selected.cancel(id);default->throw ApiJson.invalid();};}
   TerrainApi.Job job;try{job=future.get(5,TimeUnit.SECONDS);}catch(ExecutionException e){if(e.getCause() instanceof ApiException a)throw a;throw new ApiException(ApiException.Code.UNAVAILABLE,"Render control unavailable");}catch(TimeoutException e){future.cancel(false);throw new ApiException(ApiException.Code.BUSY,"Render control timed out");}
   auth.revalidate(grant,"map.render.manage");return Json.GSON.toJsonTree(job).getAsJsonObject();
  }
  if(!path.equals(PREFIX)&&!path.equals(PREFIX+"/events"))return domains.handle(r,path,query);
  Object grant=authorize(r);String profile=query.getOrDefault("profile","surface");var api=select(grant,query);query=withoutProfile(query);JsonObject result;
  if(path.equals(PREFIX)){if(!query.isEmpty())throw ApiJson.invalid();result=Json.GSON.toJsonTree(api.metadata()).getAsJsonObject();result.addProperty("profile",profile);result.add("cursor",Json.GSON.toJsonTree(api.cursor()));result.add("projection",Json.GSON.toJsonTree(api.projection()));result.add("materials",Json.GSON.toJsonTree(api.materials()));api.slice().ifPresent(slice->result.add("slice",Json.GSON.toJsonTree(slice)));}
  else{if(!query.keySet().equals(Set.of("stream","after")))throw ApiJson.invalid();result=Json.GSON.toJsonTree(api.changes(new ApiCursor(UUID.fromString(query.get("stream")),Long.parseLong(query.get("after"))))).getAsJsonObject();}
  auth.revalidate(grant,"map.tiles.read");profileAccess(grant,profile);return result;
 }
 private void profileAccess(Object grant,String name)throws Exception{if(!Set.of("surface","isometric").contains(name))auth.revalidate(grant,TerrainProfiles.scope(name));}
 private TerrainApi select(Object grant,Map<String,String> query)throws Exception{String name=query.getOrDefault("profile","surface");profileAccess(grant,name);return terrain.get().profile(name);}
 private static Map<String,String> withoutProfile(Map<String,String> query){var copy=new HashMap<>(query);copy.remove("profile");return copy;}
 private static int integer(JsonObject value,String key){try{var v=value.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw ApiJson.invalid();return v.getAsBigDecimal().intValueExact();}catch(RuntimeException invalid){throw ApiJson.invalid();}}
 @Override public RestApi.Response binary(RestApi.Request r,String path,Map<String,String> query)throws Exception{
  if(!path.equals(PREFIX+"/tile.png"))return domains.binary(r,path,query);
  Object grant=authorize(r);String profile=query.getOrDefault("profile","surface");var selected=select(grant,query);query=withoutProfile(query);if(!query.keySet().equals(Set.of("dimension","zoom","x","z")))throw ApiJson.invalid();
  var tile=new TerrainApi.Tile(query.get("dimension"),Integer.parseInt(query.get("zoom")),Integer.parseInt(query.get("x")),Integer.parseInt(query.get("z")));
  TerrainApi.Image image;var future=selected.tile(tile);
  try{image=future.get(5,TimeUnit.SECONDS);}catch(ExecutionException e){if(e.getCause() instanceof ApiException api)throw api;throw new ApiException(ApiException.Code.UNAVAILABLE,"Tile unavailable");}catch(TimeoutException e){future.cancel(false);throw new ApiException(ApiException.Code.BUSY,"Tile read timed out");}
  auth.revalidate(grant,"map.tiles.read");profileAccess(grant,profile);String tag="\""+image.etag()+"\"";var headers=Map.of("Content-Type","image/png","Cache-Control",TerrainProfiles.privateName(profile)?"private, no-store":"private, no-cache","Vary","Authorization","ETag",tag);
  String condition=r.header("if-none-match");if(condition!=null){if(condition.length()>1024)throw ApiJson.invalid();for(String value:condition.split(","))if(value.strip().equals(tag)||value.strip().equals("W/"+tag)||value.strip().equals("*"))return new RestApi.Response(304,headers,"",new byte[0]);}
  return new RestApi.Response(200,headers,"",image.png());
 }
}
