package dev.abros.rivet.core.map;

import com.google.gson.*;
import java.util.*;

/** Shared validation for server-owned polygons and their client previews. */
public final class MapTerritoryJson {
 public static MapTerritory read(JsonObject j,UUID id,String groupName){var copy=j.deepCopy();copy.addProperty("name",groupName);return read(copy,id);}
 public static MapTerritory read(JsonObject j,UUID id){
  var points=new ArrayList<MapTerritory.Point>();var rows=j.getAsJsonArray("points");
  if(rows.size()>128)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.at_most_128_vertices_ef8a204a"));
  for(var e:rows){var p=e.getAsJsonObject();points.add(new MapTerritory.Point(integer(p.get("x")),integer(p.get("z"))));}
  return new MapTerritory(id,j.get("dimension").getAsString(),j.get("name").getAsString(),integer(j.get("color")),points);
 }
 private static int integer(JsonElement e){if(!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.ui.enter_a_whole_number_0ac5d5f8"));return e.getAsBigDecimal().intValueExact();}
 public static JsonObject write(MapTerritory t){var j=new JsonObject();j.addProperty("dimension",t.dimension());j.addProperty("name",t.name());j.addProperty("color",t.color());var points=new JsonArray();for(var p:t.points()){var row=new JsonObject();row.addProperty("x",p.x());row.addProperty("z",p.z());points.add(row);}j.add("points",points);return j;}
 private MapTerritoryJson(){}
}
