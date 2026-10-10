package dev.abros.rivet.core;

import com.google.gson.*;
import java.util.*;

/** Bounded map read model. Authorization happens before coordinates leave the server. */
final class CommunityMapActivities {
 private final PgDatabase db;private final CommunityStore store;
 CommunityMapActivities(PgDatabase db,CommunityStore store){this.db=db;this.store=store;}
 JsonObject request(CommunityStore.Actor actor,JsonObject in)throws Exception{
  String source=Json.str(in,"source"),cursor=Json.opt(in,"cursor","");
  if(!Set.of("tasks","events","groupmarkers").contains(source))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_map_layer_7de329bc"));
  if(!cursor.isEmpty())UUID.fromString(cursor);
  var out=new JsonObject();out.addProperty("kind","community");out.addProperty("section","home");out.addProperty("request",Json.opt(in,"request",""));out.addProperty("source",source);var rows=new JsonArray();out.add("activities",rows);
  if(!store.modules().enabled(source.equals("groupmarkers")?"groups":source)||source.equals("events")&&!store.enabled("events")||source.equals("groupmarkers")&&!store.enabled("groups"))return out;
  boolean groups=store.modules().enabled("groups");long now=System.currentTimeMillis();
  String membership="EXISTS(SELECT 1 FROM documents g JOIN community_relations r ON r.document=g.id WHERE g.id=%s AND g.body->>'status'='open' AND r.kind='members' AND r.actor=?)";
  String sql;
  if(source.equals("groupmarkers"))sql="SELECT i.id,i.body,i.group_id FROM community_group_items i JOIN documents g ON g.id=i.group_id WHERE i.kind='place' AND i.id>? AND g.body->>'status'='open' AND jsonb_typeof(i.body->'location')='object' AND (? OR EXISTS(SELECT 1 FROM community_relations r WHERE r.document=g.id AND r.kind='members' AND r.actor=?)) ORDER BY i.id LIMIT 33";
  else if(source.equals("tasks"))sql="SELECT i.id,i.body,i.group_id,i.owner FROM community_group_items i WHERE i.kind='task' AND i.id>? AND jsonb_typeof(i.body->'location')='object' AND i.body->>'status' IN ('open','working') AND coalesce(i.body->>'archived','false')<>'true' AND ((i.group_id IS NULL AND i.owner=?) OR (? AND EXISTS(SELECT 1 FROM documents g WHERE g.id=i.group_id AND g.body->>'status'='open' AND (? OR "+membership.formatted("i.group_id")+")))) ORDER BY i.id LIMIT 33";
  else sql="SELECT d.id,d.body FROM documents d WHERE d.section='events' AND d.id>? AND d.body->>'status'='open' AND jsonb_typeof(d.body->'location')='object' AND (d.body->>'startsAt')::bigint + coalesce((d.body->>'durationMinutes')::bigint,60)*60000 > ? AND (? OR d.body->>'owner'=? OR coalesce(d.body->>'visibility','public')='public' OR d.body->>'visibility'='invited' AND jsonb_exists(d.body->'eventInvites',?) OR d.body->>'visibility'='group' AND ? AND "+membership.formatted("d.body->>'group'")+") AND (coalesce(d.body->'location'->>'membersOnly','false')<>'true' OR ? OR d.body->>'owner'=? OR ? AND "+membership.formatted("d.body->>'group'")+") ORDER BY d.id LIMIT 33";
  try(var q=db.connection().prepareStatement(sql)){
   int n=1;q.setString(n++,cursor);
   if(source.equals("groupmarkers")){q.setBoolean(n++,actor.admin());q.setString(n++,actor.id());}
   else if(source.equals("tasks")){q.setString(n++,actor.id());q.setBoolean(n++,groups);q.setBoolean(n++,actor.admin());q.setString(n++,actor.id());}
   else{q.setLong(n++,now);q.setBoolean(n++,actor.admin());q.setString(n++,actor.id());q.setString(n++,actor.id());q.setBoolean(n++,groups);q.setString(n++,actor.id());q.setBoolean(n++,actor.admin());q.setString(n++,actor.id());q.setBoolean(n++,groups);q.setString(n++,actor.id());}
   try(var rs=q.executeQuery()){while(rs.next()){
    if(rows.size()==32){out.addProperty("nextCursor",Json.str(rows.get(31).getAsJsonObject(),"id"));break;}
    String id=rs.getString(1);var body=Json.parse(rs.getString(2));var row=new JsonObject();
    row.addProperty("id",id);row.addProperty("title",Json.str(body,"title"));row.add("location",CommunityLocation.read(body.getAsJsonObject("location")).json());
    if(!source.equals("events"))row.addProperty("group",Objects.toString(rs.getString(3),""));
    else row.addProperty("endsAt",body.get("startsAt").getAsLong()+(body.has("durationMinutes")?body.get("durationMinutes").getAsLong():60)*60000L);
    rows.add(row);
   }}
  }
  return out;
 }
}
