package dev.abros.rivet.core.map;
import com.google.gson.*;
import dev.abros.rivet.api.DomainData;
import dev.abros.rivet.core.Json;
import java.util.*;

/** Connection-local data only. Gaps fail closed and require a new server snapshot. */
public final class MapIntegrationState {
 public record Layer(String id,String title){}
 public record ObjectView(String layer,String id,DomainData.Marker marker,MapTerritory territory){}
 private final Map<String,Layer> layers=new TreeMap<>();
 private final Map<String,ObjectView> objects=new TreeMap<>();
 private String epoch="";private long sequence=-1,expires;
 private List<Layer> layerView=List.of();private List<ObjectView> objectView=List.of();
 public void clear(){layers.clear();objects.clear();layerView=List.of();objectView=List.of();epoch="";sequence=-1;expires=0;}
 public boolean fresh(long now){return !epoch.isEmpty()&&now<expires;}
 public List<Layer> layers(long now){return fresh(now)?layerView:List.of();}
 public List<ObjectView> objects(long now){return fresh(now)?objectView:List.of();}
 public void receive(JsonObject packet,long now){
  try{
   if(packet.has("clear")){clear();return;}
   String incoming=UUID.fromString(Json.str(packet,"epoch")).toString();long next=packet.get("sequence").getAsLong();boolean reset=packet.get("reset").getAsBoolean();
   if(!incoming.equals(epoch)){if(!reset||next!=0)throw new IllegalArgumentException("Missing initial snapshot");clear();epoch=incoming;}
   if(next!=sequence+1)throw new IllegalArgumentException("Missing layer update");
   if(reset){layers.clear();objects.clear();}
   var rows=packet.getAsJsonArray("rows");if(rows.size()>8)throw new IllegalArgumentException("Too many updates");
   for(var value:rows){var row=value.getAsJsonObject();String layer=Json.str(row,"layer");if(!layer.matches("[a-z][a-z0-9_]{1,63}:[a-z0-9_][a-z0-9_.-]{0,63}"))throw new IllegalArgumentException("Invalid layer");
    if(!row.has("id")){String title=Json.str(row,"title");if(title.isBlank()||title.length()>100)throw new IllegalArgumentException("Invalid layer title");layers.put(layer,new Layer(layer,title));}
    else {String id=Json.str(row,"id");if(!layers.containsKey(layer)||!id.matches("[a-z0-9_][a-z0-9_./-]{0,63}"))throw new IllegalArgumentException("Invalid object");var shape=row.getAsJsonObject("shape");String key=layer+"|"+id;
     if(Json.str(row,"type").equals("marker")){var m=Json.GSON.fromJson(shape,DomainData.Marker.class);if(m.name()==null||m.name().length()>100||m.icon()==null||m.icon().length()>64||m.dimension()==null||!m.dimension().matches("[a-z0-9_.-]+:[a-z0-9_./-]+")||Math.abs((long)m.x())>30000000||Math.abs((long)m.z())>30000000)throw new IllegalArgumentException("Invalid marker");objects.put(key,new ObjectView(layer,id,m,null));}
     else if(Json.str(row,"type").equals("territory"))objects.put(key,new ObjectView(layer,id,null,MapTerritoryJson.read(shape,UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)))));
     else throw new IllegalArgumentException("Invalid shape");
    }
    if(layers.size()>256||objects.size()>8192)throw new IllegalArgumentException("Layer capacity exceeded");
   }
   sequence=next;expires=now+12000;if(reset||!rows.isEmpty()){layerView=List.copyOf(layers.values());objectView=List.copyOf(objects.values());}
  }catch(RuntimeException bad){clear();throw bad;}
 }
}
