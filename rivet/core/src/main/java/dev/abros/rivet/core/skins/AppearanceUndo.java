package dev.abros.rivet.core.skins;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.*;
import java.util.UUID;

/** One owner-scoped undo of the whole appearance, in the mutation's transaction. */
final class AppearanceUndo {
 private final PgDatabase db;private final SkinSettings settings;
 AppearanceUndo(PgDatabase db,SkinSettings settings){this.db=db;this.settings=settings;}
 JsonObject snapshot(UUID owner)throws Exception{
  var skin=new SkinStore(db,settings).appearance(owner);var cape=new CapeStore(db,settings).library(owner);var out=new JsonObject();
  String active=Json.str(skin,"active");out.addProperty("skin",active);out.addProperty("slim",!active.isEmpty()&&skin.get("slim").getAsBoolean());
  out.addProperty("cape",Json.str(cape,"active"));out.addProperty("hidden",cape.get("hidden").getAsBoolean());return out;
 }
 private JsonObject read(UUID owner)throws Exception{try(var q=db.connection().prepareStatement("SELECT body FROM records WHERE namespace='appearance-undo' AND id=?")){q.setString(1,owner.toString());try(var r=q.executeQuery()){return r.next()?Json.parse(r.getString(1)):null;}}}
 void clear(UUID owner)throws Exception{try(var q=db.connection().prepareStatement("DELETE FROM records WHERE namespace='appearance-undo' AND id=?")){q.setString(1,owner.toString());q.executeUpdate();}}
 void remember(UUID owner,JsonObject before)throws Exception{
  var after=snapshot(owner);if(before.equals(after))return;
  var record=new JsonObject();record.add("before",before);record.add("after",after);
  try(var q=db.connection().prepareStatement("INSERT INTO records(namespace,id,body) VALUES('appearance-undo',?,?::jsonb) ON CONFLICT(namespace,id) DO UPDATE SET body=excluded.body")){q.setString(1,owner.toString());q.setString(2,Json.GSON.toJson(record));q.executeUpdate();}
 }
 boolean available(UUID owner)throws Exception{var record=read(owner);return record!=null&&valid(owner,record);}
 private boolean valid(UUID owner,JsonObject record)throws Exception{
  if(!settings.enabled()||!snapshot(owner).equals(record.getAsJsonObject("after")))return false;
  var previous=record.getAsJsonObject("before");String skin=Json.str(previous,"skin"),cape=Json.str(previous,"cape");
  if(!skin.isEmpty())try(var q=db.connection().prepareStatement("SELECT 1 FROM skin_library WHERE owner=? AND id=?")){q.setObject(1,owner);q.setObject(2,UUID.fromString(skin));try(var r=q.executeQuery()){if(!r.next())return false;}}
  if(!settings.capes()&&(!cape.equals(Json.str(record.getAsJsonObject("after"),"cape"))||previous.get("hidden").getAsBoolean()!=record.getAsJsonObject("after").get("hidden").getAsBoolean()))return false;
  if(!cape.isEmpty()){boolean found=false;for(var e:new CapeStore(db,settings).library(owner).getAsJsonArray("entries"))if(Json.str(e.getAsJsonObject(),"id").equals(cape))found=true;if(!found)return false;}
  return true;
 }
 void restore(UUID owner)throws Exception{
  db.lock("skins");db.lock("capes");var record=read(owner);if(record==null||!valid(owner,record))throw new IllegalArgumentException(Messages.text("rivet.skin.undo.unavailable"));
  var previous=record.getAsJsonObject("before");var store=new SkinStore(db,settings);String skin=Json.str(previous,"skin");
  if(!skin.isEmpty())store.change(owner,"model",skin,previous.get("slim").getAsBoolean());store.change(owner,"select",skin,false);
  if(settings.capes()){var input=new JsonObject();input.addProperty("id",Json.str(previous,"cape"));var capes=new CapeStore(db,settings);capes.mutate(owner,"capeSelect",input,null);if(previous.get("hidden").getAsBoolean())capes.mutate(owner,"capeHide",input,null);}
  try(var q=db.connection().prepareStatement("UPDATE skin_profiles SET undo_available=FALSE,undo_active=NULL WHERE owner=?")){q.setObject(1,owner);q.executeUpdate();}clear(owner);
 }
}
