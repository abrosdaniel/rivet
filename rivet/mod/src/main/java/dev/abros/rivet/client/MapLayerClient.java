package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.client.Minecraft;
import java.util.*;
/** Opt-in live display. Coordinates stay in memory and expire even if a packet is lost. */
final class MapLayerClient {
 record Point(String id,CommunityLocation location,boolean player,boolean waystone,String source,String group,long endsAt){Point(String id,CommunityLocation location,boolean player){this(id,location,player,false);}Point(String id,CommunityLocation location,boolean player,boolean waystone){this(id,location,player,waystone,"","",0);}boolean activity(){return !source.isEmpty();}}
 private static List<Point> snapshot;
 private static final RequestSession placesSession=new RequestSession(),peersSession=new RequestSession();private static JsonArray places=new JsonArray(),pendingPlaces=new JsonArray(),peers=new JsonArray();private static boolean placesBusy,peersBusy,showPlaces=true,showPeers;private static String server="";private static long nextPlaces,nextPeers,peersExpire;private static final Set<String> hidden=new HashSet<>();
 static void reset(){placesSession.cancel();peersSession.cancel();placesBusy=peersBusy=false;places=new JsonArray();peers=new JsonArray();pendingPlaces=new JsonArray();nextPlaces=nextPeers=peersExpire=0;snapshot=null;server="";hidden.clear();}
 static boolean placesShown(){return showPlaces;}static boolean peersShown(){return showPeers;}static boolean hidden(String id){return hidden.contains(id);}static void hide(String id){if(!hidden.add(id))hidden.remove(id);save();snapshot=null;}static void togglePlaces(){showPlaces=!showPlaces;snapshot=null;save();refreshPlaces();}static void togglePeers(){showPeers=!showPeers;save();nextPeers=0;peers=new JsonArray();snapshot=null;}static void refreshPlaces(){nextPlaces=0;}
 private static java.nio.file.Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("rivet/map-layers.json");}
 private static void save(){try{var all=java.nio.file.Files.exists(file())?Json.read(file()):new JsonObject();var value=new JsonObject();value.addProperty("places",showPlaces);value.addProperty("peers",showPeers);var ids=new JsonArray();hidden.forEach(ids::add);value.add("hidden",ids);all.add(server,value);Json.write(file(),all);}catch(Exception ex){ServerMenuClient.result=Client.text("ui.could_not_save_map_settings_996967ff");}}
 private static void load(){showPlaces=true;showPeers=false;hidden.clear();try{var all=Json.read(file());if(all.has(server)){var value=all.getAsJsonObject(server);showPlaces=value.has("places")&&value.get("places").getAsBoolean();showPeers=value.has("peers")&&value.get("peers").getAsBoolean();if(value.has("hidden"))for(var id:value.getAsJsonArray("hidden"))hidden.add(id.getAsString());}}catch(Exception ignored){}}
 static boolean peersAvailable(){return false;}
 static List<Point> points(){
  if(!WorldMapClient.allowed()||!ServerMenuClient.available()||!ServerMenuClient.supports("player-tools"))return List.of();
  if(!peersAvailable()&&!peers.isEmpty()){peers=new JsonArray();snapshot=null;}
  if(System.currentTimeMillis()>peersExpire&&!peers.isEmpty()){peers=new JsonArray();snapshot=null;}
  if(snapshot==null){var result=new ArrayList<Point>();if(showPlaces)for(var e:places)append(result,e.getAsJsonObject(),false);if(showPeers)for(var e:peers)append(result,e.getAsJsonObject(),true);snapshot=List.copyOf(result);}return snapshot;
 }
 private static void append(List<Point> result,JsonObject row,boolean player){
  try{String id=Json.str(row,player?"uuid":"id");if(player)UUID.fromString(id);if(!player&&(hidden(id)||!Json.opt(row,"status","open").equals("open")))return;
   var j=(player?row:row.getAsJsonObject("location")).deepCopy();var normalized=new JsonObject();normalized.addProperty("name",Json.str(row,player?"name":"title"));for(String k:List.of("dimension","x","y","z"))normalized.add(k,j.get(k));
   result.add(new Point(id,CommunityLocation.read(normalized),player));
  }catch(RuntimeException ignored){/* A malformed row must not break the map. */}
 }
 static void tick(){
  var mc=Minecraft.getInstance();String current=mc.getCurrentServer()==null?"":mc.getCurrentServer().ip;
  if(!current.equals(server)){server=current;placesSession.cancel();peersSession.cancel();placesBusy=peersBusy=false;places=new JsonArray();peers=new JsonArray();pendingPlaces=new JsonArray();nextPlaces=nextPeers=0;snapshot=null;load();}
  if(mc.level==null||!WorldMapClient.allowed()||!ServerMenuClient.available()||!ServerMenuClient.supports("player-tools")){placesSession.cancel();peersSession.cancel();placesBusy=peersBusy=false;places=new JsonArray();peers=new JsonArray();snapshot=null;return;}
  long now=System.currentTimeMillis();if(placesSession.timeout(now)){placesBusy=false;nextPlaces=now+30000;}if(peersSession.timeout(now)){peersBusy=false;nextPeers=now+5000;}
  if(showPlaces&&!placesBusy&&now>=nextPlaces){pendingPlaces=new JsonArray();requestPlaces("");}
  if(!peersAvailable()){peersSession.cancel();peersBusy=false;peers=new JsonArray();snapshot=null;}
  if(peersAvailable()&&showPeers&&!peersBusy&&now>=nextPeers){peersBusy=true;nextPeers=now+3000;send(peersSession,"toolsMapPeers","");}
  if(now>peersExpire&&!peers.isEmpty()){peers=new JsonArray();snapshot=null;}
 }
 private static void send(RequestSession session,String op,String cursor){var j=new JsonObject();j.addProperty("action","community");j.addProperty("section","home");j.addProperty("op",op);j.addProperty("cursor",cursor);ServerMenuClient.requestBackground(session.begin(j,false,System.currentTimeMillis()));}
 private static void requestPlaces(String cursor){placesBusy=true;nextPlaces=System.currentTimeMillis()+30000;send(placesSession,"toolsPlaces",cursor);}
 static boolean receive(JsonObject j){if(placesSession.receive(j)){placesBusy=false;if(j.has("places")){pendingPlaces.addAll(j.getAsJsonArray("places"));String next=Json.opt(j,"nextCursor","");if(!next.isEmpty()&&pendingPlaces.size()<1000)requestPlaces(next);else{if(!places.equals(pendingPlaces)){places=pendingPlaces;snapshot=null;}}}return true;}if(peersSession.receive(j)){peersBusy=false;if(peersAvailable()&&j.has("peers")){peers=j.getAsJsonArray("peers");peersExpire=System.currentTimeMillis()+7000;snapshot=null;}return true;}return false;}
}
