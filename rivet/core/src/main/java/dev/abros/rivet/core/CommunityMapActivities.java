package dev.abros.rivet.core;

import com.google.gson.*;
import java.util.*;

/** Bounded map read model. Authorization happens before coordinates leave the server. */
final class CommunityMapActivities {
 private final PgDatabase db;private final CommunityStore store;
 CommunityMapActivities(PgDatabase db,CommunityStore store){this.db=db;this.store=store;}
 JsonObject request(CommunityStore.Actor actor,JsonObject in)throws Exception{
  String source=Json.str(in,"source"),cursor=Json.opt(in,"cursor","");
  RetiredFeatures.requireActive(source);
  if(!source.equals("groupmarkers"))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_map_layer_7de329bc"));
  if(!cursor.isEmpty())UUID.fromString(cursor);
  var out=new JsonObject();out.addProperty("kind","community");out.addProperty("section","home");out.addProperty("request",Json.opt(in,"request",""));out.addProperty("source",source);var rows=new JsonArray();out.add("activities",rows);
  if(!store.modules().enabled(source.equals("groupmarkers")?"groups":source)||source.equals("events")&&!store.enabled("events")||source.equals("groupmarkers")&&!store.enabled("groups"))return out;
  String sql="SELECT i.id,i.body,i.group_id FROM community_group_items i JOIN documents g ON g.id=i.group_id WHERE i.kind='place' AND i.id>? AND g.body->>'status'='open' AND jsonb_typeof(i.body->'location')='object' AND (? OR EXISTS(SELECT 1 FROM community_relations r WHERE r.document=g.id AND r.kind='members' AND r.actor=?)) ORDER BY i.id LIMIT 33";
  try(var q=db.connection().prepareStatement(sql)){
   q.setString(1,cursor);q.setBoolean(2,actor.admin());q.setString(3,actor.id());
   try(var rs=q.executeQuery()){while(rs.next()){
    if(rows.size()==32){out.addProperty("nextCursor",Json.str(rows.get(31).getAsJsonObject(),"id"));break;}
    String id=rs.getString(1);var body=Json.parse(rs.getString(2));var row=new JsonObject();
    row.addProperty("id",id);row.addProperty("title",Json.str(body,"title"));row.add("location",CommunityLocation.read(body.getAsJsonObject("location")).json());
    row.addProperty("group",Objects.toString(rs.getString(3),""));
    rows.add(row);
   }}
  }
  return out;
 }
}
