package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.map.*;
import java.util.*;

/** Public map data stays client-cached; only approved object changes cross the network. */
final class MapPublications {
 private static final RequestSession session=new RequestSession();
 private static final MapPublicationState state=new MapPublicationState();
 private static List<MapMarker> markers=List.of();private static List<MapTerritory> territories=List.of();private static long next,generation=-1;
 static boolean available(){return WorldMapClient.allowed()&&ServerMenuClient.supports("map-publications");}
 static List<MapMarker> markers(){return available()&&state.fresh(System.currentTimeMillis())?markers:List.of();}
 static List<MapTerritory> territories(){return available()&&state.fresh(System.currentTimeMillis())?territories:List.of();}
 static void reset(){session.cancel();state.reset();markers=List.of();territories=List.of();next=0;generation=-1;}
 static void refresh(){next=0;}
 static void tick(){if(!available()){reset();return;}long now=System.currentTimeMillis();if(session.timeout(now)){state.begin();next=now+5000;}if(!session.pending()&&now>=next){state.begin();request("");}}
 private static void request(String cursor){var j=new JsonObject();j.addProperty("action","community");j.addProperty("section","home");j.addProperty("op","mapObjects");j.addProperty("cursor",cursor);
  if(ServerMenuClient.supports("map-publication-deltas")){j.addProperty("deltaProtocol",true);j.addProperty("since",state.snapshot());j.addProperty("until",state.reading());}
  else j.addProperty("snapshot",cursor.isEmpty()?state.snapshot():state.reading());
  next=System.currentTimeMillis()+10000;ServerMenuClient.requestBackground(session.begin(j,false,System.currentTimeMillis()));
 }
 private static void update(){if(generation==state.generation())return;var points=new ArrayList<MapMarker>();var areas=new ArrayList<MapTerritory>();for(var row:state.rows()){var id=UUID.fromString(Json.str(row,"id"));var body=row.getAsJsonObject("published");if(Json.str(row,"type").equals("territory"))areas.add(MapTerritoryJson.read(body,id));else{var m=MapMarkerLink.read(body);points.add(new MapMarker(id,m.dimension(),m.name(),m.x(),m.y(),m.z(),m.color(),m.icon()));}}if(!markers.equals(points))markers=List.copyOf(points);if(!territories.equals(areas))territories=List.copyOf(areas);generation=state.generation();}
 static boolean receive(JsonObject packet){
  if(Json.opt(packet,"kind","").equals("mapPublicationChange")){if(!available())return true;try{state.changed(packet.getAsJsonObject("change"));update();next=0;}catch(RuntimeException bad){reset();}return true;}
  if(!session.receive(packet))return false;
  try{if(packet.has("restart")){state.restart();next=0;return true;}boolean complete=state.page(packet,System.currentTimeMillis());update();if(!complete)request(Json.str(packet,"nextCursor"));}
  catch(RuntimeException bad){state.begin();next=System.currentTimeMillis()+5000;}return true;
 }
 private MapPublications(){}
}
