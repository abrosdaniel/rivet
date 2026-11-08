package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.client.Minecraft;
import java.util.*;
/** Opt-in live display. Coordinates stay in memory and expire even if a packet is lost. */
final class MapLayerClient {
 record Point(String id,CommunityLocation location,boolean player,boolean waystone,String source,String group,long endsAt){Point(String id,CommunityLocation location,boolean player){this(id,location,player,false);}Point(String id,CommunityLocation location,boolean player,boolean waystone){this(id,location,player,waystone,"","",0);}boolean activity(){return !source.isEmpty();}}
 private static List<Point> snapshot;private static JsonArray indexed;
 private static final dev.abros.rivet.core.map.MapPlaceSnapshot places=new dev.abros.rivet.core.map.MapPlaceSnapshot();
 private static final RequestSession placesSession=new RequestSession();private static boolean placesBusy,showPlaces=true;private static String server="";private static long nextPlaces;private static final Set<String> hidden=new HashSet<>();
 static void reset(){placesSession.cancel();placesBusy=false;places.clear();nextPlaces=0;snapshot=null;indexed=null;server="";hidden.clear();}
 static boolean placesShown(){return showPlaces;}static boolean hidden(String id){return hidden.contains(id);}static void hide(String id){if(!hidden.add(id))hidden.remove(id);save();snapshot=null;}static void togglePlaces(){showPlaces=!showPlaces;snapshot=null;save();refreshPlaces();}static void refreshPlaces(){nextPlaces=0;}
 private static java.nio.file.Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("rivet/map-layers.json");}
 private static void save(){try{var all=java.nio.file.Files.exists(file())?Json.read(file()):new JsonObject();var value=new JsonObject();value.addProperty("places",showPlaces);var ids=new JsonArray();hidden.forEach(ids::add);value.add("hidden",ids);all.add(server,value);Json.write(file(),all);}catch(Exception ex){ServerMenuClient.result=Client.text("ui.could_not_save_map_settings_996967ff");}}
 private static void load(){showPlaces=true;hidden.clear();try{var all=Json.read(file());if(all.has(server)){var value=all.getAsJsonObject(server);showPlaces=value.has("places")&&value.get("places").getAsBoolean();if(value.has("hidden"))for(var id:value.getAsJsonArray("hidden"))hidden.add(id.getAsString());}}catch(Exception ignored){}}
 static List<Point> points(){
  if(!WorldMapClient.allowed()||!ServerMenuClient.available()||!ServerMenuClient.supports("player-tools"))return List.of();
  var current=places.rows(System.currentTimeMillis());if(current!=indexed){indexed=current;snapshot=null;}
  if(snapshot==null){var result=new ArrayList<Point>();if(showPlaces)for(var e:current)append(result,e.getAsJsonObject());snapshot=List.copyOf(result);}return snapshot;
 }
 private static void append(List<Point> result,JsonObject row){
  try{String id=Json.str(row,"id");if(hidden(id)||!Json.opt(row,"status","open").equals("open"))return;
   var j=row.getAsJsonObject("location");var normalized=new JsonObject();normalized.addProperty("name",Json.str(row,"title"));for(String k:List.of("dimension","x","y","z"))normalized.add(k,j.get(k));
   result.add(new Point(id,CommunityLocation.read(normalized),false));
  }catch(RuntimeException ignored){/* A malformed row must not break the map. */}
 }
 static void tick(){
  var mc=Minecraft.getInstance();String current=mc.getCurrentServer()==null?"":mc.getCurrentServer().ip;
  if(!current.equals(server)){reset();server=current;load();}
  if(mc.level==null||!WorldMapClient.allowed()||!ServerMenuClient.available()||!ServerMenuClient.supports("player-tools")){placesSession.cancel();placesBusy=false;places.clear();snapshot=null;nextPlaces=0;return;}
  long now=System.currentTimeMillis();if(placesSession.timeout(now)){placesBusy=false;places.cancel();nextPlaces=now+30000;}
  if(showPlaces&&!placesBusy&&now>=nextPlaces){places.begin();requestPlaces("");}
 }
 private static void send(RequestSession session,String op,String cursor){var j=new JsonObject();j.addProperty("action","community");j.addProperty("section","home");j.addProperty("op",op);j.addProperty("cursor",cursor);ServerMenuClient.requestBackground(session.begin(j,false,System.currentTimeMillis()));}
 private static void requestPlaces(String cursor){placesBusy=true;nextPlaces=System.currentTimeMillis()+30000;send(placesSession,"toolsPlaces",cursor);}
 static boolean receive(JsonObject j){if(!placesSession.receive(j))return false;placesBusy=false;try{String next=places.accept(j,System.currentTimeMillis());if(!next.isEmpty())requestPlaces(next);}catch(RuntimeException invalid){places.cancel();nextPlaces=System.currentTimeMillis()+30000;}return true;}
}
