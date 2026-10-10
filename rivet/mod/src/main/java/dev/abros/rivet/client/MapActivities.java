package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.client.Minecraft;
import java.util.*;

/** Ephemeral, permission-filtered task/event snapshots. Never saved as personal markers. */
final class MapActivities {
 private static final List<Source> SOURCES=List.of(new Source("tasks"),new Source("events"),new Source("groupmarkers"));
 private static MapLayerClient.Point route;private static long routeExpires;
 static boolean available(String source){return WorldMapClient.allowed()&&ServerMenuClient.available()&&ServerMenuClient.supports("map-activities")&&ServerMenuClient.module(source.equals("groupmarkers")?"groups":source)&&(!source.equals("groupmarkers")||ServerMenuClient.supports("group-map"));}
 static List<MapLayerClient.Point> points(){long now=System.currentTimeMillis();return SOURCES.stream().filter(s->available(s.name)&&now<s.expires).flatMap(s->s.points.stream()).filter(p->p.group().isEmpty()||ServerMenuClient.module("groups")).filter(p->p.endsAt()==0||now<p.endsAt()).toList();}
 static void navigate(MapLayerClient.Point point){route=point;routeExpires=System.currentTimeMillis()+12000;DirectionCue.start(point.location());}
 private static void stopRoute(String source){if(route!=null&&route.source().equals(source)){if(Objects.equals(DirectionCue.target(),route.location()))DirectionCue.clear();route=null;}}
 static void reset(){for(var s:SOURCES)s.clear();route=null;}
 static void invalidate(){for(var s:SOURCES)s.clear();}
 static void tick(){long now=System.currentTimeMillis();for(var s:SOURCES){
  if(Minecraft.getInstance().level==null||!available(s.name)){s.clear();stopRoute(s.name);continue;}
  if(s.session.timeout(now)){s.clear();s.next=now+5000;stopRoute(s.name);}
  if(now>=s.expires&&!s.points.isEmpty()){s.points=List.of();stopRoute(s.name);}
  if(!s.session.pending()&&now>=s.next){s.pending.clear();s.cursors.clear();s.started=now;s.request("");}
 }if(route!=null&&!route.group().isEmpty()&&!ServerMenuClient.module("groups"))stopRoute(route.source());if(route!=null&&(now>=routeExpires||route.endsAt()>0&&now>=route.endsAt()))stopRoute(route.source());}
 static boolean receive(JsonObject packet){for(var s:SOURCES)if(s.session.receive(packet)){
  try{
   if(packet.has("error")||!packet.has("activities")||!s.name.equals(Json.opt(packet,"source","")))throw new IllegalArgumentException();
   long now=System.currentTimeMillis();if(now-s.started>=12000||!available(s.name))throw new IllegalArgumentException();
   var rows=packet.getAsJsonArray("activities");if(rows.size()>32)throw new IllegalArgumentException();
   for(var value:rows){var row=value.getAsJsonObject();String id=Json.str(row,"id");UUID.fromString(id);String group=Json.opt(row,"group","");if(!group.isEmpty())UUID.fromString(group);
    var p=CommunityLocation.read(row.getAsJsonObject("location"));String title=Json.str(row,"title");if(title.isBlank()||title.length()>100)throw new IllegalArgumentException();
    var location=new CommunityLocation(title.substring(0,Math.min(80,title.length())),p.dimension(),p.x(),p.y(),p.z(),false);
    var point=new MapLayerClient.Point(id,location,false,false,s.name,group,row.has("endsAt")?row.get("endsAt").getAsLong():0);
    if(s.pending.putIfAbsent(id,point)!=null)throw new IllegalArgumentException();
   }
   String cursor=Json.opt(packet,"nextCursor","");
   if(!cursor.isEmpty()){UUID.fromString(cursor);if(s.pending.size()>=4096||!s.cursors.add(cursor))throw new IllegalArgumentException();s.request(cursor);}
   else{s.points=List.copyOf(s.pending.values());s.expires=s.started+12000;s.next=now+3000;s.pending.clear();
    if(route!=null&&route.source().equals(s.name)){var updated=s.points.stream().filter(p->p.id().equals(route.id())).findFirst();if(updated.isEmpty())stopRoute(s.name);else if(Objects.equals(DirectionCue.target(),route.location())){var next=updated.get();if(!next.location().equals(route.location()))DirectionCue.start(next.location());route=next;routeExpires=s.expires;}else route=null;}
   }
  }catch(RuntimeException ex){s.clear();s.next=System.currentTimeMillis()+5000;stopRoute(s.name);}return true;
 }return false;}
 private static final class Source {
  final String name;final RequestSession session=new RequestSession();final Map<String,MapLayerClient.Point> pending=new LinkedHashMap<>();final Set<String> cursors=new HashSet<>();List<MapLayerClient.Point> points=List.of();long next,expires,started;
  Source(String name){this.name=name;}
  void clear(){session.cancel();pending.clear();cursors.clear();points=List.of();next=expires=0;}
  void request(String cursor){var j=new JsonObject();j.addProperty("action","community");j.addProperty("section","home");j.addProperty("op","mapActivities");j.addProperty("source",name);j.addProperty("cursor",cursor);ServerMenuClient.requestBackground(session.begin(j,false,System.currentTimeMillis()));}
 }
 private MapActivities(){}
}
