package dev.abros.rivet.core;

import com.google.gson.*;
import dev.abros.rivet.core.map.MapPositionPolicy;
import java.util.*;

/** Resolves audiences without ever reading or persisting player coordinates. */
final class CommunityMapPositions {
 private final PgDatabase db;private final CommunityStore store;
 CommunityMapPositions(PgDatabase db,CommunityStore store){this.db=db;this.store=store;}
 JsonObject request(CommunityStore.Actor actor,JsonObject in)throws Exception{
  String op=Json.str(in,"op");var out=new JsonObject();out.addProperty("kind","community");out.addProperty("section","home");out.addProperty("request",Json.opt(in,"request",""));
  if(op.equals("mapPositionSettings")||op.equals("mapPositionSave")){
   if(op.equals("mapPositionSave")){var policy=MapPositionPolicy.read(in.getAsJsonObject("settings"));db.lock("map-position:"+actor.id());new RequestJournal(db).execute(actor.id(),in,()->{store.record("map-position",actor.id(),policy.json());return new JsonObject();});}
   out.add("positionSettings",MapPositionPolicy.read(store.record("map-position",actor.id())).json());out.addProperty("positionGroups",store.modules().enabled("groups"));return out;
  }
  if(!op.equals("mapPositionPeers")||!in.has("positionCandidates"))throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,dev.abros.rivet.core.Messages.text("rivet.core.positions_unavailable_26a3ffbd"));
  var candidates=in.getAsJsonArray("positionCandidates");if(candidates.size()>(in.has("positionCompact")&&in.get("positionCompact").getAsBoolean()?dev.abros.rivet.core.map.MapPositionPage.SNAPSHOT_LIMIT:64))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.message.error_6335dff903bd"));var ids=new LinkedHashSet<String>();for(var id:candidates){String uuid=UUID.fromString(id.getAsString()).toString();if(!uuid.equals(actor.id()))ids.add(uuid);}
  if(ids.isEmpty()){out.add("positionPolicies",new JsonArray());return out;}
  var policies=new HashMap<String,MapPositionPolicy>();var shared=new HashSet<String>();
  long policyTiming=PerformanceMetrics.start();
  try(var q=db.connection().prepareStatement("SELECT id,body FROM records WHERE namespace='map-position' AND id=ANY(?)")){q.setArray(1,db.connection().createArrayOf("text",ids.toArray()));try(var rs=q.executeQuery()){while(rs.next())policies.put(rs.getString(1),MapPositionPolicy.read(Json.parse(rs.getString(2))));}}finally{PerformanceMetrics.end("map.positions.policies",policyTiming);}
  var groupIds=ids.stream().filter(id->{var policy=policies.getOrDefault(id,MapPositionPolicy.defaults());return policy.audience().equals("groups")&&!policy.mode().equals("hidden");}).toList();
  if(store.modules().enabled("groups")&&!groupIds.isEmpty()){long groupTiming=PerformanceMetrics.start();try(var q=db.connection().prepareStatement("SELECT DISTINCT p.actor FROM community_relations p JOIN community_relations v ON p.document=v.document JOIN documents d ON d.id=p.document WHERE p.kind='members' AND v.kind='members' AND v.actor=? AND p.actor=ANY(?) AND d.section='groups' AND d.body->>'status'='open'")){q.setString(1,actor.id());q.setArray(2,db.connection().createArrayOf("text",groupIds.toArray()));try(var rs=q.executeQuery()){while(rs.next())shared.add(rs.getString(1));}}finally{PerformanceMetrics.end("map.positions.groups",groupTiming);}}
  var rows=new JsonArray();for(String id:ids){var policy=policies.getOrDefault(id,MapPositionPolicy.defaults());if(policy.audienceAllows(shared.contains(id))){var row=policy.json();row.addProperty("uuid",id);rows.add(row);}}out.add("positionPolicies",rows);return out;
 }
}
