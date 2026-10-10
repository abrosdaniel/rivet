package dev.abros.rivet.client;

import com.google.gson.JsonObject;
import java.util.*;

/** Shared visibility for both map renderers. Does not grant access to any source. */
final class MapLayers {
 enum Layer { MARKERS,TERRITORIES,DEATHS,TASKS,EVENTS,GROUPMARKERS,PLACES,WAYSTONES,PLAYERS,PEERS,MOBS,ROUTE;
  String key(){return "map.layer."+name().toLowerCase(Locale.ROOT);}
 }
 private final EnumMap<Layer,Boolean> values=new EnumMap<>(Layer.class);
 MapLayers(){for(var layer:Layer.values())values.put(layer,true);}
 boolean shown(Layer layer,boolean unused){return values.get(layer);}
 void set(Layer layer,boolean unused,boolean shown){values.put(layer,shown);}
 void load(JsonObject j){if(j==null)return;for(var layer:Layer.values())if(j.has(layer.name())){var v=j.get(layer.name());if(v.isJsonObject()){var row=v.getAsJsonObject();values.put(layer,(!row.has("map")||row.get("map").getAsBoolean())&&(!row.has("mini")||row.get("mini").getAsBoolean()));}else values.put(layer,v.getAsBoolean());}}
 JsonObject json(){var j=new JsonObject();for(var layer:Layer.values())j.addProperty(layer.name(),values.get(layer));return j;}
 static boolean available(Layer layer){return switch(layer){case TASKS,EVENTS,GROUPMARKERS->MapActivities.available(layer.name().toLowerCase(Locale.ROOT));case TERRITORIES->MapGroupClient.available();case PEERS->false;case PLAYERS->MapPositions.supported()?MapPositions.available():WorldMapClient.allowed();case PLACES->WorldMapClient.allowed()&&ServerMenuClient.available()&&ServerMenuClient.supports("player-tools");default->WorldMapClient.allowed();};}
 static boolean visible(Layer layer,boolean unused){var s=MapSettings.INSTANCE;var r=MapRenderSettings.INSTANCE;return available(layer)&&s.layers.shown(layer,false)&&switch(layer){case DEATHS->s.deathMinimap&&s.deathMap;case ROUTE->s.hud.directionMap;case MOBS->r.radar&&r.radarWorld;case PLAYERS->r.players&&(MapPositions.supported()||r.radar&&r.radarWorld);case PLACES->MapLayerClient.placesShown();case PEERS->MapLayerClient.peersShown();default->true;};}
 static void toggle(Layer layer,boolean unused){
  if(!available(layer))return;var s=MapSettings.INSTANCE;var r=MapRenderSettings.INSTANCE;boolean next=!visible(layer,false);
  if(next&&(layer==Layer.PLAYERS||layer==Layer.MOBS))for(var v:java.util.List.of(Layer.PLAYERS,Layer.MOBS))s.layers.set(v,false,visible(v,false));
  s.layers.set(layer,false,next);
  if(next)switch(layer){case DEATHS->s.deathMinimap=s.deathMap=true;case ROUTE->s.hud.directionMap=true;case MOBS->r.radar=r.radarWorld=true;case PLAYERS->{r.players=true;if(!MapPositions.supported())r.radar=r.radarWorld=true;}case PLACES->{if(!MapLayerClient.placesShown())MapLayerClient.togglePlaces();}case PEERS->{if(!MapLayerClient.peersShown())MapLayerClient.togglePeers();}default->{}}
  s.save();
 }
}
