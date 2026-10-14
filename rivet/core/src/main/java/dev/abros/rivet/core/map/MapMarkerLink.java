package dev.abros.rivet.core.map;

import dev.abros.rivet.core.Json;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** A bounded coordinate snapshot, never an executable command or a permission grant. */
public final class MapMarkerLink {
 public static String encode(MapMarker marker){return Base64.getUrlEncoder().withoutPadding().encodeToString(Json.GSON.toJson(json(marker)).getBytes(StandardCharsets.UTF_8));}
 public static JsonObject json(MapMarker marker){var j=new JsonObject();j.addProperty("dimension",marker.dimension());j.addProperty("name",marker.name());j.addProperty("x",marker.x());j.addProperty("y",marker.y());j.addProperty("z",marker.z());j.addProperty("color",marker.color());j.addProperty("icon",marker.death()?"pin":marker.icon());return j;}
 public static MapMarker decode(String encoded){
  if(encoded.length()>2048||!encoded.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Invalid marker link");
  try{return read(Json.parse(new String(Base64.getUrlDecoder().decode(encoded),StandardCharsets.UTF_8)));}catch(Exception bad){throw new IllegalArgumentException("Invalid marker link",bad);}
 }
 public static MapMarker read(JsonObject j){if(Json.str(j,"dimension").length()>256)throw new IllegalArgumentException("Invalid marker dimension");Json.keys(j,"dimension","name","x","y","z","color","icon");return new MapMarker(UUID.randomUUID(),Json.str(j,"dimension"),Json.str(j,"name"),integer(j,"x"),integer(j,"y"),integer(j,"z"),integer(j,"color"),Json.str(j,"icon"));}
 private static int integer(JsonObject j,String key){var v=j.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid coordinate");return v.getAsBigDecimal().intValueExact();}
 private MapMarkerLink(){}
}
