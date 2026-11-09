package dev.abros.rivet.core.skins;
import dev.abros.rivet.core.Json;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Only fixed Mojang endpoints and the official texture host may be fetched. */
public final class MojangSkins {
 public record Result(byte[] png,boolean slim,byte[] cape){public Result(byte[] png,boolean slim){this(png,slim,null);}}
 private static void checkCancelled()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Mojang skin lookup interrupted");}
 private static byte[] get(String address,int limit)throws IOException{
  checkCancelled();
  var url=URI.create(address);if(!url.getScheme().equals("https")||!Set.of("api.mojang.com","sessionserver.mojang.com","textures.minecraft.net").contains(url.getHost())||url.getPort()!=-1||url.getUserInfo()!=null)throw new IOException("Invalid Mojang URL");
  var response=dev.abros.rivet.core.PublicHttp.send(java.net.http.HttpRequest.newBuilder(url).GET().build());
  try(var in=response.body()){int status=response.statusCode();checkCancelled();if(status==204||status==404)return null;if(status!=200)throw new IOException("Mojang HTTP "+status);byte[] bytes=in.readNBytes(limit+1);checkCancelled();if(bytes.length>limit)throw new IOException("Mojang response too large");return bytes;}

 }
 public static Result find(String nickname,UUID official)throws Exception{
  checkCancelled();
  String id;if(official!=null)id=official.toString().replace("-","");else{if(!nickname.matches("[A-Za-z0-9_]{1,16}"))return new Result(null,false);var bytes=get("https://api.mojang.com/users/profiles/minecraft/"+nickname,16384);if(bytes==null)return new Result(null,false);id=Json.str(Json.parse(new String(bytes,StandardCharsets.UTF_8)),"id");}
  if(!id.matches("[0-9a-fA-F]{32}"))throw new IOException("Invalid Mojang profile");var bytes=get("https://sessionserver.mojang.com/session/minecraft/profile/"+id,32768);if(bytes==null)return new Result(null,false);
  return profile(bytes,MojangSkins::get);
 }
 @FunctionalInterface interface Fetcher {byte[] get(String url,int limit)throws IOException;}
 static Result profile(byte[] bytes,Fetcher fetch)throws IOException{
  for(var property:Json.parse(new String(bytes,StandardCharsets.UTF_8)).getAsJsonArray("properties")){
   var p=property.getAsJsonObject();if(!Json.str(p,"name").equals("textures"))continue;
   var body=Json.parse(new String(Base64.getDecoder().decode(Json.str(p,"value")),StandardCharsets.UTF_8));var textures=body.getAsJsonObject("textures");
   byte[] skin=null,cape=null;boolean slim=false;
   if(textures.has("SKIN")){var value=textures.getAsJsonObject("SKIN");skin=texture(value,fetch);slim=value.has("metadata")&&"slim".equals(Json.opt(value.getAsJsonObject("metadata"),"model",""));}
   if(textures.has("CAPE"))cape=texture(textures.getAsJsonObject("CAPE"),fetch);
   return new Result(skin,slim,cape);
  }
  return new Result(null,false);
 }
 private static byte[] texture(com.google.gson.JsonObject value,Fetcher fetch)throws IOException{
  String url=Json.str(value,"url");if(url.startsWith("http://textures.minecraft.net/"))url="https"+url.substring(4);
  var uri=URI.create(url);
  if(!"https".equals(uri.getScheme())||!"textures.minecraft.net".equals(uri.getHost())||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!uri.getPath().matches("/texture/[0-9a-fA-F]+"))throw new IOException("Invalid texture URL");
  byte[] png=fetch.get(url,1024*1024);if(png==null)throw new IOException("Mojang texture unavailable");return png;
 }
}
