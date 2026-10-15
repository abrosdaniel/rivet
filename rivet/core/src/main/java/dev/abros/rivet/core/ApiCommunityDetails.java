package dev.abros.rivet.core;

import com.google.gson.*;
import dev.abros.rivet.api.ApiException;
import java.util.*;
import static dev.abros.rivet.api.ApiException.Code.*;

/** Explicit private projections. Continuations bind actor, parent revision and relation kind. */
final class ApiCommunityDetails {
 private final PgDatabase db;private final CommunityStore store;
 ApiCommunityDetails(PgDatabase db,CommunityStore store){this.db=db;this.store=store;}
 JsonObject read(CommunityStore.Actor actor,String path,Map<String,String> query)throws Exception{
  String[] p=path.split("/");String domain=p[0],id=p[1],kind=p[2];
  if(!store.enabled(domain)||!store.modules().enabled(domain))throw new ApiException(MODULE_DISABLED,"Domain disabled");
  if(!UUID.fromString(id).toString().equals(id)||!Set.of("cursor").containsAll(query.keySet()))throw new ApiException(INVALID_ARGUMENT,"Invalid filters");
  // The ordinary detail policy checks hidden/deleted entries before private data is read.
  store.apiRead(actor,domain,id,"");var document=store.get(id);long revision=document.get("revision").getAsLong();
  String scope="api:"+actor.id()+":"+path;int offset=0;String token=query.getOrDefault("cursor","");
  if(!token.isEmpty()){var cursor=CommunityCursor.decode(token,scope);if(cursor.time()!=revision)throw new ApiException(CONFLICT,"Resource changed");if(cursor.sequence()>1000)throw new ApiException(INVALID_ARGUMENT,"Invalid cursor");offset=(int)cursor.sequence();}
  var out=new JsonObject();var items=new JsonArray();boolean more=false;
  if(domain.equals("groups")){
   boolean member=document.getAsJsonObject("members").has(actor.id()),manager=store.manager(document,actor);
   if(kind.equals("markers")){
    if(!member)throw new ApiException(FORBIDDEN,"Group membership required");
    try(var q=db.connection().prepareStatement("SELECT id,body,revision FROM community_group_items WHERE group_id=? AND kind='place' ORDER BY id LIMIT 21 OFFSET ?")){
     q.setString(1,id);q.setInt(2,offset);try(var rs=q.executeQuery()){while(rs.next()){if(items.size()==20){more=true;break;}var b=Json.parse(rs.getString(2));var row=pick(b,"title","description","location");row.addProperty("id",rs.getString(1));row.addProperty("revision",rs.getLong(3));items.add(row);}}
    }
   }else{
    boolean ownOnly=!kind.equals("members")&&!manager;
    try(var q=db.connection().prepareStatement("SELECT r.actor,r.value,p.name FROM community_relations r LEFT JOIN people p ON p.id=r.actor WHERE r.document=? AND r.kind=? AND (?='' OR r.actor=?) ORDER BY r.actor LIMIT 21 OFFSET ?")){
     q.setString(1,id);q.setString(2,kind);q.setString(3,ownOnly?actor.id():"");q.setString(4,actor.id());q.setInt(5,offset);
     try(var rs=q.executeQuery()){while(rs.next()){if(items.size()==20){more=true;break;}var row=new JsonObject();row.addProperty("id",rs.getString(1));row.addProperty("name",Objects.toString(rs.getString(3),rs.getString(1)));var value=JsonParser.parseString(rs.getString(2));if(kind.equals("members"))row.addProperty("role",value.getAsString());if(kind.equals("applications")){row.addProperty("text",Json.opt(value.getAsJsonObject(),"text",""));}items.add(row);}}
    }
   }
  }else{
   if(!token.isEmpty())throw new ApiException(INVALID_ARGUMENT,"Schedule is not paginated");
   var detail=pick(document,"id","revision","visibility","group","startsAt","durationMinutes","repeat","series");
   if(Json.str(document,"owner").equals(actor.id()))detail.add("invitees",document.has("eventInvites")?document.get("eventInvites").deepCopy():new JsonArray());
   var occurrences=new JsonArray();String series=Json.opt(document,"series","");
   if(!series.isEmpty())try(var q=db.connection().prepareStatement("SELECT id FROM documents WHERE section='events' AND body->>'series'=? ORDER BY (body->>'startsAt')::bigint,id LIMIT 26")){q.setString(1,series);try(var rs=q.executeQuery()){while(rs.next()){var event=store.get(rs.getString(1));if(new CommunityPlus(db,store).visible(actor,event)&&!Set.of("hidden","deleted").contains(Json.opt(event,"status","")))occurrences.add(pick(event,"id","revision","startsAt","status"));}}}
   detail.add("occurrences",occurrences);out.add("item",detail);return out;
  }
  out.add("items",items);out.addProperty("revision",revision);out.addProperty("nextCursor",more?new CommunityCursor(offset+20,revision,1000,scope).encode():"");return out;
 }
 private static JsonObject pick(JsonObject source,String... names){var out=new JsonObject();for(String name:names)if(source.has(name))out.add(name,source.get(name).deepCopy());return out;}
}
