package dev.abros.rivet.core.skins;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import java.util.*;

/** Saved combinations reference only the owner's library; applying is one database transaction. */
final class OutfitStore {
 private final PgDatabase db;private final SkinSettings settings;
 OutfitStore(PgDatabase db,SkinSettings settings){this.db=db;this.settings=settings;}
 private JsonArray rows(UUID owner)throws Exception{try(var q=db.connection().prepareStatement("SELECT body FROM records WHERE namespace='skin-outfits' AND id=?")){q.setString(1,owner.toString());try(var r=q.executeQuery()){return r.next()?Json.parse(r.getString(1)).getAsJsonArray("entries"):new JsonArray();}}}
 private Set<String> skins(UUID owner)throws Exception{var ids=new HashSet<String>();ids.add("");try(var q=db.connection().prepareStatement("SELECT id FROM skin_library WHERE owner=?")){q.setObject(1,owner);try(var r=q.executeQuery()){while(r.next())ids.add(r.getString(1));}}return ids;}
 JsonArray library(UUID owner)throws Exception{
  var rows=rows(owner);var skinIds=skins(owner);var capeIds=new HashSet<String>();capeIds.add("");for(var e:new CapeStore(db,settings).library(owner).getAsJsonArray("entries"))capeIds.add(Json.str(e.getAsJsonObject(),"id"));
  var appearance=new SkinStore(db,settings).appearance(owner);var cape=new CapeStore(db,settings).library(owner);if(!settings.capes()){cape.addProperty("active","");cape.addProperty("hidden",false);}
  for(var e:rows){var row=e.getAsJsonObject();if(Json.str(row,"skin").isEmpty())row.addProperty("hash",Json.str(appearance,"fallbackHash"));boolean enabled=skinIds.contains(Json.str(row,"skin"))&&capeIds.contains(Json.str(row,"cape"))&&(settings.capes()||Json.str(row,"cape").isEmpty()&&!row.get("hidden").getAsBoolean());row.addProperty("available",enabled);row.addProperty("selected",enabled&&Json.str(row,"skin").equals(Json.str(appearance,"active"))&&(Json.str(row,"skin").isEmpty()||row.get("slim").getAsBoolean()==appearance.get("slim").getAsBoolean())&&Json.str(row,"cape").equals(Json.str(cape,"active"))&&row.get("hidden").getAsBoolean()==cape.get("hidden").getAsBoolean());}
  return rows;
 }
 void mutate(UUID owner,String op,JsonObject input)throws Exception{
  if(!settings.enabled())throw new IllegalArgumentException(Messages.text("rivet.core.skins_disabled_4c1f50ce"));
  db.lock("skins");db.lock("capes");var rows=rows(owner);String id=Json.opt(input,"id","");JsonObject target=null;for(var e:rows)if(Json.str(e.getAsJsonObject(),"id").equals(id))target=e.getAsJsonObject();
  if(op.equals("outfitSave")){
   String name=Json.str(input,"name").strip();if(name.isEmpty()||name.length()>40||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException(Messages.text("rivet.core.name_1_40_characters_2e10bd2e"));if(rows.size()>=settings.maxOutfitsPerPlayer())throw new IllegalArgumentException(Messages.text("rivet.outfit.limit"));
   var skin=new SkinStore(db,settings).appearance(owner);var cape=new CapeStore(db,settings).library(owner);var row=new JsonObject();row.addProperty("id",UUID.randomUUID().toString());row.addProperty("name",name);row.addProperty("skin",Json.str(skin,"active"));row.addProperty("hash",Json.str(skin,"hash"));row.addProperty("slim",skin.get("slim").getAsBoolean());row.addProperty("cape",settings.capes()?Json.str(cape,"active"):"");row.addProperty("hidden",settings.capes()&&cape.get("hidden").getAsBoolean());rows.add(row);
  }else{
   if(target==null)throw new IllegalArgumentException(Messages.text("rivet.outfit.missing"));
   if(op.equals("outfitDelete"))rows.remove(target);
   else if(op.equals("outfitSelect")){
    boolean valid=false;for(var e:library(owner))if(Json.str(e.getAsJsonObject(),"id").equals(id))valid=e.getAsJsonObject().get("available").getAsBoolean();if(!valid)throw new IllegalArgumentException(Messages.text("rivet.outfit.missing"));
    var store=new SkinStore(db,settings);String skin=Json.str(target,"skin");if(!skin.isEmpty())store.change(owner,"model",skin,target.get("slim").getAsBoolean());store.change(owner,"select",skin,false);
    if(settings.capes()){var cape=new JsonObject();cape.addProperty("id",Json.str(target,"cape"));new CapeStore(db,settings).mutate(owner,"capeSelect",cape,null);if(target.get("hidden").getAsBoolean())new CapeStore(db,settings).mutate(owner,"capeHide",cape,null);}return;
   }else throw new IllegalArgumentException("Unknown outfit operation");
  }
  var document=new JsonObject();document.add("entries",rows);try(var q=db.connection().prepareStatement("INSERT INTO records(namespace,id,body) VALUES('skin-outfits',?,?::jsonb) ON CONFLICT(namespace,id) DO UPDATE SET body=excluded.body")){q.setString(1,owner.toString());q.setString(2,Json.GSON.toJson(document));q.executeUpdate();}
 }
}
