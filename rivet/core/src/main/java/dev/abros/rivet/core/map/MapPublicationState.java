package dev.abros.rivet.core.map;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import java.util.*;

/** Atomic page replacement with revision-ordered live corrections and a bounded liveness lease. */
public final class MapPublicationState {
 private Map<String,JsonObject> visible=new LinkedHashMap<>(),pending=new LinkedHashMap<>(),live=new LinkedHashMap<>();
 private long snapshot=-1,reading=-1,expires,generation;private boolean delta;
 public long snapshot(){return snapshot;}
 public long reading(){return reading;}
 public long generation(){return generation;}
 public Collection<JsonObject> rows(){return visible.values().stream().map(JsonObject::deepCopy).toList();}
 public boolean fresh(long now){return now<expires;}
 public void reset(){visible.clear();pending.clear();live.clear();snapshot=reading=-1;expires=0;generation++;}
 public void begin(){pending.clear();reading=-1;}
 public void restart(){begin();snapshot=-1;}
 private static String id(JsonObject row){String id=UUID.fromString(Json.str(row,"id")).toString();if(!id.equals(Json.str(row,"id")))throw new IllegalArgumentException("Non-canonical map id");return id;}
 private static void apply(Map<String,JsonObject> target,JsonObject row){String id=id(row);if(row.has("removed")&&row.get("removed").getAsBoolean())target.remove(id);else target.put(id,row.deepCopy());}
 public void changed(JsonObject row){
  String id=id(row);long revision=row.get("revision").getAsBigDecimal().longValueExact();
  if(revision<=snapshot)return;var old=live.get(id);if(old!=null&&old.get("revision").getAsLong()>=revision)return;
  if(!live.containsKey(id)&&live.size()>=MapPublicationLimits.TOTAL){reset();return;}
  live.put(id,row.deepCopy());if(snapshot>=0||row.has("removed")){apply(visible,row);if(visible.size()>MapPublicationLimits.TOTAL){reset();return;}generation++;}
 }
 /** Returns true only after a complete snapshot/delta or unchanged acknowledgement. */
 public boolean page(JsonObject packet,long now){
  if(packet.has("error"))throw new IllegalArgumentException("Map read failed");
  long version=packet.get("snapshot").getAsBigDecimal().longValueExact();
  if(packet.has("unchanged")){if(version!=snapshot)throw new IllegalArgumentException("Unexpected unchanged map version");expires=now+30000;return true;}
  boolean incremental=packet.has("delta")&&packet.get("delta").getAsBoolean();
  if(reading<0){reading=version;delta=incremental;}else if(reading!=version||delta!=incremental)throw new IllegalArgumentException("Mixed map pages");
  if(version<snapshot||incremental&&snapshot<0)throw new IllegalArgumentException("Invalid map base");
  for(var entry:packet.getAsJsonArray("mapObjects")){var row=entry.getAsJsonObject();if(pending.putIfAbsent(id(row),row.deepCopy())!=null)throw new IllegalArgumentException("Repeated map row");}
  if(pending.size()>MapPublicationLimits.TOTAL)throw new IllegalArgumentException("Map capacity");
  String cursor=Json.opt(packet,"nextCursor","");if(!cursor.isEmpty()){if(pending.isEmpty()||!new ArrayList<>(pending.keySet()).getLast().equals(cursor))throw new IllegalArgumentException("Invalid map cursor");expires=now+30000;return false;}
  Map<String,JsonObject> next=delta?new LinkedHashMap<>(visible):new LinkedHashMap<>();
  for(var row:pending.values())apply(next,row);
  for(var row:live.values())if(row.get("revision").getAsLong()>version)apply(next,row);
  if(next.size()>MapPublicationLimits.TOTAL)throw new IllegalArgumentException("Map capacity");
  if(!next.equals(visible)){visible=next;generation++;}snapshot=version;pending.clear();reading=-1;live.values().removeIf(row->row.get("revision").getAsLong()<=version);expires=now+30000;return true;
 }
}
