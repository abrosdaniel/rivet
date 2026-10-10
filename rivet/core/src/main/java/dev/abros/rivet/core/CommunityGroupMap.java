package dev.abros.rivet.core;

import com.google.gson.*;
import dev.abros.rivet.core.map.*;
import java.util.*;

/** Group territory read model. Internal marker permissions are independent of public boundaries. */
final class CommunityGroupMap {
 private final PgDatabase db;private final CommunityStore store;
 CommunityGroupMap(PgDatabase db,CommunityStore store){this.db=db;this.store=store;}
 JsonObject read(CommunityStore.Actor actor,JsonObject in)throws Exception{
  store.modules().require("groups");String cursor=Json.opt(in,"cursor","");if(!cursor.isEmpty())UUID.fromString(cursor);
  var out=new JsonObject();out.addProperty("kind","community");out.addProperty("section","groups");out.addProperty("request",Json.opt(in,"request",""));var rows=new JsonArray();out.add("territories",rows);
  if(!store.enabled("groups"))return out;
  int limit=in.has("groupSnapshot")&&in.get("groupSnapshot").getAsBoolean()?4096:2;
  try(var q=db.connection().prepareStatement("SELECT d.id,d.body,coalesce((d.body->>'revision')::bigint,0),(SELECT r.value #>> '{}' FROM community_relations r WHERE r.document=d.id AND r.kind='members' AND r.actor=?) AS role FROM documents d WHERE d.section='groups' AND d.body->>'status'='open' AND d.id>? AND (jsonb_typeof(d.body->'territory')='object' OR ? OR d.body->>'owner'=? OR EXISTS(SELECT 1 FROM community_relations r WHERE r.document=d.id AND r.kind='members' AND r.actor=?)) ORDER BY d.id LIMIT ?")){
   q.setString(1,actor.id());q.setString(2,cursor);q.setBoolean(3,actor.admin());q.setString(4,actor.id());q.setString(5,actor.id());q.setInt(6,limit+1);
   try(var rs=q.executeQuery()){while(rs.next()){if(rows.size()==limit){if(limit==4096)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_f21d1e9e5aab"));out.addProperty("nextCursor",Json.str(rows.get(limit-1).getAsJsonObject(),"id"));break;}String id=rs.getString(1);var group=Json.parse(rs.getString(2));var row=new JsonObject();row.addProperty("id",id);row.addProperty("title",Json.str(group,"title"));row.addProperty("revision",rs.getLong(3));row.addProperty("manage",actor.admin()||actor.id().equals(Json.opt(group,"owner",""))||"assistant".equals(rs.getString(4)));if(group.has("territory"))row.add("territory",MapTerritoryJson.write(MapTerritoryJson.read(group.getAsJsonObject("territory"),UUID.fromString(id),Json.str(group,"title"))));rows.add(row);}}
  }return out;
 }
 void save(CommunityStore.Actor actor,JsonObject in,JsonObject group)throws Exception{
  store.modules().require("groups");if(!Json.str(group,"section").equals("groups")||!store.manager(group,actor))throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,dev.abros.rivet.core.Messages.text("rivet.core.insufficient_permissions_for_group_territory_208c4b80"));
  if(!in.has("revision")||in.get("revision").getAsLong()!=group.get("revision").getAsLong())throw new CommunityFailure(CommunityFailure.Code.CONFLICT,dev.abros.rivet.core.Messages.text("rivet.core.group_changed_reopen_the_territory_ba759bf3"));
  boolean clear=in.has("clearTerritory")&&in.get("clearTerritory").getAsBoolean();
  if(!clear&&!in.has("territory"))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.no_territory_specified_b8c51ada"));
  if(clear||in.get("territory").isJsonNull())group.remove("territory");
  else {var geometry=MapTerritoryJson.write(MapTerritoryJson.read(in.getAsJsonObject("territory"),UUID.fromString(Json.str(group,"id")),Json.str(group,"title")));geometry.remove("name");group.add("territory",geometry); }
  // Keep old location data for recovery; it is no longer returned or rendered as a group location.
  EntryHistory.append(group,"territory",actor.name(),System.currentTimeMillis());store.put(group);
 }
}
