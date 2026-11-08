package dev.abros.rivet.core;

import com.google.gson.*;
import java.util.*;

/** Server-authorized personal and group tools. Never trusts client visibility or role flags. */
final class CommunityPlus {
 private final PgDatabase db;private final CommunityStore store;
 CommunityPlus(PgDatabase db,CommunityStore store){this.db=db;this.store=store;}
 private static void require(boolean ok){if(!ok)throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,dev.abros.rivet.core.Messages.text("rivet.core.action_unavailable_c7b956d7"));}
 private static String text(JsonObject j,String key,int limit){String value=Json.opt(j,key,"").strip();if(value.length()>limit||value.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'))throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.invalid_field_1184c511")+key);return value;}
 private boolean member(String group,String player)throws Exception{if(group.isEmpty()||!store.modules().enabled("groups"))return false;try(var q=db.connection().prepareStatement("SELECT 1 FROM community_relations r JOIN documents d ON d.id=r.document WHERE r.document=? AND r.kind='members' AND r.actor=? AND d.body->>'status'='open'")){q.setString(1,group);q.setString(2,player);try(var rows=q.executeQuery()){return rows.next();}}}
 boolean visible(CommunityStore.Actor a,JsonObject j){return !RetiredFeatures.contains(Json.str(j,"section"));}
 void requireVisible(CommunityStore.Actor a,JsonObject j)throws Exception{if(!visible(a,j))throw new CommunityFailure(CommunityFailure.Code.NOT_FOUND,dev.abros.rivet.core.Messages.text("rivet.core.entry_unavailable_6fd8b0fc"));}
 void allowContact(String recipient,String sender)throws Exception{try(var q=db.connection().prepareStatement("SELECT 1 FROM community_ignores WHERE player=? AND target=?")){q.setString(1,recipient);q.setString(2,sender);try(var rows=q.executeQuery()){if(rows.next())throw new CommunityFailure(CommunityFailure.Code.FORBIDDEN,dev.abros.rivet.core.Messages.text("rivet.core.player_has_restricted_contact_b6ba8a00"));}}}
 private String person(String name)throws Exception{try(var q=db.connection().prepareStatement("SELECT id FROM people WHERE id=? OR lower(name)=lower(?)")){q.setString(1,name);q.setString(2,name);try(var rows=q.executeQuery()){if(!rows.next())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.player_not_found_b7489085")+name);String id=rows.getString(1);if(rows.next())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.ambiguous_player_name_specify_a_uuid_27787fcd"));return id;}}}
 JsonObject request(CommunityStore.Actor a,JsonObject in,String section)throws Exception{
  String op=Json.str(in,"op"),id=Json.opt(in,"id","");if(op.equals("plusTaskStatus")||op.equals("plusItemSave")&&Json.opt(in,"kind","").equals("task"))RetiredFeatures.requireActive("tasks");var out=new JsonObject();out.addProperty("kind","community");out.addProperty("section",section);out.addProperty("request",Json.opt(in,"request",""));
  if(op.equals("plusProfile")||op.equals("plusProfileSave")){
   String target=id.isEmpty()?a.id():id;UUID.fromString(target);
   if(op.endsWith("Save")){require(target.equals(a.id()));db.lock("profile:"+target);try(var q=db.connection().prepareStatement("INSERT INTO community_profile VALUES(?,?,?) ON CONFLICT(player) DO UPDATE SET about=excluded.about,interests=excluded.interests")){q.setString(1,target);q.setString(2,text(in,"about",300));q.setString(3,text(in,"interests",100));q.executeUpdate();}ChangeOutbox.add(db,"players",target,"");}
   var profile=new JsonObject();profile.addProperty("about","");profile.addProperty("interests","");try(var q=db.connection().prepareStatement("SELECT about,interests FROM community_profile WHERE player=?")){q.setString(1,target);try(var rows=q.executeQuery()){if(rows.next()){profile.addProperty("about",rows.getString(1));profile.addProperty("interests",rows.getString(2));}}}if(!new CommunityPlayerTools(db,store).visible(target,a.id())){profile.addProperty("about","");profile.addProperty("interests","");}out.add("profile",profile);return out;
  }
  if(op.equals("plusIgnore")||op.equals("plusIgnores")){
   if(op.equals("plusIgnore")){String target=person(text(in,"target",100));require(!target.equals(a.id()));boolean remove=in.has("remove")&&in.get("remove").getAsBoolean();db.lock("community:ignore:"+a.id());if(!remove)try(var limit=db.connection().prepareStatement("SELECT count(*) FROM community_ignores WHERE player=?")){limit.setString(1,a.id());try(var count=limit.executeQuery()){count.next();require(count.getInt(1)<100);}}try(var q=db.connection().prepareStatement(remove?"DELETE FROM community_ignores WHERE player=? AND target=?":"INSERT INTO community_ignores VALUES(?,?) ON CONFLICT DO NOTHING")){q.setString(1,a.id());q.setString(2,target);q.executeUpdate();}}
   var entries=new JsonArray();try(var q=db.connection().prepareStatement("SELECT i.target,p.name FROM community_ignores i LEFT JOIN people p ON p.id=i.target WHERE player=? ORDER BY p.name")){q.setString(1,a.id());try(var rows=q.executeQuery()){while(rows.next()){var row=new JsonObject();row.addProperty("id",rows.getString(1));row.addProperty("name",Objects.toString(rows.getString(2),rows.getString(1)));entries.add(row);}}}out.add("entries",entries);return out;
  }
  if(op.equals("plusItemRead")){
   require(section.equals("groups"));
   // Read object and authorization in one statement, without enriching every group relation.
   try(var q=db.connection().prepareStatement("SELECT i.body,i.revision,d.section,d.body->>'status',EXISTS (SELECT 1 FROM community_relations r WHERE r.document=d.id AND r.kind='members' AND r.actor=?) FROM documents d LEFT JOIN community_group_items i ON i.group_id=d.id AND i.id=? WHERE d.id=?")){
    q.setString(1,a.id());q.setString(2,Json.str(in,"itemId"));q.setString(3,id);
    try(var rows=q.executeQuery()){
     if(!rows.next())throw new CommunityFailure(CommunityFailure.Code.NOT_FOUND,Messages.text("rivet.core.entry_not_found_82764bdc"));
     require("groups".equals(rows.getString(3))&&"open".equals(rows.getString(4))&&(a.admin()||rows.getBoolean(5)));
     require(rows.getString(1)!=null);var item=Json.parse(rows.getString(1));
     if(Json.opt(item,"kind","").equals("task"))RetiredFeatures.requireActive("tasks");
     item.addProperty("revision",rows.getLong(2));out.add("item",item);return out;
    }
   }
  }
  var j=store.get(id,true);require(Json.str(j,"section").equals(section));requireVisible(a,j);require(Json.str(j,"status").equals("open"));boolean manager=store.manager(j,a),owner=Json.str(j,"owner").equals(a.id());
  if(op.equals("plusTerritorySave")){
   new CommunityGroupMap(db,store).save(a,in,j);
  }else if(op.equals("plusRemoveMember")){
   require(section.equals("groups")&&manager);String target=text(in,"target",100);require(!target.equals(Json.str(j,"owner"))&&j.getAsJsonObject("members").has(target));require(owner||a.admin()||j.has("assistantRemoval")&&j.get("assistantRemoval").getAsBoolean()&&j.getAsJsonObject("members").get(target).getAsString().equals("member"));String reason=text(in,"reason",300);require(!reason.isBlank());j.getAsJsonObject("members").remove(target);store.put(j);store.note(target,"groups",id,LocalizedText.parts(LocalizedText.key("rivet.core.you_have_been_removed_from_f0e84803"),LocalizedText.literal(Json.str(j,"title")),LocalizedText.literal(": "),LocalizedText.literal(reason)));
  }else if(op.equals("plusAssistantRemoval")){require(section.equals("groups")&&(owner||a.admin()));j.addProperty("assistantRemoval",in.get("enabled").getAsBoolean());store.put(j);
  }else if(Set.of("plusItemSave","plusItemDelete").contains(op)){
   require(section.equals("groups")&&(member(id,a.id())||a.admin()));String item=Json.opt(in,"itemId","");JsonObject previous=null;long revision=0;
   if(!item.isEmpty())try(var q=db.connection().prepareStatement("SELECT body,revision FROM community_group_items WHERE id=? AND group_id=? FOR UPDATE")){q.setString(1,item);q.setString(2,id);try(var rows=q.executeQuery()){require(rows.next());previous=Json.parse(rows.getString(1));revision=rows.getLong(2);}}
   if(previous!=null&&Json.opt(previous,"kind","").equals("task"))store.modules().require("tasks");
   if(previous!=null&&(!in.has("itemRevision")||in.get("itemRevision").getAsLong()!=revision))throw new CommunityFailure(CommunityFailure.Code.CONFLICT,dev.abros.rivet.core.Messages.text("rivet.core.entry_changed_reopen_it_f2f4b795"));
   {require(manager);if(op.equals("plusItemDelete")){require(previous!=null);try(var q=db.connection().prepareStatement("DELETE FROM community_group_items WHERE id=? AND group_id=?")){q.setString(1,item);q.setString(2,id);q.executeUpdate();}}
    else{String kind=text(in,"kind",20);require(kind.equals("place"));if(previous!=null)require(kind.equals(Json.str(previous,"kind")));var body=previous==null?new JsonObject():previous.deepCopy();String title=text(in,"title",100);require(!title.isBlank());body.addProperty("title",title);body.addProperty("kind",kind);body.addProperty("description",text(in,"description",1000));
     require(in.has("location")&&!in.get("location").isJsonNull());var location=CommunityLocation.read(in.getAsJsonObject("location"));body.add("location",new CommunityLocation(location.name(),location.dimension(),location.x(),location.y(),location.z(),true).json());
     if(item.isEmpty()){try(var q=db.connection().prepareStatement("SELECT count(*) FROM community_group_items WHERE group_id=? AND kind='place'")){q.setString(1,id);try(var rows=q.executeQuery()){rows.next();require(rows.getInt(1)<200);}}item=UUID.randomUUID().toString();}saveItem(item,id,body,revision+1);
    }
   }out.addProperty("changedId",item);out.addProperty("changedRevision",revision+1);store.put(j);ChangeOutbox.add(db,"home",id,"");
  }else throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.unknown_action_ea069893"));
  var read=in.deepCopy();read.addProperty("op","detail");read.remove("cursor");var result=store.execute(a,read,"detail",section);for(var entry:out.entrySet())if(entry.getKey().startsWith("changed"))result.add(entry.getKey(),entry.getValue());return result;
 }
 private void saveItem(String id,String group,JsonObject body,long revision)throws Exception{try(var q=db.connection().prepareStatement("INSERT INTO community_group_items(id,group_id,kind,body,revision) VALUES(?,?,?,?::jsonb,?) ON CONFLICT(id) DO UPDATE SET body=excluded.body,revision=excluded.revision")){q.setString(1,id);q.setString(2,group);q.setString(3,Json.str(body,"kind"));q.setString(4,body.toString());q.setLong(5,revision);q.executeUpdate();}}
 void enrich(CommunityStore.Actor a,JsonObject source,JsonObject detail,int page)throws Exception{
  String section=Json.str(source,"section"),id=Json.str(source,"id");
  if(section.equals("groups")){
   boolean member=member(id,a.id())||a.admin();String visibility="group_id=? AND kind='place' AND ?";var items=new JsonArray();
   try(var q=db.connection().prepareStatement("SELECT count(*) FROM community_group_items WHERE "+visibility)){q.setString(1,id);q.setBoolean(2,member);try(var rows=q.executeQuery()){rows.next();detail.addProperty("groupItemsCount",rows.getInt(1));}}
   try(var q=db.connection().prepareStatement("SELECT id,body,revision FROM community_group_items WHERE "+visibility+" ORDER BY kind,id LIMIT 5 OFFSET ?")){q.setString(1,id);q.setBoolean(2,member);q.setInt(3,page*5);try(var rows=q.executeQuery()){while(rows.next()){var body=Json.parse(rows.getString(2));body.addProperty("id",rows.getString(1));body.addProperty("revision",rows.getLong(3));items.add(body);}}}
   detail.add("groupItems",items);detail.addProperty("canRemoveMember",Json.str(source,"owner").equals(a.id())||a.admin()||store.manager(source,a)&&source.has("assistantRemoval")&&source.get("assistantRemoval").getAsBoolean());
  }

 }
 void profiles(JsonArray entries)throws Exception{
  var ids=new ArrayList<String>();for(var e:entries)ids.add(Json.str(e.getAsJsonObject(),"uuid"));try(var q=db.connection().prepareStatement("SELECT player,about,interests FROM community_profile WHERE player=ANY(?)")){q.setArray(1,db.connection().createArrayOf("text",ids.toArray()));try(var rows=q.executeQuery()){while(rows.next())for(var e:entries){var row=e.getAsJsonObject();if(Json.str(row,"uuid").equals(rows.getString(1))){row.addProperty("about",rows.getString(2));row.addProperty("interests",rows.getString(3));}}}}
 }
 void home(CommunityStore.Actor a,JsonObject out,JsonArray groups)throws Exception{
  out.add("tasks",new JsonArray());var invitations=new JsonArray();if(store.modules().enabled("groups"))try(var q=db.connection().prepareStatement("SELECT body FROM community_documents WHERE section='groups' AND body->>'status'='open' AND jsonb_exists(body->'invitations',?) ORDER BY sequence DESC LIMIT 5")){q.setString(1,a.id());try(var rows=q.executeQuery()){while(rows.next()){var group=Json.parse(rows.getString(1));var row=CommunityPreview.of(group);dev.abros.rivet.core.LocalizedText.key("rivet.ui.you_have_been_invited_78128de8").put(row,"attention");invitations.add(row);}}}out.add("invitations",invitations);
  for(var entry:groups){var option=entry.getAsJsonObject();var group=store.get(Json.str(option,"value"));option.addProperty("membersCount",group.getAsJsonObject("members").size());var ids=new JsonArray();group.getAsJsonObject("members").keySet().forEach(ids::add);option.add("memberIds",ids);
   if(store.manager(group,a))option.addProperty("applicationsCount",group.getAsJsonObject("applications").size());}
 }
}
