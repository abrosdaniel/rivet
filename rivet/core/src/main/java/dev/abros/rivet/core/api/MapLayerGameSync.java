package dev.abros.rivet.core.api;

import com.google.gson.*;
import dev.abros.rivet.api.DomainData;
import dev.abros.rivet.core.Json;
import java.util.*;

/** Per-connection bounded delta stream. Removed access resets before any replacement data. */
public final class MapLayerGameSync {
 public static final String FEATURE="map-integration-layers";
 private final Map<String,Long> sent=new HashMap<>();
 private final String epoch=UUID.randomUUID().toString();
 private final ArrayDeque<Item> pending=new ArrayDeque<>();
 private long revision=-1,sequence,last;private boolean reset=true;
 private record Item(String key,long revision,dev.abros.rivet.api.MapLayersApi.LayerView layer,dev.abros.rivet.api.MapLayersApi.ObjectView object){
  JsonObject row(){var row=new JsonObject();row.addProperty("layer",layer.id());if(object==null)row.addProperty("title",layer.title());else{row.addProperty("id",object.id());row.addProperty("type",object.shape() instanceof DomainData.Marker?"marker":"territory");row.add("shape",Json.GSON.toJsonTree(object.shape()));}return row;}
 }
 public JsonObject next(MapLayerRegistry registry,UUID viewer,long now){
  if(registry.revision()!=revision){
   var snapshot=registry.visible(viewer);var target=new LinkedHashMap<String,Item>();
   for(var value:snapshot.layers()){
    var layer=value.layer();target.put(layer.id(),new Item(layer.id(),layer.revision(),layer,null));
    for(var object:value.objects()){
     String key=layer.id()+"|"+object.id();target.put(key,new Item(key,object.revision(),layer,object));
    }
   }
   if(!target.keySet().containsAll(sent.keySet())){sent.clear();reset=true;}
   pending.clear();for(var item:target.values())if(!Objects.equals(sent.get(item.key()),item.revision()))pending.add(item);
   revision=snapshot.revision();
  }
  if(!reset&&pending.isEmpty()&&now-last<3000)return null;
  var packet=new JsonObject();packet.addProperty("kind",FEATURE);packet.addProperty("epoch",epoch);packet.addProperty("sequence",sequence++);packet.addProperty("reset",reset);reset=false;
  var rows=new JsonArray();int bytes=0;
  while(!pending.isEmpty()&&rows.size()<8){var item=pending.peek();var row=item.row();int size=row.toString().length();if(bytes+size>24000)break;pending.remove();rows.add(row);sent.put(item.key(),item.revision());bytes+=size;}
  packet.add("rows",rows);last=now;return packet;
 }
}
