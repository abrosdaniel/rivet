package dev.abros.rivet.core;

import com.google.gson.*;
import dev.abros.rivet.api.ApiException;
import dev.abros.rivet.core.api.ApiJson;
import java.util.*;
import static dev.abros.rivet.api.ApiException.Code.*;

/** Explicit external actions; game services remain authoritative for ownership and side effects. */
public final class CommunityApiCommands {
 private dev.abros.rivet.core.api.DomainAccess access=dev.abros.rivet.core.api.DomainAccess.DENY;
 public CommunityApiCommands access(dev.abros.rivet.core.api.DomainAccess value){access=Objects.requireNonNull(value);return this;}
 private dev.abros.rivet.core.api.ApiChat.LocalAudience local=player->{throw new ApiException(UNAVAILABLE,"Local presence unavailable");};
 public CommunityApiCommands local(dev.abros.rivet.core.api.ApiChat.LocalAudience value){local=Objects.requireNonNull(value);return this;}
 private final PgDatabase db;private final CommunityStore store;private final ServerSettings settings;
 public CommunityApiCommands(PgDatabase db,CommunityStore store,ServerSettings settings){this.db=db;this.store=store;this.settings=settings;}
 public static String scope(String path){
  if(path.startsWith("admin/server/"))return "server.control.write";
  if(path.startsWith("admin/map/objects/"))return "moderation.manage";
  if(path.startsWith("admin/reports/"))return "reports.manage";
  if(path.equals("chat/messages"))return "chat.write";
  if(path.equals("reports"))return "reports.write";
  if(path.equals("tasks")||path.startsWith("tasks/"))return "tasks.write";
  if(path.startsWith("map/objects/"))return "map.write";
  if(path.equals("me/notification-preferences"))return "notifications.write";
  if(path.equals("me/profile"))return "profiles.write";
  if(path.matches("me/notifications/[0-9]+/read"))return "notifications.write";
  String domain=path.split("/",-1)[0];return Set.of("groups","events","board","polls","ideas").contains(domain)?domain+".write":null;
 }
 public JsonObject execute(CommunityStore.Actor actor,String application,String method,String path,String match,JsonObject body)throws Exception{
  RetiredFeatures.requireActive(path.split("/",-1)[0]);
  ApiJson.fields(body,"operationId","issuedAt","data");String operation=ApiJson.string(body,"operationId");uuid(operation);
  if(!body.get("issuedAt").isJsonPrimitive()||!body.get("issuedAt").getAsJsonPrimitive().isNumber()||!body.get("data").isJsonObject())throw ApiJson.invalid();
  body.get("issuedAt").getAsBigDecimal().longValueExact();var data=body.getAsJsonObject("data");
  var in=new JsonObject();in.addProperty("section","home");String[] p=path.split("/",-1);String op;
  if(path.equals("admin/server/announcements")&&method.equals("POST")){store.modules().require("server");ApiJson.fields(data,"text","startsAt");op="scheduleAnnouncement";
  }else if(path.matches("admin/server/announcements/[0-9a-f-]{36}/cancel")&&method.equals("POST")){store.modules().require("server");ApiJson.fields(data);uuid(p[3]);in.addProperty("id",p[3]);revision(in,match);op="cancelAnnouncement";
  }else if(path.startsWith("admin/server/")&&method.equals("POST")){if(p.length!=3)throw ApiJson.invalid();store.modules().require("server");ApiServerControl.permission(p[2]);op="serverControl";
  }else if(path.equals("me/notification-preferences")&&method.equals("PUT")){ApiJson.fields(data,"muted","snoozeUntil");if(!data.get("muted").isJsonArray())throw ApiJson.invalid();for(var entry:data.getAsJsonArray("muted"))if(!entry.isJsonPrimitive()||!entry.getAsJsonPrimitive().isString())throw ApiJson.invalid();if(!data.get("snoozeUntil").isJsonPrimitive()||!data.get("snoozeUntil").getAsJsonPrimitive().isNumber())throw ApiJson.invalid();data.get("snoozeUntil").getAsBigDecimal().longValueExact();op="preferences";in.add("preferences",data.deepCopy());
  }else if(path.equals("chat/messages")&&method.equals("POST")){ApiJson.fields(data,"channel","text");op="chatSend";
  }else if(path.startsWith("admin/map/objects/")&&method.equals("POST")){
   if(!settings.flag("map.enabled"))throw new ApiException(MODULE_DISABLED,"Map disabled");if(p.length!=5)throw ApiJson.invalid();uuid(p[3]);in.addProperty("id",p[3]);revision(in,match);ApiJson.fields(data);op=switch(p[4]){case "approve"->"mapObjectApprove";case "reject"->"mapObjectReject";case "withdraw"->"mapObjectWithdraw";default->throw ApiJson.invalid();};
  }else if(path.startsWith("admin/reports/")&&method.equals("POST")){
   store.modules().require("reports");if(p.length!=5||!p[3].equals("actions"))throw ApiJson.invalid();uuid(p[2]);in.addProperty("id",p[2]);revision(in,match);op="reportManage";in.addProperty("operation",p[4]);
   switch(p[4]){case "reply"->ApiJson.fields(data,"text","resolved");case "claim","release"->ApiJson.fields(data);case "assign"->ApiJson.fields(data,"target");case "classify"->ApiJson.fields(data,"category","priority");case "state"->ApiJson.fields(data,"status","nextStep");case "note"->ApiJson.fields(data,"text");default->throw ApiJson.invalid();}
  }else if(path.equals("reports")&&method.equals("POST")){store.modules().require("reports");ApiJson.fields(data,"message","category");ApiJson.string(data,"message");ApiJson.string(data,"category");op="reportSubmit";
  }else if(p[0].equals("map")){
   if(!settings.flag("map.enabled"))throw new ApiException(MODULE_DISABLED,"Map disabled");
   if(p.length!=4||!p[1].equals("objects"))throw ApiJson.invalid();uuid(p[2]);in.addProperty("id",p[2]);revision(in,match);
   if(p[3].equals("draft")&&method.equals("PUT")){ApiJson.fields(data,"type","draft");op="mapObjectSubmit";}
   else if(p[3].equals("withdraw")&&method.equals("POST")){ApiJson.fields(data);op="mapObjectWithdraw";}else throw ApiJson.invalid();
  }else if(path.equals("me/profile")&&method.equals("PUT")){
   if(!store.enabled("players"))throw new ApiException(MODULE_DISABLED,"Profiles disabled");ApiJson.fields(data,"about","interests");op="plusProfileSave";
  }else if(path.matches("me/notifications/[0-9]+/read")&&method.equals("POST")){
   ApiJson.fields(data);Long.parseLong(p[2]);op="read";in.addProperty("section","notifications");in.addProperty("id",p[2]);in.addProperty("exact",true);
  }else if(p.length==1&&p[0].equals("groups")&&method.equals("POST")){
   if(!store.enabled(p[0])||!store.modules().enabled(p[0]))throw new ApiException(MODULE_DISABLED,"Domain disabled");in.addProperty("section",p[0]);op="create";
   ApiJson.fields(data,"title","description","type");
  }else{
   if(p.length!=4||!p[2].equals("actions")||!method.equals("POST"))throw ApiJson.invalid();
   String section=p[0];if(!store.enabled(section)||!store.modules().enabled(section))throw new ApiException(MODULE_DISABLED,"Domain disabled");
   uuid(p[1]);in.addProperty("section",section);in.addProperty("id",p[1]);revision(in,match);op=switch(p[3]){case "markerSave"->"plusItemSave";case "markerDelete"->"plusItemDelete";case "territory"->"plusTerritorySave";case "removeMember"->"plusRemoveMember";case "assistantRemoval"->"plusAssistantRemoval";default->p[3];};
   switch(section+":"+p[3]){
    case "groups:markerSave"->{ApiJson.fields(data,"itemId","itemRevision","title","description","location");in.addProperty("kind","place");}
    case "groups:markerDelete"->ApiJson.fields(data,"itemId","itemRevision");
    case "groups:territory"->ApiJson.fields(data,"territory");
    case "groups:hide"->ApiJson.fields(data,"text");
    case "groups:removeMember"->ApiJson.fields(data,"target","reason");
    case "groups:assistantRemoval"->ApiJson.fields(data,"enabled");
    case "groups:edit"->ApiJson.fields(data,"title","description","type");
    case "groups:delete","groups:restore","groups:leave","groups:withdrawApplication","groups:recruiting"->ApiJson.fields(data);
    case "groups:apply"->ApiJson.fields(data,"text");
    case "groups:invite","groups:revokeInvitation"->ApiJson.fields(data,"target");
    case "groups:application"->ApiJson.fields(data,"target","accept");
    case "groups:invitation"->ApiJson.fields(data,"accept");
    case "groups:role"->ApiJson.fields(data,"target","role");
    default->throw ApiJson.invalid();
   }
  }
  for(String name:List.of("title","description","status","type","about","interests","text","target","role","startsAt","reason","category","priority","nextStep","itemId"))if(data.has(name))ApiJson.string(data,name);
  for(String name:List.of("accept","resolved","enabled"))if(data.has(name)&&(!data.get(name).isJsonPrimitive()||!data.get(name).getAsJsonPrimitive().isBoolean()))throw ApiJson.invalid();
  for(String field:List.of("itemRevision"))if(data.has(field)){if(!data.get(field).isJsonPrimitive()||!data.get(field).getAsJsonPrimitive().isNumber())throw ApiJson.invalid();data.get(field).getAsBigDecimal().intValueExact();}
  if(Set.of("plusItemSave","plusItemDelete").contains(op)){String item=ApiJson.string(data,"itemId");if(!item.isEmpty())uuid(item);if(data.get("itemRevision").getAsLong()<0||item.isEmpty()&&(op.equals("plusItemDelete")||data.get("itemRevision").getAsLong()!=0))throw ApiJson.invalid();if(op.equals("plusItemSave")){if(!data.get("location").isJsonObject())throw ApiJson.invalid();CommunityLocation.read(data.getAsJsonObject("location"));}}
  if(data.has("target"))uuid(ApiJson.string(data,"target"));
  if(Set.of("plusProfileSave","preferences").contains(op)&&(match==null||!match.matches("\"[a-f0-9]{64}\"")))throw ApiJson.invalid();
  if(op.equals("plusTerritorySave")&&!data.get("territory").isJsonNull()){var t=data.getAsJsonObject("territory");ApiJson.fields(t,"dimension","color","points");ApiJson.string(t,"dimension");for(var point:t.getAsJsonArray("points"))ApiJson.fields(point.getAsJsonObject(),"x","z");}
  if(op.equals("mapObjectSubmit")){
   if(!data.get("draft").isJsonObject())throw ApiJson.invalid();var draft=data.getAsJsonObject("draft");String type=ApiJson.string(data,"type");
   if(type.equals("marker"))ApiJson.fields(draft,"dimension","name","x","y","z","color","icon");
   else if(type.equals("territory")){
    ApiJson.fields(draft,"dimension","name","color","points");if(!draft.get("points").isJsonArray())throw ApiJson.invalid();
    for(var point:draft.getAsJsonArray("points")){if(!point.isJsonObject())throw ApiJson.invalid();ApiJson.fields(point.getAsJsonObject(),"x","z");}
   }else throw ApiJson.invalid();
  }
  in.addProperty("op",op);for(var e:data.entrySet())in.add(e.getKey(),e.getValue().deepCopy());
  var receipt=body.deepCopy();receipt.addProperty("method",method);receipt.addProperty("path",path);receipt.addProperty("match",match);
  if(op.equals("hide"))access.require(actor.id(),"rivet.admin");
  if(Set.of("scheduleAnnouncement","cancelAnnouncement").contains(op))access.require(actor.id(),"rivet.announce");
  if(op.equals("serverControl"))access.require(actor.id(),ApiServerControl.permission(p[2]));
  if(path.startsWith("admin/map/"))access.require(actor.id(),"rivet.admin");
  if(op.equals("reportManage"))access.require(actor.id(),"rivet.reports");
  return db.communityTransaction(()->new RequestJournal(db).execute("api:"+application+":"+actor.id(),receipt,()->{
   CommunityStore.Actor effective=actor;
   if(op.equals("hide")){access.require(actor.id(),"rivet.admin");effective=new CommunityStore.Actor(actor.id(),actor.name(),true,false);}
   if(path.startsWith("admin/map/")){access.require(actor.id(),"rivet.admin");effective=new CommunityStore.Actor(actor.id(),actor.name(),true,false);}
   if(op.equals("reportManage")){
    access.require(actor.id(),"rivet.reports");db.lock("api:report:"+Json.str(in,"id"));
    if(data.has("target"))in.addProperty("targetName",store.personName(Json.str(data,"target")));
   }
   if(op.equals("preferences")){db.lock("preferences:"+actor.id());var current=new CommunityApiResources(db,store,settings).read(actor,path,Map.of()).getAsJsonObject("item");if(!Objects.equals(match,"\""+current.get("revision").getAsString()+"\""))throw new ApiException(CONFLICT,"Preferences changed");}
   if(op.equals("plusProfileSave")){
    db.lock("profile:"+actor.id());var current=new CommunityApiResources(db,store,settings).read(actor,"profiles/"+actor.id(),Map.of()).getAsJsonObject("item");
    if(!Objects.equals(match,"\""+current.get("revision").getAsString()+"\""))throw new ApiException(CONFLICT,"Profile changed");
   }
   if(in.has("id")&&p[0].equals("groups")){
    if(p[0].equals("groups"))db.lock("community:groups");var current=store.get(p[1],true);new CommunityPlus(db,store).requireVisible(actor,current);
    if(!Json.str(current,"section").equals(p[0])||current.get("revision").getAsLong()!=in.get("revision").getAsLong())throw new ApiException(CONFLICT,"Resource changed");
   }
   if(Set.of("plusItemSave","plusItemDelete").contains(op)&&!Json.str(in,"itemId").isEmpty()){try(var q=db.connection().prepareStatement("SELECT kind FROM community_group_items WHERE id=? AND group_id=? FOR UPDATE")){q.setString(1,Json.str(in,"itemId"));q.setString(2,Json.str(in,"id"));try(var rows=q.executeQuery()){if(!rows.next()||!rows.getString(1).equals("place"))throw new ApiException(NOT_FOUND,"Marker unavailable");}}}
   JsonObject source;
   if(Set.of("scheduleAnnouncement","cancelAnnouncement").contains(op)){access.require(actor.id(),"rivet.announce");in.addProperty("op",op.equals("scheduleAnnouncement")?"create":"cancel");source=new ScheduledAnnouncements(store).request(actor.id(),actor.name(),in,System.currentTimeMillis());}else if(op.equals("serverControl")){access.require(actor.id(),ApiServerControl.permission(p[2]));source=new ApiServerControl(db,store).change(actor.name(),p[2],match,data);}else if(op.equals("chatSend")){new dev.abros.rivet.core.api.ApiChat(db,store,settings,local).send(actor,application,data);source=new JsonObject();}else if(op.equals("reportSubmit")){source=new JsonObject();String id=new CommunityReports(store).submit(UUID.fromString(actor.id()),actor.name(),data,System.currentTimeMillis());in.addProperty("id",id);source.addProperty("revision",1);}else if(op.equals("reportManage")){var service=new CommunityReports(store);source=Json.str(in,"operation").equals("reply")?service.replyRequest(actor.id(),actor.name(),in):service.manageRequest(actor.id(),actor.name(),in);source.add("revision",source.getAsJsonObject("report").get("revision"));}else source=store.request(effective,in);var result=new JsonObject();result.addProperty("accepted",true);result.addProperty("operationId",operation);
   if(source.has("changedId")){result.add("id",source.get("changedId"));result.add("revision",source.get("changedRevision"));}
   else {if(in.has("id"))result.add("id",in.get("id"));else if(source.has("detail"))result.add("id",source.getAsJsonObject("detail").get("id"));if(source.has("revision"))result.add("revision",source.get("revision"));if(source.has("detail"))result.add("revision",source.getAsJsonObject("detail").get("revision"));}
   store.audit(actor.name(),"api "+application+" "+op,path);
   return result;
  }));
 }
 private static void uuid(String id){if(!UUID.fromString(id).toString().equals(id))throw ApiJson.invalid();}
 private static void revision(JsonObject in,String match){if(match==null||!match.matches("\"[0-9]{1,18}\""))throw ApiJson.invalid();in.addProperty("revision",Long.parseLong(match.substring(1,match.length()-1)));}
}
