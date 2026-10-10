package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.map.*;
import java.util.*;
import net.minecraft.client.Minecraft;

/** Atomic, expiring group territories. Membership and editing rights are supplied by the server. */
final class MapGroupClient {
 record Group(String id,String title,long revision,boolean manage,MapTerritory territory){}
 private static final RequestSession session=new RequestSession();
 private static final Map<String,Group> pending=new LinkedHashMap<>();private static final Set<String> cursors=new HashSet<>();
 private static String correlation="";private static int page;
 private static List<Group> groups=List.of();private static long next,expires,started;private static String status=Client.text("ui.loading_territories_69a10574");
 static String status(){return status;}
 static boolean available(){return WorldMapClient.allowed()&&ServerMenuClient.available()&&ServerMenuClient.supports("group-map")&&ServerMenuClient.module("groups");}
 static List<Group> groups(){return available()&&System.currentTimeMillis()<expires?groups:List.of();}
 static List<MapTerritory> territories(){return groups().stream().map(Group::territory).filter(Objects::nonNull).toList();}
 static void reset(){session.cancel();correlation="";page=0;pending.clear();cursors.clear();groups=List.of();next=expires=0;status=Client.text("ui.loading_territories_69a10574");}
 static void tick(){long now=System.currentTimeMillis();if(!available()||Minecraft.getInstance().level==null){reset();return;}if(session.timeout(now)){reset();next=now+5000;}if(now>=expires)groups=List.of();if(!session.pending()&&now>=next){pending.clear();cursors.clear();started=now;request("");}}
 private static void request(String cursor){var j=new JsonObject();j.addProperty("action","community");j.addProperty("op","groupMap");j.addProperty("section","groups");j.addProperty("cursor",cursor);var request=session.begin(j,false,System.currentTimeMillis());correlation=Json.str(request,"request");page=0;ServerMenuClient.requestBackground(request);}
 static boolean receive(JsonObject j){boolean paged=j.has("groupPage")&&ServerMenuClient.supports(MapGroupPages.FEATURE);if(paged){if(!session.pending()||!correlation.equals(Json.opt(j,"request","")))return false;}else if(!session.receive(j))return false;try{if(paged&&j.get("groupPage").getAsInt()!=page++)throw new IllegalArgumentException();long now=System.currentTimeMillis();if(!available()||now-started>=12000||!j.has("territories")||j.has("error"))throw new IllegalArgumentException();var rows=j.getAsJsonArray("territories");if(rows.size()>(paged?4096:2))throw new IllegalArgumentException();for(var e:rows){var row=e.getAsJsonObject();String id=Json.str(row,"id");UUID uuid=UUID.fromString(id);var group=new Group(id,Json.str(row,"title"),row.get("revision").getAsLong(),row.get("manage").getAsBoolean(),row.has("territory")?MapTerritoryJson.read(row.getAsJsonObject("territory"),uuid,Json.str(row,"title")):null);if(pending.putIfAbsent(id,group)!=null)throw new IllegalArgumentException();}if(pending.size()>4096)throw new IllegalArgumentException();if(paged&&!j.get("groupLast").getAsBoolean())return true;if(paged)session.receive(j);String cursor=Json.opt(j,"nextCursor","");if(!cursor.isEmpty()){UUID.fromString(cursor);if(pending.size()>=4096||!cursors.add(cursor))throw new IllegalArgumentException();request(cursor);}else{groups=List.copyOf(pending.values());pending.clear();status=groups.isEmpty()?Client.text("ui.no_group_territories_available_1ad0d22b"):"";expires=started+12000;next=now+3000;}}catch(RuntimeException ex){reset();status=Client.text("ui.could_not_load_territories_retrying_962b0d41");next=System.currentTimeMillis()+5000;}return true;}
 static void open(WorldMapScreen map,String id){var group=groups().stream().filter(g->g.id().equals(id)).findFirst();if(group.isEmpty())return;var g=group.get();if(g.territory()!=null)map.focusTerritory(g.territory());if(g.manage())Minecraft.getInstance().setScreen(new MapTerritoryScreen(map,new MapTerritories.Draft(g,map.selectedDimension())));else Minecraft.getInstance().setScreen(map);}
 private MapGroupClient(){}
}
