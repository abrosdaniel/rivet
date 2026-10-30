package dev.abros.rivet.core;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Stateless deltas of already authorized and localized pages. No server-side private snapshots. */
public final class MenuPageDelta {
 public static final String FEATURE="menu-page-deltas";
 private static final int LIMIT=32767;
 private MenuPageDelta(){}
 public static boolean eligible(JsonObject query){
  if(!MenuRequests.read(query))return false;
  return Json.opt(query,"action","").equals("community")
   ?Set.of("list","detail","plusProfile","plusIgnores","plusItemRead","toolsPlaces","toolsFollowing","toolsPrivacy","toolsMapSettings","workGet","workMembers").contains(Json.opt(query,"op",""))
   :Set.of("players","reports","myReports","myReport","history","menuData").contains(Json.opt(query,"action",""));
 }
 private static JsonObject body(JsonObject value){var copy=value.deepCopy();copy.remove("request");return copy;}
 private static JsonElement canonical(JsonElement value){if(value.isJsonObject()){var out=new JsonObject();new TreeSet<>(value.getAsJsonObject().keySet()).forEach(key->out.add(key,canonical(value.getAsJsonObject().get(key))));return out;}if(value.isJsonArray()){var out=new JsonArray();for(var item:value.getAsJsonArray())out.add(canonical(item));return out;}return value;}
 private static String hash(JsonElement value){return Hashes.sha256(Json.GSON.toJson(canonical(value)).getBytes(StandardCharsets.UTF_8));}
 private static String rowKey(JsonElement value){
  if(!value.isJsonArray()||value.getAsJsonArray().isEmpty()||value.getAsJsonArray().size()>100)return "";
  for(String key:List.of("id","uuid")){var seen=new HashSet<String>();boolean valid=true;for(var entry:value.getAsJsonArray()){
   if(!entry.isJsonObject()||!entry.getAsJsonObject().has(key)){valid=false;break;}
   var id=entry.getAsJsonObject().get(key);if(!id.isJsonPrimitive()||!id.getAsJsonPrimitive().isString()||!seen.add(id.getAsString())){valid=false;break;}
  }if(valid)return key;}return "";
 }
 public static JsonObject known(JsonObject snapshot){
  if(snapshot==null||snapshot.has("error")||snapshot.size()>64||Json.GSON.toJson(snapshot).length()>LIMIT)return null;
  var base=body(snapshot);var result=new JsonObject();result.addProperty("hash",hash(base));var fields=new JsonObject();var rows=new JsonObject();
  for(var entry:base.entrySet()){fields.addProperty(entry.getKey(),hash(entry.getValue()));String key=rowKey(entry.getValue());if(!key.isEmpty()){var indexed=new JsonObject();indexed.addProperty("key",key);indexed.add("hashes",RowDelta.known(entry.getValue().getAsJsonArray(),key));rows.add(entry.getKey(),indexed);}}
  result.add("fields",fields);result.add("rows",rows);int length=Json.GSON.toJson(result).length();return length>16000||length+256>=Json.GSON.toJson(snapshot).length()?null:result;
 }
 public static JsonObject encode(JsonObject reply,JsonObject known){
  if(known==null||reply.has("error")||reply.size()>64||Json.GSON.toJson(reply).length()>LIMIT)return reply;
  try{
   if(Json.GSON.toJson(known).length()>16000)return reply;
   String baseHash=Json.str(known,"hash");if(!baseHash.matches("[0-9a-f]{64}"))return reply;
   var fields=known.getAsJsonObject("fields");var rows=known.getAsJsonObject("rows");if(fields.size()>64||rows.size()>64)return reply;
   var base=body(reply);var changed=new JsonObject();var changedRows=new JsonObject();var removed=new JsonArray();
   for(String key:fields.keySet())if(!base.has(key))removed.add(key);
   for(var entry:base.entrySet()){
    String field=entry.getKey();var value=entry.getValue();if(hash(value).equals(Json.opt(fields,field,"")))continue;
    String key=rowKey(value);
    if(!key.isEmpty()&&rows.has(field)&&Json.opt(rows.getAsJsonObject(field),"key","").equals(key)){
     var delta=RowDelta.encode(value.getAsJsonArray(),rows.getAsJsonObject(field).getAsJsonObject("hashes"),key);delta.addProperty("key",key);
     if(Json.GSON.toJson(delta).length()<Json.GSON.toJson(value).length()){changedRows.add(field,delta);continue;}
    }
    changed.add(field,value.deepCopy());
   }
   var patch=new JsonObject();patch.addProperty("base",baseHash);patch.addProperty("hash",hash(base));patch.add("fields",changed);patch.add("rows",changedRows);patch.add("removed",removed);
   var result=new JsonObject();result.addProperty("request",Json.opt(reply,"request",""));result.add("pageDelta",patch);
   return Json.GSON.toJson(result).length()+64<Json.GSON.toJson(reply).length()?result:reply;
  }catch(RuntimeException invalid){return reply;}
 }
 public static JsonObject apply(JsonObject previous,JsonObject packet){
  if(previous==null)throw new IllegalArgumentException("Missing page baseline");
  var patch=packet.getAsJsonObject("pageDelta");var result=body(previous);
  if(!hash(result).equals(Json.str(patch,"base")))throw new IllegalArgumentException("Page baseline mismatch");
  var fields=patch.getAsJsonObject("fields");var rows=patch.getAsJsonObject("rows");var removed=patch.getAsJsonArray("removed");
  if(fields.size()>64||rows.size()>64||removed.size()>64)throw new IllegalArgumentException("Page delta too large");
  for(var field:removed)result.remove(field.getAsString());
  for(var field:fields.entrySet())result.add(field.getKey(),field.getValue().deepCopy());
  for(var field:rows.entrySet()){var delta=field.getValue().getAsJsonObject();String key=Json.str(delta,"key");if(!Set.of("id","uuid").contains(key)||fields.has(field.getKey()))throw new IllegalArgumentException("Invalid row delta");result.add(field.getKey(),RowDelta.apply(result.getAsJsonArray(field.getKey()),delta,key));}
  if(Json.GSON.toJson(result).length()>LIMIT||!hash(result).equals(Json.str(patch,"hash")))throw new IllegalArgumentException("Page delta result mismatch");
  result.addProperty("request",Json.opt(packet,"request",""));return result;
 }
}
