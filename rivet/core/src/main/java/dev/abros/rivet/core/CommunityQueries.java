package dev.abros.rivet.core;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Read models with keyset continuations, visibility checks and explicit archive filtering. */
final class CommunityQueries {
 private final PgDatabase db;
 CommunityQueries(PgDatabase db){this.db=db;}
 private String scope(CommunityStore.Actor actor,JsonObject input,String section){return Hashes.sha256((actor.id()+"|"+actor.admin()+"|"+section+"|"+Json.opt(input,"query","")+"|"+Json.opt(input,"member","")+"|"+Json.opt(input,"sort","default")+"|"+Boolean.toString(input.has("mine")&&input.get("mine").getAsBoolean())+"|"+Boolean.toString(input.has("participating")&&input.get("participating").getAsBoolean())+"|"+Boolean.toString(input.has("archive")&&input.get("archive").getAsBoolean())+"|"+Boolean.toString(input.has("trash")&&input.get("trash").getAsBoolean())).getBytes(StandardCharsets.UTF_8));}
 private long ceiling(String table)throws Exception{try(var q=db.connection().createStatement();var row=q.executeQuery("SELECT coalesce(max("+(table.equals("notices")?"id":"sequence")+"),0) FROM "+table)){row.next();return row.getLong(1);}}
 JsonArray list(CommunityStore.Actor actor,JsonObject input,String section,long now,JsonObject config)throws Exception {
  // Home owns its dedicated group/announcement projections. Never query archived activities.
  if(section.equals("home"))return new JsonArray();
  RetiredFeatures.requireActive(section);
  if(!section.equals("groups")||!config.getAsJsonArray("sections").contains(new JsonPrimitive("groups")))return new JsonArray();
  String scope=scope(actor,input,section),token=Json.opt(input,"cursor","");
  CommunityCursor after=token.isEmpty()?null:CommunityCursor.decode(token,scope);
  long cap=after==null?ceiling("documents"):after.ceiling();
  String sort=Json.opt(input,"sort","default");
  if(!Set.of("default","recent","oldest").contains(sort))throw new IllegalArgumentException(Messages.text("rivet.core.unknown_entry_order_1f3ef4aa"));
  boolean ascending=sort.equals("oldest");
  var args=new ArrayList<Object>();
  args.add(actor.admin());args.add(actor.id());
  args.add(input.has("mine")&&input.get("mine").getAsBoolean());args.add(actor.id());
  args.add(Json.opt(input,"member",""));args.add(Json.opt(input,"member",""));
  args.add(Json.opt(input,"query",""));args.add(cap);
  String sql="SELECT id,sequence FROM documents WHERE section='groups' AND (body->>'status'<>'hidden' OR ? OR body->>'owner'=?) AND (NOT ? OR body->>'owner'=?) AND (?='' OR EXISTS(SELECT 1 FROM community_relations rel WHERE rel.document=documents.id AND rel.kind='members' AND rel.actor=?)) AND strpos(lower(body->>'title'),lower(?))>0 AND sequence<=?";
  boolean trash=input.has("trash")&&input.get("trash").getAsBoolean();
  if(trash){sql+=" AND body->>'status'='deleted' AND (? OR body->>'owner'=?)";args.add(actor.admin());args.add(actor.id());}
  else {
   boolean archive=input.has("archive")&&input.get("archive").getAsBoolean();
   sql+=" AND body->>'status'<>'deleted' AND "+(archive?"":"NOT ")+"(body->>'status' IN ('closed','done','declined','cancelled','hidden'))";
  }
  if(input.has("participating")&&input.get("participating").getAsBoolean()){
   sql+=" AND EXISTS(SELECT 1 FROM community_relations rel WHERE rel.document=documents.id AND rel.kind IN ('members','applications','invitations') AND rel.actor=?)";
   args.add(actor.id());
  }
  if(after!=null){sql+=" AND sequence"+(ascending?">?":"<?");args.add(after.sequence());}
  sql+=" ORDER BY sequence"+(ascending?" ASC":" DESC")+" LIMIT 11";
  // Reconstruct child collections only for the selected page, never for the full archive.
  sql="WITH selected AS MATERIALIZED ("+sql+") SELECT (SELECT body FROM community_documents full_document WHERE full_document.id=selected.id),selected.sequence FROM selected ORDER BY selected.sequence"+(ascending?" ASC":" DESC");
  var out=new JsonArray();long last=0;boolean more=false;
  try(var q=db.connection().prepareStatement(sql)){
   for(int i=0;i<args.size();i++)q.setObject(i+1,args.get(i));
   try(var rows=q.executeQuery()){while(rows.next()){
    if(out.size()==10){more=true;break;}
    var document=Json.parse(rows.getString(1));var preview=CommunityPreview.of(document);
    preview.addProperty("isOwner",Json.str(document,"owner").equals(actor.id()));
    out.add(preview);last=rows.getLong(2);
   }}
  }
  if(more)out.get(out.size()-1).getAsJsonObject().addProperty("_nextCursor",new CommunityCursor(last,0,cap,scope).encode());
  return out;
 }
 JsonArray notices(CommunityStore.Actor actor,JsonObject input)throws Exception {
  String category=Json.opt(input,"category","");if(!category.isEmpty()&&!NotificationCategories.valid(category))throw new IllegalArgumentException(Messages.text("rivet.core.unknown_category_0a688127"));
  return new NotificationStore(db).page(actor.id(),category,Json.opt(input,"cursor",""),scope(actor,input,"notifications")+category);
 }
}
