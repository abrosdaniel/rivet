package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Client preferences, isolated by server and account; never changes the server library. */
final class SkinWheelFavorites {
 private static String scope=""; private static LinkedHashSet<String> saved;
 private static Path path(){var mc=Minecraft.getInstance();var server=mc.getCurrentServer();String key=(server==null?"local":server.ip)+"/"+mc.getUser().getProfileId();String id=UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();if(!id.equals(scope)){scope=id;saved=null;var p=mc.gameDirectory.toPath().resolve("rivet/skin-wheel/"+id+".json");if(Files.exists(p))try{saved=new LinkedHashSet<>();for(var e:Json.read(p).getAsJsonArray("favorites"))if(saved.size()<8)saved.add(e.getAsString());}catch(Exception ignored){saved=null;}}return mc.gameDirectory.toPath().resolve("rivet/skin-wheel/"+id+".json");}
 static LinkedHashMap<String,JsonObject> available(){var all=new LinkedHashMap<String,JsonObject>();all.put("",null);if(SkinClient.library.has("entries"))for(var e:SkinClient.library.getAsJsonArray("entries")){var entry=e.getAsJsonObject();all.put(Json.str(entry,"id"),entry);}return all;}
 static LinkedHashMap<String,JsonObject> entries(){path();var all=available();var result=new LinkedHashMap<String,JsonObject>();for(String id:saved==null?all.keySet():saved)if(all.containsKey(id)&&result.size()<8)result.put(id,all.get(id));return result;}
 static boolean contains(String id){return entries().containsKey(id);}
 static String toggle(String id){var p=path();var next=new LinkedHashSet<>(entries().keySet());if(!next.remove(id)){if(next.size()>=8)return "skins.wheel.full";next.add(id);}var j=new JsonObject();var array=new JsonArray();next.forEach(array::add);j.add("favorites",array);try{Json.write(p,j);saved=next;return "";}catch(java.io.IOException error){return "skins.wheel.saveFailed";}}
}
