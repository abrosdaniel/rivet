package dev.abros.rivet.core.map;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import java.util.*;

/** Negotiated, bounded position page. Dimension names are shared within one packet. */
public final class MapPositionPage {
 public static final String FEATURE="map-position-pages";
 public static final int LEGACY_LIMIT=64,COMPACT_LIMIT=384,SNAPSHOT_LIMIT=4096,MAX_CHARS=30000;
 private static final Gson WIRE=new GsonBuilder().disableHtmlEscaping().create();
 public static String wire(JsonObject packet){return WIRE.toJson(packet);}
 private final JsonArray rows=new JsonArray(),dimensions=new JsonArray();
 private final Map<String,Integer> indices=new HashMap<>();private int chars=128;
 public boolean add(JsonObject peer){
  String dimension=Json.str(peer,"dimension");Integer index=indices.get(dimension);boolean fresh=index==null;if(fresh)index=dimensions.size();
  var row=new JsonArray();row.add(peer.get("uuid"));row.add(peer.get("name"));row.add(index);for(String key:List.of("x","y","z"))row.add(peer.get(key));
  int cost=WIRE.toJson(row).length()+1+(fresh?WIRE.toJson(new JsonPrimitive(dimension)).length()+1:0);
  if(rows.size()>=COMPACT_LIMIT||chars+cost>MAX_CHARS)return false;
  if(fresh){indices.put(dimension,index);dimensions.add(dimension);}rows.add(row);chars+=cost;return true;
 }
 public int size(){return rows.size();}
 public void write(JsonObject out){out.remove("peers");out.add("positionRows",rows);out.add("positionDimensions",dimensions);}
 public static List<JsonObject> split(JsonObject envelope,JsonArray peers){
  if(peers.size()>SNAPSHOT_LIMIT)throw new IllegalArgumentException("Position snapshot too large");
  var pages=new ArrayList<JsonObject>();var page=new MapPositionPage();
  for(var peer:peers){if(!page.add(peer.getAsJsonObject())){if(page.size()==0)throw new IllegalArgumentException("Position exceeds packet budget");var out=envelope.deepCopy();page.write(out);pages.add(out);page=new MapPositionPage();if(!page.add(peer.getAsJsonObject()))throw new IllegalArgumentException("Position exceeds packet budget");}}
  var out=envelope.deepCopy();page.write(out);pages.add(out);
  for(int i=0;i<pages.size();i++){pages.get(i).addProperty("positionPage",i);pages.get(i).addProperty("positionLast",i==pages.size()-1);if(wire(pages.get(i)).length()>32767)throw new IllegalArgumentException("Position packet too large");}
  return List.copyOf(pages);
 }
 public static JsonArray read(JsonObject packet,boolean negotiated){
  if(!packet.has("positionRows")&&!packet.has("positionDimensions")){var legacy=packet.getAsJsonArray("peers");if(legacy==null||legacy.size()>LEGACY_LIMIT)throw new IllegalArgumentException("Invalid legacy position page");return legacy;}
  if(!negotiated)throw new IllegalArgumentException("Position page feature not negotiated");
  var rows=packet.getAsJsonArray("positionRows");var dimensions=packet.getAsJsonArray("positionDimensions");
  if(packet.has("peers")||rows==null||dimensions==null||rows.size()>COMPACT_LIMIT||dimensions.size()>COMPACT_LIMIT)throw new IllegalArgumentException("Invalid compact position page");
  var result=new JsonArray();for(var element:rows){var row=element.getAsJsonArray();if(row.size()!=6)throw new IllegalArgumentException("Invalid position tuple");var value=row.get(2);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Invalid dimension index");int index=value.getAsBigDecimal().intValueExact();if(index<0||index>=dimensions.size())throw new IllegalArgumentException("Invalid dimension index");var peer=new JsonObject();peer.add("uuid",row.get(0));peer.add("name",row.get(1));peer.add("dimension",dimensions.get(index));for(int i=0;i<3;i++)peer.add(List.of("x","y","z").get(i),row.get(i+3));result.add(peer);}return result;
 }
 public MapPositionPage(){}
}
