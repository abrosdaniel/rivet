package dev.abros.rivet.core;
import com.google.gson.JsonObject;
import java.util.Set;
/** Fixed, bounded rate-limit lanes. Payload IDs cannot create additional lanes. */
public final class MenuRequestLane {
 private static final Set<String> MAP_READS=Set.of("groupMap","mapActivities","mapObjects","toolsPlaces");
 private MenuRequestLane(){}
 public static String key(JsonObject query,boolean negotiated){
  String action=Json.opt(query,"action","");
  return negotiated&&action.equals("community")&&MAP_READS.contains(Json.opt(query,"op",""))&&MenuRequests.read(query)?"mapData":action;
 }
}
