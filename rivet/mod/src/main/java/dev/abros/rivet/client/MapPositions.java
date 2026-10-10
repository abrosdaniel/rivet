package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.network.Protocol;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Atomic, short-lived live positions. Never persisted as markers or terrain. */
final class MapPositions {
 private static final RequestSession session=new RequestSession();private static final List<MapLayerClient.Point> pending=new ArrayList<>();private static List<MapLayerClient.Point> points=List.of();private static final Set<String> cursors=new HashSet<>();private static long next,expires,started,revision=-1;private static String cursor="";
 private static dev.abros.rivet.core.map.MapPositionBatch batch;private static String batchRequest="";
 private static final dev.abros.rivet.core.map.MapPositionPoll poll=new dev.abros.rivet.core.map.MapPositionPoll();
 private static MapLayerClient.Point route;
 static void navigate(MapLayerClient.Point point){route=point;DirectionCue.start(point.location());}
 private static void updateRoute(){if(route==null)return;if(!Objects.equals(DirectionCue.target(),route.location())){route=null;return;}var updated=points().stream().filter(p->p.id().equals(route.id())).findFirst();if(updated.isEmpty()){DirectionCue.clear();route=null;}else{route=updated.get();DirectionCue.start(route.location());}}
 static boolean supported(){return ServerMenuClient.supports("map-positions");}
 static boolean available(){return WorldMapClient.allowed()&&ServerMenuClient.available()&&supported()&&ServerMenuClient.state.has("map")&&ServerMenuClient.state.getAsJsonObject("map").has("positions")&&ServerMenuClient.state.getAsJsonObject("map").get("positions").getAsBoolean();}
 static List<MapLayerClient.Point> points(){return available()&&System.currentTimeMillis()<expires?points:List.of();}
 static void reset(){clearSnapshot();poll.reset();}
 private static void clearSnapshot(){if(route!=null&&Objects.equals(DirectionCue.target(),route.location()))DirectionCue.clear();route=null;session.cancel();batch=null;batchRequest="";points=List.of();pending.clear();cursors.clear();next=expires=started=0;revision=-1;cursor="";}
 static void tick(){long now=System.currentTimeMillis();if(!available()||Minecraft.getInstance().player==null){reset();return;}if(now>=expires)points=List.of();updateRoute();if(!MapLayers.visible(MapLayers.Layer.PLAYERS,false)&&route==null){reset();return;}if((started>0&&now-started>=7000&&(session.pending()||batch!=null||!cursor.isEmpty()))||session.timeout(now)){retry(now);}if(next==0)next=now+dev.abros.rivet.core.map.MapPositionPoll.initialDelay(Minecraft.getInstance().player.getUUID());if(!session.pending()&&batch==null&&now>=next){if(cursor.isEmpty()){pending.clear();cursors.clear();started=now;revision=-1;}var q=new JsonObject();q.addProperty("action","community");q.addProperty("section","home");q.addProperty("op","mapPositionPeers");q.addProperty("cursor",cursor);var request=session.begin(q,false,now);if(ServerMenuClient.supports(dev.abros.rivet.core.map.MapPositionPage.FEATURE)){batch=new dev.abros.rivet.core.map.MapPositionBatch(started);batchRequest=Json.str(request,"request");}if(ServerMenuClient.previewTransport!=null)ServerMenuClient.previewTransport.accept(request);else PacketDistributor.sendToServer(new Protocol.FeatureRequest(Json.GSON.toJson(request)));}}
 static boolean receive(JsonObject j){
  if(Json.opt(j,"kind","").equals("mapPositionsChanged")){reset();return true;}
  boolean fragment=batch!=null&&batchRequest.equals(Json.opt(j,"request",""));if(!fragment&&!session.receive(j))return false;
  long now=System.currentTimeMillis();try{
   if(!available()||j.has("error")||j.has("positionStale")||now-started>=7000)throw new IllegalArgumentException();
   if(batch!=null){var rows=batch.accept(j,now);if(rows==null)return true;session.receive(j);points=decode(rows);batch=null;batchRequest="";expires=started+7000;next=now+1000;poll.reset();updateRoute();return true;}
   long stamp=j.get("positionRevision").getAsLong();if(revision>=0&&stamp!=revision)throw new IllegalArgumentException();revision=stamp;
   pending.addAll(decode(dev.abros.rivet.core.map.MapPositionPage.read(j,false)));if(pending.size()>4096)throw new IllegalArgumentException();
   cursor=Json.opt(j,"nextCursor","");if(!cursor.isEmpty()){UUID.fromString(cursor);if(!cursors.add(cursor))throw new IllegalArgumentException();next=now+300;}
   else{points=List.copyOf(pending);pending.clear();expires=started+7000;next=now+1000;poll.reset();updateRoute();}
  }catch(RuntimeException ex){if(fragment)session.receive(j);retry(now);}return true;
 }
 private static void retry(long now){if(Minecraft.getInstance().player==null){reset();return;}long delay=poll.failed(Minecraft.getInstance().player.getUUID());clearSnapshot();next=now+delay;}
 private static List<MapLayerClient.Point> decode(JsonArray rows){var result=new ArrayList<MapLayerClient.Point>();for(var element:rows){var row=element.getAsJsonObject();String id=UUID.fromString(Json.str(row,"uuid")).toString();var location=new JsonObject();location.addProperty("name",Json.str(row,"name"));for(String key:List.of("dimension","x","y","z"))location.add(key,row.get(key));result.add(new MapLayerClient.Point(id,CommunityLocation.read(location),true));}return List.copyOf(result);}
 private MapPositions(){}
}
