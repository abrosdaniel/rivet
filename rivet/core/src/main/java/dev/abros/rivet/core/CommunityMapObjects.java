package dev.abros.rivet.core;

import com.google.gson.*;
import dev.abros.rivet.core.map.*;
import java.util.*;

/** Private review drafts and published snapshots are separate; client visibility flags grant no rights. */
final class CommunityMapObjects {
 private final PgDatabase db;private final CommunityStore store;
 CommunityMapObjects(PgDatabase db,CommunityStore store){this.db=db;this.store=store;}
 private static void require(boolean allowed){if(!allowed)throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,Messages.text("rivet.core.action_unavailable_c7b956d7"));}
 JsonObject request(CommunityStore.Actor actor,JsonObject in)throws Exception{
  var out=new JsonObject();out.addProperty("kind","community");out.addProperty("section","home");out.addProperty("request",Json.opt(in,"request",""));
  String op=Json.str(in,"op");
  if(op.equals("mapObjects")){
   String cursor=Json.opt(in,"cursor","");if(!cursor.isEmpty())cursor=UUID.fromString(cursor).toString();
   boolean manage=in.has("manage")&&in.get("manage").getAsBoolean();var rows=new JsonArray();int pageChars=0;
   db.lock("map-publication-version");var versionRow=store.record("map-publication-state","version");long version=publicVersion(versionRow);out.addProperty("snapshot",version);
   if(!manage&&in.has("deltaProtocol")&&in.get("deltaProtocol").getAsBoolean())return changes(in,out,versionRow,version);
   if(!manage&&in.has("snapshot")){long expected=in.get("snapshot").getAsBigDecimal().longValueExact();if(cursor.isEmpty()&&expected==version){out.addProperty("unchanged",true);return out;}if(!cursor.isEmpty()&&expected!=version){out.addProperty("restart",true);return out;}}
   String target=Json.opt(in,"id","");if(!target.isEmpty())target=UUID.fromString(target).toString();
   String filter=manage?"(body->>'owner'=? OR ?)":"jsonb_typeof(body->'published')='object'";
   try(var q=db.connection().prepareStatement("SELECT id,body FROM records WHERE namespace='map-object' AND id>? AND "+filter+" AND (?='' OR id=?) ORDER BY id LIMIT 17")){
    q.setString(1,cursor);if(manage){q.setString(2,actor.id());q.setBoolean(3,actor.admin()&&!(in.has("mine")&&in.get("mine").getAsBoolean()));}q.setString(manage?4:2,target);q.setString(manage?5:3,target);
    try(var rs=q.executeQuery()){while(rs.next()){
     if(rows.size()==16){out.addProperty("nextCursor",Json.str(rows.get(15).getAsJsonObject(),"id"));break;}
     var row=Json.parse(rs.getString(2));row.addProperty("id",rs.getString(1));if(!manage)row=published(row);int size=wireRowSize(row);if(pageChars+size>28000&&!rows.isEmpty()){out.addProperty("nextCursor",Json.str(rows.get(rows.size()-1).getAsJsonObject(),"id"));break;}rows.add(row);pageChars+=size;
    }}
   }out.add("mapObjects",rows);out.addProperty("admin",actor.admin());return out;
  }
  String id=UUID.fromString(Json.str(in,"id")).toString();db.lock("map-object-quota:"+actor.id());db.lock("map-object:"+id);
  db.lock("map-publication-version");var row=store.record("map-object",id);
  if(row==null){require(op.equals("mapObjectSubmit"));try(var q=db.connection().prepareStatement("SELECT count(*) FROM records WHERE namespace='map-object' AND body->>'owner'=?")){q.setString(1,actor.id());try(var rs=q.executeQuery()){rs.next();require(rs.getInt(1)<MapPublicationLimits.PER_AUTHOR);}}
   try(var q=db.connection().prepareStatement("SELECT count(*) FROM records WHERE namespace='map-object'")){try(var rs=q.executeQuery()){rs.next();require(rs.getInt(1)<MapPublicationLimits.TOTAL);}}
   row=new JsonObject();row.addProperty("owner",actor.id());row.addProperty("author",actor.name());row.addProperty("revision",0);
  }
  var previousPublished=row.has("published")?row.get("published").deepCopy():null;
  long revision=row.get("revision").getAsLong();if(!in.has("revision")||in.get("revision").getAsBigDecimal().longValueExact()!=revision)throw new CommunityFailure(CommunityFailure.Code.CONFLICT,Messages.text("rivet.core.location_changed_reopen_it_1d7882a8"));
  switch(op){
   case "mapObjectSubmit"->{require(Json.str(row,"owner").equals(actor.id()));String type=Json.str(in,"type");require(type.equals("marker")||type.equals("territory"));if(row.has("type"))require(type.equals(Json.str(row,"type")));row.addProperty("type",type);var payload=in.getAsJsonObject("draft");row.add("draft",type.equals("marker")?MapMarkerLink.json(MapMarkerLink.read(payload)):MapTerritoryJson.write(MapTerritoryJson.read(payload,UUID.fromString(id))));}
   case "mapObjectApprove"->{require(actor.admin()&&row.has("draft"));row.add("published",row.remove("draft"));}
   case "mapObjectReject"->{require(actor.admin()&&row.has("draft"));row.remove("draft");}
   case "mapObjectWithdraw"->{require(actor.admin()||Json.str(row,"owner").equals(actor.id()));row.remove("published");row.remove("draft");}
   default->throw new IllegalArgumentException("Unknown map operation");
  }
  db.lock("map-publication-version");var versionRow=store.record("map-publication-state","version");long version=versionRow==null?0:versionRow.get("value").getAsLong();var nextVersion=new JsonObject();nextVersion.addProperty("value",version+1);
  long publicVersion=publicVersion(versionRow);nextVersion.addProperty("publicVersion",publicVersion);nextVersion.addProperty("floor",versionRow!=null&&versionRow.has("floor")?versionRow.get("floor").getAsLong():publicVersion);
  boolean publicChanged=!Objects.equals(previousPublished,row.get("published"));
  if(publicChanged){publicVersion++;nextVersion.addProperty("publicVersion",publicVersion);row.addProperty("publicationRevision",publicVersion);}
  store.record("map-publication-state","version",nextVersion);
  // Global revisions prevent an old approval from matching a withdrawn and resubmitted ID.
  row.addProperty("revision",version+1);if(row.has("draft")||row.has("published"))store.record("map-object",id,row);else store.deleteRecord("map-object",id);
  if(publicChanged){
   var change=new JsonObject();change.addProperty("id",id);change.addProperty("revision",publicVersion);
   if(row.has("published")){change.addProperty("type",Json.str(row,"type"));change.add("published",row.get("published").deepCopy());}else change.addProperty("removed",true);
   store.record("map-publication-change",id,change);
   try(var q=db.connection().prepareStatement("DELETE FROM records WHERE namespace='map-publication-change' AND id IN (SELECT id FROM records WHERE namespace='map-publication-change' ORDER BY (body->>'revision')::bigint DESC OFFSET ?) RETURNING body")){
    q.setInt(1,MapPublicationLimits.TOTAL);try(var result=q.executeQuery()){long floor=nextVersion.get("floor").getAsLong();while(result.next())floor=Math.max(floor,Json.parse(result.getString(1)).get("revision").getAsLong());nextVersion.addProperty("floor",floor);}
   }store.record("map-publication-state","version",nextVersion);
   out.add("publicationChange",change);
  }
  out.addProperty("saved",true);out.addProperty("revision",version+1);return out;
 }

 // Rows are nested four spaces deeper inside the pretty-printed response array.
 private static int wireRowSize(JsonObject row){String encoded=Json.GSON.toJson(row);return encoded.length()+4*((int)encoded.chars().filter(c->c=='\n').count()+1)+2;}
 private static long publicVersion(JsonObject state){return state==null?0:state.get(state.has("publicVersion")?"publicVersion":"value").getAsLong();}
 private static JsonObject published(JsonObject row){var out=new JsonObject();out.add("id",row.get("id"));out.add("type",row.get("type"));out.add("published",row.get("published"));out.add("revision",row.has("publicationRevision")?row.get("publicationRevision"):row.get("revision"));return out;}
 private JsonObject changes(JsonObject in,JsonObject out,JsonObject state,long version)throws Exception{
  long since=in.has("since")?in.get("since").getAsBigDecimal().longValueExact():-1;
  String cursor=Json.opt(in,"cursor","");if(!cursor.isEmpty())cursor=UUID.fromString(cursor).toString();
  long until=cursor.isEmpty()?version:in.get("until").getAsBigDecimal().longValueExact();
  if(since < -1||since>version||until>version||until<Math.max(0,since))throw new IllegalArgumentException("Invalid map version");
  long floor=state!=null&&state.has("floor")?state.get("floor").getAsLong():version;
  if(since>=0&&since<floor){out.addProperty("restart",true);return out;}
  out.addProperty("delta",since>=0);out.addProperty("snapshot",until);
  if(since==version){out.addProperty("unchanged",true);return out;}
  var rows=new JsonArray();int size=0;
  String sql=since<0?"SELECT id,body FROM records WHERE namespace='map-object' AND jsonb_typeof(body->'published')='object' AND id>? ORDER BY id LIMIT 129":"SELECT id,body FROM records WHERE namespace='map-publication-change' AND (body->>'revision')::bigint>? AND (body->>'revision')::bigint<=? AND id>? ORDER BY id LIMIT 129";
  try(var q=db.connection().prepareStatement(sql)){
   if(since<0)q.setString(1,cursor);else{q.setLong(1,since);q.setLong(2,until);q.setString(3,cursor);}
   try(var rs=q.executeQuery()){while(rs.next()){
    var row=Json.parse(rs.getString(2));row.addProperty("id",rs.getString(1));if(since<0)row=published(row);
    int length=wireRowSize(row);
    if((size+length>28000||rows.size()==128)&&!rows.isEmpty()){out.addProperty("nextCursor",Json.str(rows.get(rows.size()-1).getAsJsonObject(),"id"));break;}
    rows.add(row);size+=length;
   }}
  }out.add("mapObjects",rows);return out;
 }
}
