package dev.abros.rivet.core.map;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import java.util.*;

/** Bounded atomic public-place pages. A failed refresh never extends the old snapshot's lifetime. */
public final class MapPlaceSnapshot {
 private JsonArray current=new JsonArray(),pending=new JsonArray();
 private final Set<String> cursors=new HashSet<>(),ids=new HashSet<>();
 private long expires;private boolean reading;
 public void begin(){pending=new JsonArray();cursors.clear();ids.clear();reading=true;}
 public void cancel(){pending=new JsonArray();cursors.clear();ids.clear();reading=false;}
 public void clear(){cancel();current=new JsonArray();expires=0;}
 /** Returns the next cursor, or empty after committing an entire bounded result. */
 public String accept(JsonObject response,long now){
  if(!reading)throw new IllegalStateException("No active place snapshot");
  try{
   if(response.has("error")||!response.has("places"))throw new IllegalArgumentException("Missing place page");
   var rows=response.getAsJsonArray("places");if(rows.size()>20)throw new IllegalArgumentException("Oversized place page");
   for(var value:rows){var row=value.getAsJsonObject();String id=UUID.fromString(Json.str(row,"id")).toString();if(!ids.add(id))throw new IllegalArgumentException("Duplicate place");pending.add(row.deepCopy());}
   String next=Json.opt(response,"nextCursor","");
   if(!next.isEmpty()){
    UUID.fromString(next);if(rows.isEmpty()||!next.equals(Json.str(rows.get(rows.size()-1).getAsJsonObject(),"id"))||!cursors.add(next))throw new IllegalArgumentException("Invalid place cursor");
    if(pending.size()<1000)return next;
   }
   if(!current.equals(pending))current=pending;expires=now+60000;cancel();return "";
  }catch(RuntimeException invalid){cancel();throw invalid;}
 }
 /** Stable reference until content changes or expires; consumers may cache derived geometry. */
 public JsonArray rows(long now){if(now>=expires&&!current.isEmpty())current=new JsonArray();return current;}
}
