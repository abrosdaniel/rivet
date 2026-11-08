package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.CommunityLocation;
import dev.abros.rivet.core.map.MapMarker;
import dev.abros.rivet.core.map.MapTerritory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;

/** Local search never uploads personal markers. Activation resolves a fresh authorized snapshot. */
final class MenuPlaces {
 private record Place(String id,String label,CommunityLocation location,MapTerritory territory){
  Place(String id,String label,CommunityLocation location){this(id,label,location,null);}
  String name(){return territory==null?location.name():territory.name();}
 }
 private static List<Place> available(){
  if(!WorldMapClient.ready())return List.of();
  var result=new ArrayList<Place>();
  if(WorldMapClient.markersReady())for(var marker:WorldMapClient.markers())result.add(marker("personal:","search.place.personal",marker));
  for(var marker:MapPublications.markers())result.add(marker("public:","search.place.public",marker));
  for(var point:MapLayerClient.points())if(!point.player())result.add(new Place("place:"+point.id(),Client.text("search.place.public"),point.location()));
  for(var point:MapActivities.points())result.add(new Place("group-point:"+point.group()+":"+point.id(),Client.text("search.place.groupMarker"),point.location()));
  for(var point:MapWaystones.points())result.add(new Place("waystone:"+point.id(),Client.text("search.place.waystone"),point.location()));
  for(var area:WorldMapClient.territories())result.add(area("personal-area:"+area.id(),Client.text("search.place.personal"),area));
  for(var area:MapPublications.territories())result.add(area("public-area:"+area.id(),Client.text("search.place.public"),area));
  for(var group:MapGroupClient.groups())if(group.territory()!=null)result.add(area("group-area:"+group.id(),Client.text("search.place.group"),group.territory()));
  var titles=new HashMap<String,String>();for(var layer:MapIntegrationLayers.layers())titles.put(layer.id(),layer.title());
  for(var object:MapIntegrationLayers.searchObjects()){
   String id="adapter:"+object.layer()+"|"+object.id(),label=Client.text("search.place.adapter")+" · "+titles.getOrDefault(object.layer(),object.layer());
   if(object.territory()!=null)result.add(area(id,label,object.territory()));
   else if(object.marker()!=null){var m=object.marker();result.add(new Place(id,label,new CommunityLocation(m.name(),m.dimension(),m.x(),m.y(),m.z(),false)));}
  }
  return result;
 }
 private static Place area(String id,String label,MapTerritory area){return new Place(id,label,null,area);}
 private static Place marker(String prefix,String label,MapMarker marker){return new Place(prefix+marker.id(),Client.text(label),new CommunityLocation(marker.name(),marker.dimension(),marker.x(),marker.y(),marker.z(),false));}
 static JsonArray search(String query){var rows=query.strip().length()<2?new JsonArray():browse(query,"all",0);if(rows.size()>50)rows.remove(50);return rows;}
 static JsonArray browse(String query,String source,int offset){
  var rows=new JsonArray();String needle=query.strip().toLowerCase(Locale.ROOT);int skipped=0;
  for(var place:available())if(matches(place.id,source)&&place.name().toLowerCase(Locale.ROOT).contains(needle)){
   if(skipped++<offset)continue;
   var row=new JsonObject();row.addProperty("section","map-place");row.addProperty("id",place.id);row.addProperty("title",place.name());row.addProperty("label",place.label);row.addProperty("dimension",place.territory==null?place.location.dimension():place.territory.dimension());rows.add(row);if(rows.size()>=51)break;
  }
  return rows;
 }
 private static boolean matches(String id,String source){return switch(source){case "personal"->id.startsWith("personal:")||id.startsWith("personal-area:");case "public"->id.startsWith("public:")||id.startsWith("public-area:")||id.startsWith("place:");case "groups"->id.startsWith("group-point:")||id.startsWith("group-area:");case "adapters"->id.startsWith("adapter:")||id.startsWith("waystone:");default->true;};}
 static boolean open(Screen parent,String id){
  var place=available().stream().filter(p->p.id.equals(id)).findFirst();if(place.isEmpty())return false;
  var map=new WorldMapScreen(parent);Minecraft.getInstance().setScreen(map);if(place.get().territory!=null)map.focusTerritory(place.get().territory);else map.focusLocation(place.get().location);return true;
 }
 private MenuPlaces(){}
}
