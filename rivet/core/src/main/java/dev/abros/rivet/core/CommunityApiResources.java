package dev.abros.rivet.core;

import com.google.gson.*;
import dev.abros.rivet.api.ApiException;
import java.util.*;
import static dev.abros.rivet.api.ApiException.Code.*;

/** Bounded API projections over existing task, map, profile and chat access policies. */
public final class CommunityApiResources {
 private dev.abros.rivet.core.api.MapLayerRegistry layers;
 public CommunityApiResources layers(dev.abros.rivet.core.api.MapLayerRegistry value){layers=Objects.requireNonNull(value);return this;}

 private dev.abros.rivet.core.api.LiveMapAccess live=dev.abros.rivet.core.api.LiveMapAccess.UNAVAILABLE;
 public CommunityApiResources live(dev.abros.rivet.core.api.LiveMapAccess value){live=Objects.requireNonNull(value);return this;}

 private dev.abros.rivet.core.api.DomainAccess access=dev.abros.rivet.core.api.DomainAccess.DENY;
 public CommunityApiResources access(dev.abros.rivet.core.api.DomainAccess value){access=Objects.requireNonNull(value);return this;}
 private PgDatabase.Work<dev.abros.rivet.core.pack.PackManifest> pack;
 public CommunityApiResources pack(PgDatabase.Work<dev.abros.rivet.core.pack.PackManifest> value){pack=value;return this;}
 private final PgDatabase db;private final CommunityStore store;private final ServerSettings settings;
 public CommunityApiResources(PgDatabase db,CommunityStore store,ServerSettings settings){this.db=db;this.store=store;this.settings=settings;}
 public Set<String> capabilities(){var result=new HashSet<>(Set.of("rest.notifications.read"));if(layers!=null&&layers.available()&&settings.flag("map.enabled"))result.add("rest.map.layers");for(String capability:live.capabilities())result.add("rest."+capability);if(store.modules().enabled("server"))result.add("rest.server.control.read");if(pack!=null&&store.modules().enabled("pack"))result.add("rest.pack.read");if(store.enabled("players")){result.add("rest.profiles.read");result.add("rest.statistics.read");if(settings.skins().enabled())result.add("rest.skins.read");}if(store.modules().enabled("reports")){result.add("rest.reports.read");result.add("rest.reports.manage");}if(settings.flag("map.enabled")){result.add("rest.map.read");result.add("rest.moderation.manage");}if(store.modules().enabled("tasks"))result.add("rest.tasks.read");if(settings.flag("chat.enabled"))result.add("rest.chat.read");return Set.copyOf(result);}
 public static String scope(String path){
  if(path.equals("map/layers")||path.matches("map/layers/[a-z][a-z0-9_]{1,63}/[a-z0-9_][a-z0-9_.-]{0,63}/objects"))return "map.read";

  if(path.equals("players/online"))return "players.presence.read";
  if(path.equals("map/positions"))return "map.positions.read";
  if(path.equals("map/waystones"))return "map.waystones.read";

  if(path.matches("groups/[0-9a-f-]{36}/(members|applications|invitations|markers)"))return "groups.read";
  if(path.matches("events/[0-9a-f-]{36}/schedule"))return "events.read";
  if(path.equals("admin/server/announcements"))return "server.control.read";
  if(path.equals("admin/server"))return "server.control.read";
  if(path.equals("admin/map/objects")||path.startsWith("admin/map/objects/"))return "moderation.manage";
  if(path.equals("admin/reports")||path.startsWith("admin/reports/"))return "reports.manage";
  if(path.equals("pack"))return "pack.read";
  if(path.equals("reports")||path.startsWith("reports/"))return "reports.read";
  if(path.matches("statistics/[0-9a-f-]{36}"))return "statistics.read";
  if(path.matches("skins/[0-9a-f-]{36}"))return "skins.read";
  if(path.equals("tasks")||path.startsWith("tasks/"))return "tasks.read";
  if(path.startsWith("profiles/"))return "profiles.read";
  if(path.equals("me/notifications")||path.equals("me/notification-preferences"))return "notifications.read";
  if((path.equals("chat/history")||path.equals("chat/channels")))return "chat.read";
  if(path.equals("me/map/objects")||path.startsWith("me/map/objects/")||path.equals("map/territories")||path.equals("map/objects")||path.startsWith("map/objects/"))return "map.read";
  return null;
 }
 private static void filters(Map<String,String> query,String... allowed){if(!Set.of(allowed).containsAll(query.keySet()))throw new ApiException(INVALID_ARGUMENT,"Unknown filter");}
 private static String uuid(String value){try{if(!UUID.fromString(value).toString().equals(value))throw new IllegalArgumentException();return value;}catch(IllegalArgumentException e){throw new ApiException(INVALID_ARGUMENT,"Invalid ID");}}
 private static JsonObject pick(JsonObject source,String... fields){var out=new JsonObject();for(String f:fields)if(source.has(f))out.add(f,source.get(f).deepCopy());return out;}
 private static long cursor(String value){try{long n=Long.parseLong(value);if(n<1)throw new IllegalArgumentException();return n;}catch(IllegalArgumentException e){throw new ApiException(INVALID_ARGUMENT,"Invalid cursor");}}
 private void module(String name){if(!store.modules().enabled(name))throw new ApiException(MODULE_DISABLED,"Module disabled");}
 public JsonObject read(CommunityStore.Actor actor,String path,Map<String,String> query)throws Exception{
  if(path.equals("map/layers")||path.startsWith("map/layers/")){
   if(!settings.flag("map.enabled")||layers==null)throw new ApiException(MODULE_DISABLED,"Map layers unavailable");filters(query,"cursor","snapshot");
   var viewer=actor.id().equals("__api_public__")?null:UUID.fromString(actor.id());var snapshot=query.containsKey("snapshot")?OptionalLong.of(Long.parseLong(query.get("snapshot"))):OptionalLong.empty();String cursor=query.getOrDefault("cursor","");
   if(path.equals("map/layers"))return Json.GSON.toJsonTree(layers.layers(viewer,cursor,snapshot)).getAsJsonObject();
   String[] p=path.split("/");if(p.length!=5||!p[4].equals("objects"))throw new ApiException(INVALID_ARGUMENT,"Invalid layer path");return Json.GSON.toJsonTree(layers.objects(viewer,p[2]+":"+p[3],cursor,snapshot)).getAsJsonObject();
  }

  if(Set.of("players/online","map/positions","map/waystones").contains(path)){
   if(actor.id().equals("__api_public__"))throw new ApiException(UNAUTHENTICATED,"Player session required");
   if(!settings.flag("map.enabled"))throw new ApiException(MODULE_DISABLED,"Map disabled");
   if(!path.equals("map/waystones")&&!settings.flag("map.positions"))throw new ApiException(MODULE_DISABLED,"Positions disabled");
   filters(query,"cursor","dimension");return live.read(UUID.fromString(actor.id()),path,Map.copyOf(query));
  }

  if(path.equals("pack")){module("pack");filters(query);if(pack==null)throw new ApiException(MODULE_DISABLED,"Pack unavailable");var value=pack.run();if(value==null)throw new ApiException(NOT_FOUND,"No published pack");var item=new JsonObject();item.addProperty("hash",value.hash());item.addProperty("minecraft",value.minecraft());item.addProperty("loader",value.loader());item.addProperty("files",value.files().size());item.add("components",Json.GSON.toJsonTree(value.components()));var out=new JsonObject();out.add("item",item);return out;}
  return db.communityTransaction(()->{
   if(path.startsWith("groups/")||path.startsWith("events/"))return new ApiCommunityDetails(db,store).read(actor,path,query);
   if(path.equals("admin/server/announcements")){module("server");filters(query);access.require(actor.id(),"rivet.announce");var in=new JsonObject();in.addProperty("op","list");var result=new JsonObject();result.add("items",new ScheduledAnnouncements(store).request(actor.id(),actor.name(),in,System.currentTimeMillis()).get("announcements"));result.addProperty("nextCursor","");return result;}

   if(path.equals("admin/server")){filters(query);boolean allowed=false;for(String right:List.of("rivet.restart","rivet.maintenance","rivet.announce"))if(access.allowed(UUID.fromString(actor.id()),right)){allowed=true;break;}if(!allowed)throw new ApiException(FORBIDDEN,"Staff permission required");var out=new JsonObject();out.add("item",new ApiServerControl(db,store).read());return out;}
   if(path.startsWith("admin/map/objects")){access.require(actor.id(),"rivet.admin");var result=map(new CommunityStore.Actor(actor.id(),actor.name(),true,false),path.substring(6),query);access.require(actor.id(),"rivet.admin");return result;}
   if(path.equals("admin/reports")||path.startsWith("admin/reports/"))return staffReports(actor,path,query);
   if(path.equals("reports")||path.startsWith("reports/"))return reports(actor,path,query);
   if(path.startsWith("statistics/")||path.startsWith("skins/"))return playerMetadata(actor,path,query);
   if(path.equals("tasks")||path.startsWith("tasks/"))return tasks(actor,path,query);
   if(path.startsWith("profiles/"))return profile(actor,uuid(path.substring(9)),query);
   if(path.equals("me/notification-preferences")){filters(query);var value=store.preferences(actor.id());var item=new JsonObject();item.add("muted",value.has("muted")?value.get("muted").deepCopy():new JsonArray());item.addProperty("snoozeUntil",value.has("snoozeUntil")?value.get("snoozeUntil").getAsLong():0);item.addProperty("revision",Hashes.sha256(item.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));var out=new JsonObject();out.add("item",item);return out;}
   if(path.equals("me/notifications"))return notices(actor,query);
   if(path.equals("chat/channels"))return channels(actor,query);
   if(path.equals("chat/history"))return chat(actor,query);
   if(path.startsWith("map/")||path.startsWith("me/map/"))return map(actor,path,query);
   throw new ApiException(NOT_FOUND,"Resource unavailable");
  });
 }
 private JsonObject staffReports(CommunityStore.Actor actor,String path,Map<String,String> query)throws Exception{
  module("reports");access.require(actor.id(),"rivet.reports");var out=new JsonObject();
  if(path.equals("admin/reports")){filters(query,"cursor","filter");var source=new CommunityReports(store).queue(actor.id(),query.getOrDefault("filter","all"),"",query.getOrDefault("cursor",""));var items=new JsonArray();for(var e:source.getAsJsonArray("entries"))items.add(staffReport(e.getAsJsonObject()));out.add("items",items);out.addProperty("nextCursor",Json.opt(source,"nextCursor",""));}
  else{filters(query);var value=store.record("reports",uuid(path.substring(14)));if(value==null)throw new ApiException(NOT_FOUND,"Report unavailable");out.add("item",staffReport(value));}
  access.require(actor.id(),"rivet.reports");return out;
 }
 private static JsonObject staffReport(JsonObject value){var out=report(value);for(String field:List.of("uuid","player","assigned","assignedName","priority","nextStep","internalNotes"))if(value.has(field))out.add(field,value.get(field).deepCopy());return out;}
 private JsonObject reports(CommunityStore.Actor actor,String path,Map<String,String> query)throws Exception{
  module("reports");var service=new CommunityReports(store);var out=new JsonObject();
  if(path.equals("reports")){filters(query,"cursor");var source=service.cursor(UUID.fromString(actor.id()),query.getOrDefault("cursor",""));var items=new JsonArray();for(var e:source.getAsJsonArray("entries"))items.add(report(e.getAsJsonObject()));out.add("items",items);out.addProperty("nextCursor",Json.opt(source,"nextCursor",""));}
  else{filters(query);String id=uuid(path.substring(8));var value=store.record("reports",id);if(value==null||!actor.id().equals(Json.opt(value,"uuid","")))throw new ApiException(NOT_FOUND,"Report unavailable");out.add("item",report(value));}return out;
 }
 private static JsonObject report(JsonObject value){return pick(value,"id","message","category","status","revision","createdAt","reply","replyAuthor","repliedAt");}
 private JsonObject playerMetadata(CommunityStore.Actor actor,String path,Map<String,String> query)throws Exception{
  filters(query);String id=uuid(path.substring(path.indexOf('/')+1));profile(actor,id,Map.of());var out=new JsonObject();var item=new JsonObject();item.addProperty("uuid",id);
  if(path.startsWith("statistics/")){var values=new PlayerStatistics(db,settings.statistics()).read(List.of(UUID.fromString(id)),Map.of());var value=values.get(UUID.fromString(id));if(value!=null)for(var e:value.entrySet())item.add(e.getKey(),e.getValue());}
  else{if(!settings.skins().enabled())throw new ApiException(MODULE_DISABLED,"Skins disabled");var value=new dev.abros.rivet.core.skins.SkinStore(db,settings.skins()).appearance(UUID.fromString(id));for(String field:List.of("hash","slim"))if(value.has(field))item.add(field,value.get(field));}
  out.add("item",item);return out;
 }
 private JsonObject tasks(CommunityStore.Actor actor,String path,Map<String,String> query)throws Exception{
  module("tasks");var service=new CommunityTasks(db,store);var input=new JsonObject();var out=new JsonObject();
  if(!path.equals("tasks")){
   boolean details=path.endsWith("/details");filters(query,details?new String[]{"subtaskOffset","commentOffset","revision"}:new String[]{});var task=service.task(uuid(path.substring(6,details?path.length()-8:path.length())),false);if(!service.access(actor,task,false))throw new ApiException(NOT_FOUND,"Task unavailable");if(details){if(query.containsKey("revision")&&!query.get("revision").equals(task.get("revision").getAsString()))throw new ApiException(CONFLICT,"Task changed");var request=new JsonObject();for(String key:List.of("subtaskOffset","commentOffset"))if(query.containsKey(key))request.addProperty(key,Long.parseLong(query.get(key)));TaskDetailPage.apply(task,request);var view=pick(task,"id","revision","assignee","dueAt","subtaskOffset","subtaskCount","commentOffset","commentCount");for(String key:List.of("subtasks","comments","resources")){var rows=new JsonArray();if(task.has(key))for(var row:task.getAsJsonArray(key))rows.add(switch(key){case "subtasks"->pick(row.getAsJsonObject(),"title","done","assignee");case "comments"->pick(row.getAsJsonObject(),"author","text","at");default->pick(row.getAsJsonObject(),"item","amount","alternatives");});view.add(key,rows);}out.add("item",view);}else out.add("item",task(task));return out;
  }
  filters(query,"group","cursor");String group=query.getOrDefault("group",""),after=query.getOrDefault("cursor","");if(!group.isEmpty())uuid(group);if(!after.isEmpty())uuid(after);
  if(!group.isEmpty()){var probe=new JsonObject();probe.addProperty("group",group);if(!service.access(actor,probe,false))throw new ApiException(NOT_FOUND,"Group tasks unavailable");}
  var items=new JsonArray();out.add("items",items);out.addProperty("nextCursor","");
  try(var q=db.connection().prepareStatement(CommunityTasks.LIST_SQL)){
   q.setString(1,group);q.setString(2,actor.id());q.setString(3,group);q.setString(4,after);for(int i=5;i<=8;i++)q.setString(i,"");
   try(var rows=q.executeQuery()){while(rows.next()){if(items.size()==20){out.addProperty("nextCursor",items.get(19).getAsJsonObject().get("id").getAsString());break;}var item=Json.parse(rows.getString(2));item.addProperty("id",rows.getString(1));item.addProperty("group",group);item.addProperty("revision",rows.getLong(3));if(rows.getString(5)!=null)item.addProperty("code",rows.getString(5));items.add(task(item));}}
  }return out;
 }
 private static JsonObject task(JsonObject value){return pick(value,"id","code","group","title","description","preview","status","revision","dueAt","completedAt","archived","location","stagesTotal","stagesDone");}
 private JsonObject profile(CommunityStore.Actor actor,String target,Map<String,String> query)throws Exception{
  if(!store.enabled("players"))throw new ApiException(MODULE_DISABLED,"Profiles disabled");filters(query);if(!new CommunityPlayerTools(db,store).visible(target,actor.id()))throw new ApiException(NOT_FOUND,"Profile unavailable");
  var out=new JsonObject();try(var q=db.connection().prepareStatement("SELECT p.name,c.about,c.interests FROM people p LEFT JOIN community_profile c ON c.player=p.id WHERE p.id=?")){
   q.setString(1,target);try(var rows=q.executeQuery()){if(!rows.next())throw new ApiException(NOT_FOUND,"Profile unavailable");var item=new JsonObject();item.addProperty("uuid",target);item.addProperty("name",rows.getString(1));item.addProperty("about",Objects.toString(rows.getString(2),""));item.addProperty("interests",Objects.toString(rows.getString(3),""));item.addProperty("revision",Hashes.sha256((item.get("about").toString()+":"+item.get("interests").toString()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));out.add("item",item);}
  }return out;
 }
 private JsonObject notices(CommunityStore.Actor actor,Map<String,String> query)throws Exception{
  filters(query,"cursor");long before=query.containsKey("cursor")?cursor(query.get("cursor")):Long.MAX_VALUE;var out=new JsonObject();var items=new JsonArray();out.add("items",items);out.addProperty("nextCursor","");
  // Disabled sections are filtered in SQL before the page limit. Reading never marks a notice read.
  var active=new ArrayList<String>();for(String s:FeatureModules.OPTIONAL)if(store.modules().enabled(s))active.add(s);
  try(var q=db.connection().prepareStatement("SELECT id,body,read FROM notices WHERE recipient=? AND id<? AND (NOT (coalesce(body->>'routeSection',body->>'section')=ANY(?)) OR coalesce(body->>'routeSection',body->>'section')=ANY(?)) ORDER BY id DESC LIMIT 21")){
   q.setString(1,actor.id());q.setLong(2,before);q.setArray(3,db.connection().createArrayOf("text",FeatureModules.OPTIONAL.toArray()));q.setArray(4,db.connection().createArrayOf("text",active.toArray()));
   try(var rows=q.executeQuery()){while(rows.next()){if(items.size()==20){out.addProperty("nextCursor",items.get(19).getAsJsonObject().get("id").getAsString());break;}var item=pick(Json.parse(rows.getString(2)),"title","body","section","at");item.addProperty("id",Long.toString(rows.getLong(1)));item.addProperty("read",rows.getBoolean(3));items.add(item);}}
  }return out;
 }
 private JsonObject channels(CommunityStore.Actor actor,Map<String,String> query)throws Exception{
  if(!settings.flag("chat.enabled"))throw new ApiException(MODULE_DISABLED,"Chat disabled");filters(query,"cursor");String after=query.getOrDefault("cursor","");if(!after.isEmpty())uuid(after);
  var result=new JsonObject();var items=new JsonArray();result.add("items",items);result.addProperty("nextCursor","");
  if(after.isEmpty())for(String id:settings.flag("chat.local")?List.of("global","local"):List.of("global")){var row=new JsonObject();row.addProperty("id",id);row.addProperty("name",settings.text("chat."+id+"Name"));items.add(row);}
  if(settings.flag("chat.group")&&store.modules().enabled("groups"))try(var q=db.connection().prepareStatement("SELECT d.id,d.body->>'title' FROM documents d JOIN community_relations r ON r.document=d.id AND r.kind='members' AND r.actor=? WHERE d.section='groups' AND d.body->>'status'='open' AND d.id>? ORDER BY d.id LIMIT 21")){
   q.setString(1,actor.id());q.setString(2,after);try(var rows=q.executeQuery()){int count=0;String last="";while(rows.next()){if(count++==20){result.addProperty("nextCursor",last);break;}last=rows.getString(1);var row=new JsonObject();row.addProperty("id",last);row.addProperty("name",rows.getString(2));items.add(row);}}
  }return result;
 }
 private JsonObject chat(CommunityStore.Actor actor,Map<String,String> query)throws Exception{
  if(!settings.flag("chat.enabled"))throw new ApiException(MODULE_DISABLED,"Chat disabled");filters(query,"cursor");
  long before=query.containsKey("cursor")?cursor(query.get("cursor")):Long.MAX_VALUE;
  var history=new ChatHistory(db,settings.number("chat.history"),settings.number("chat.historyDays"));var out=new JsonObject();var items=new JsonArray();out.add("items",items);out.addProperty("nextCursor","");
  history.replay(actor.id(),System.currentTimeMillis()+1,settings.flag("chat.local"),settings.flag("chat.group")&&store.modules().enabled("groups"),before,20,messages->{
   for(var m:messages){var row=new JsonObject();row.addProperty("id",Long.toString(m.id()));row.addProperty("at",m.at());row.addProperty("channel",m.channel());row.addProperty("text",chatText(JsonParser.parseString(m.body()),0));items.add(row);}
   if(!messages.isEmpty()&&messages.size()==Math.min(20,settings.number("chat.history")))out.addProperty("nextCursor",Long.toString(messages.getLast().id()));
  });return out;
 }
 /** Never export click/hover events, command payloads, NBT or selectors from Minecraft components. */
 private static String chatText(JsonElement value,int depth){
  if(depth>32||value==null||value.isJsonNull())return "";if(value.isJsonPrimitive())return value.getAsString();var out=new StringBuilder();
  if(value.isJsonArray()){for(var v:value.getAsJsonArray())out.append(chatText(v,depth+1));}
  else{var j=value.getAsJsonObject();if(j.has("text"))out.append(chatText(j.get("text"),depth+1));else if(j.has("translate"))out.append(Json.opt(j,"fallback",Json.opt(j,"translate","")));if(j.has("with"))out.append(chatText(j.get("with"),depth+1));if(j.has("extra"))out.append(chatText(j.get("extra"),depth+1));}
  return out.substring(0,Math.min(out.length(),12000));
 }
 private JsonObject map(CommunityStore.Actor actor,String path,Map<String,String> query)throws Exception{
  if(!settings.flag("map.enabled"))throw new ApiException(MODULE_DISABLED,"Map disabled");
  var input=new JsonObject();var out=new JsonObject();var items=new JsonArray();out.add("items",items);
  if(path.equals("map/territories")){
   module("groups");filters(query,"cursor");input.addProperty("cursor",query.getOrDefault("cursor",""));var source=new CommunityGroupMap(db,store).read(actor,input);
   for(var item:source.getAsJsonArray("territories"))if(item.getAsJsonObject().has("territory"))items.add(pick(item.getAsJsonObject(),"id","title","revision","territory"));out.addProperty("nextCursor",Json.opt(source,"nextCursor",""));return out;
  }
  boolean own=path.startsWith("me/map/"),manage=own||actor.admin();if(own)path=path.substring(3);if(manage)input.addProperty("manage",true);
  boolean detail=!path.equals("map/objects");filters(query,detail?new String[]{}:new String[]{"cursor","snapshot"});input.addProperty("op","mapObjects");input.addProperty("cursor",query.getOrDefault("cursor",""));
  if(detail)input.addProperty("id",uuid(path.substring(12)));
  if(query.containsKey("snapshot")){long version;try{version=Long.parseLong(query.get("snapshot"));if(version<0)throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw new ApiException(INVALID_ARGUMENT,"Invalid snapshot");}input.addProperty("snapshot",version);}
  if(!query.getOrDefault("cursor","").isEmpty()&&!query.containsKey("snapshot"))throw new ApiException(INVALID_ARGUMENT,"Snapshot required with cursor");
  var source=new CommunityMapObjects(db,store).request(actor,input);if(source.has("restart"))throw new ApiException(CONFLICT,"Publication snapshot changed");
  for(var item:source.getAsJsonArray("mapObjects")==null?new JsonArray():source.getAsJsonArray("mapObjects"))items.add(manage?pick(item.getAsJsonObject(),"id","type","revision","published","draft"):pick(item.getAsJsonObject(),"id","type","revision","published"));
  if(detail){if(items.isEmpty())throw new ApiException(NOT_FOUND,"Publication unavailable");out.remove("items");out.add("item",items.get(0));}
  if(!detail)out.addProperty("nextCursor",Json.opt(source,"nextCursor",""));out.add("snapshot",source.get("snapshot"));if(source.has("unchanged"))out.add("unchanged",source.get("unchanged"));return out;
 }
}
