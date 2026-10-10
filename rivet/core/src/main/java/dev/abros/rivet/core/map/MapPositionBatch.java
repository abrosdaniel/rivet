package dev.abros.rivet.core.map;

import com.google.gson.*;
import dev.abros.rivet.core.*;
import java.util.*;

/** One atomic response assembled from ordered packets, with a fixed request-time deadline. */
public final class MapPositionBatch {
 public static final long TTL=7000;
 private final long started;private long revision=-1;private int page;private boolean complete;
 private final JsonArray pending=new JsonArray();private final Set<String> ids=new HashSet<>();
 public MapPositionBatch(long started){this.started=started;}
 public JsonArray accept(JsonObject packet,long now){
  if(complete||now<started||now-started>=TTL||page>=64)throw new IllegalArgumentException("Expired position snapshot");
  if(integer(packet,"positionPage")!=page)throw new IllegalArgumentException("Position page order");
  long stamp=integer(packet,"positionRevision");if(stamp<0||(page>0&&revision!=stamp))throw new IllegalArgumentException("Position generation changed");revision=stamp;
  var last=packet.get("positionLast");if(last==null||!last.isJsonPrimitive()||!last.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("Missing snapshot boundary");
  var rows=MapPositionPage.read(packet,true);if(rows.isEmpty()&&!last.getAsBoolean()||pending.size()+rows.size()>MapPositionPage.SNAPSHOT_LIMIT)throw new IllegalArgumentException("Position snapshot size");
  for(var row:rows){var peer=row.getAsJsonObject();String id=UUID.fromString(Json.str(peer,"uuid")).toString();if(!ids.add(id))throw new IllegalArgumentException("Duplicate position");var location=peer.deepCopy();location.remove("uuid");CommunityLocation.read(location);pending.add(peer);}
  page++;if(!last.getAsBoolean())return null;complete=true;return pending;
 }
 private static long integer(JsonObject j,String key){var value=j.get(key);if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid position integer");return value.getAsBigDecimal().longValueExact();}
}
