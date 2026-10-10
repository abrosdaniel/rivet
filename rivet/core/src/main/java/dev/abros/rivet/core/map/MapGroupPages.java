package dev.abros.rivet.core.map;
import com.google.gson.*;
import dev.abros.rivet.core.Json;
import java.util.*;
/** A single request delivers a bounded snapshot without inter-page request throttling. */
public final class MapGroupPages {
 public static final String FEATURE="group-map-pages";
 public static List<JsonObject> split(JsonObject result){
  var rows=result.getAsJsonArray("territories");if(rows.size()>4096)throw new IllegalArgumentException("Too many groups");
  var envelope=result.deepCopy();envelope.remove("territories");envelope.remove("nextCursor");var pages=new ArrayList<JsonObject>();var page=new JsonArray();
  for(var row:rows){
   page.add(row);var candidate=envelope.deepCopy();candidate.add("territories",page);
   if(Json.GSON.toJson(candidate).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>30000){
    page.remove(page.size()-1);if(page.isEmpty())throw new IllegalArgumentException("Territory too large");
    var full=envelope.deepCopy();full.add("territories",page);pages.add(full);page=new JsonArray();page.add(row);
   }
  }
  var packet=envelope.deepCopy();packet.add("territories",page);pages.add(packet);
  for(int n=0;n<pages.size();n++){pages.get(n).addProperty("groupPage",n);pages.get(n).addProperty("groupLast",n==pages.size()-1);}
  return List.copyOf(pages);
 }
 private MapGroupPages(){}
}
