package dev.abros.rivet.core.skins;

import com.google.gson.*;
import dev.abros.rivet.core.*;
import java.util.*;

/** UUID-owned capes use the existing transactional records store; blobs are content addressed. */
public final class CapeStore {
 private final PgDatabase db;private final SkinSettings settings;
 public CapeStore(PgDatabase db,SkinSettings settings){this.db=db;this.settings=settings;}
 private JsonObject read(String namespace,String id)throws Exception{try(var q=db.connection().prepareStatement("SELECT body FROM records WHERE namespace=? AND id=?")){q.setString(1,namespace);q.setString(2,id);try(var r=q.executeQuery()){return r.next()?Json.parse(r.getString(1)):new JsonObject();}}}
 private void write(String namespace,String id,JsonObject value)throws Exception{try(var q=db.connection().prepareStatement("INSERT INTO records(namespace,id,body) VALUES(?,?,?::jsonb) ON CONFLICT(namespace,id) DO UPDATE SET body=excluded.body")){q.setString(1,namespace);q.setString(2,id);q.setString(3,Json.GSON.toJson(value));q.executeUpdate();}}
 private JsonObject profile(UUID owner)throws Exception{var p=read("cape-profile",owner.toString());if(!p.has("entries"))p.add("entries",new JsonArray());if(!p.has("active"))p.addProperty("active","");if(!p.has("hidden"))p.addProperty("hidden",false);return p;}
 public JsonObject library(UUID owner)throws Exception{return db.communityTransaction(()->{var p=profile(owner);p.addProperty("enabled",settings.enabled()&&settings.capes());p.addProperty("limit",settings.maxCapesPerPlayer());p.addProperty("maxBytes",settings.maxCapeBytes());p.addProperty("maxResolution",settings.maxCapeResolution());return p;});}
 public JsonObject appearance(UUID owner)throws Exception{return db.communityTransaction(()->{var p=profile(owner);var out=new JsonObject();out.addProperty("capeHidden",settings.enabled()&&settings.capes()&&p.get("hidden").getAsBoolean());out.addProperty("capeHash","");if(settings.enabled()&&settings.capes()&&!p.get("hidden").getAsBoolean())for(var e:p.getAsJsonArray("entries")){var row=e.getAsJsonObject();if(Json.str(row,"id").equals(Json.str(p,"active")))out.addProperty("capeHash",Json.str(row,"hash"));}return out;});}
 public void mutate(UUID owner,String op,JsonObject input,byte[] data)throws Exception{
  if(!settings.enabled()||!settings.capes())throw new IllegalArgumentException(Messages.text("rivet.cape.disabled"));
  byte[] png=op.equals("capeUpload")?CapeImage.normalize(data,settings.maxCapeBytes(),settings.maxCapeResolution()):null;
  db.communityTransaction(()->{db.lock("capes");var p=profile(owner);var entries=p.getAsJsonArray("entries");String id=Json.opt(input,"id","");
   switch(op){
    case "capeUpload"->{String name=Json.str(input,"name").strip();if(name.isEmpty()||name.length()>40||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException(Messages.text("rivet.core.name_1_40_characters_2e10bd2e"));String hash=Hashes.sha256(png);JsonObject found=null;for(var e:entries)if(Json.str(e.getAsJsonObject(),"hash").equals(hash))found=e.getAsJsonObject();if(found==null){if(entries.size()>=settings.maxCapesPerPlayer())throw new IllegalArgumentException(Messages.text("rivet.cape.limit"));found=new JsonObject();found.addProperty("id",UUID.randomUUID().toString());found.addProperty("name",name);found.addProperty("hash",hash);entries.add(found);var blob=new JsonObject();blob.addProperty("png",Base64.getEncoder().encodeToString(png));write("cape-image",hash,blob);}p.addProperty("active",Json.str(found,"id"));p.addProperty("hidden",false);}
    case "capeSelect","capeDelete"->{JsonObject found=null;for(var e:entries)if(Json.str(e.getAsJsonObject(),"id").equals(id))found=e.getAsJsonObject();if(found==null&&(!id.isEmpty()||op.equals("capeDelete")))throw new IllegalArgumentException(Messages.text("rivet.cape.missing"));if(op.equals("capeSelect")){p.addProperty("active",id);p.addProperty("hidden",false);}else{entries.remove(found);if(Json.str(p,"active").equals(id))p.addProperty("active","");}}
    case "capeHide"->p.addProperty("hidden",true);
    default->throw new IllegalArgumentException("Unknown cape operation");
   }
   write("cape-profile",owner.toString(),p);
   if(op.equals("capeDelete"))try(var q=db.connection().createStatement()){q.executeUpdate("DELETE FROM records image WHERE image.namespace='cape-image' AND NOT EXISTS(SELECT 1 FROM records p CROSS JOIN LATERAL jsonb_array_elements(CASE WHEN p.namespace='cape-profile' THEN p.body->'entries' ELSE '[]'::jsonb END) e WHERE p.namespace='cape-profile' AND e->>'hash'=image.id)");}
   return null;
  });
 }
 public byte[] bytes(String hash)throws Exception{if(!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid cape hash");return db.communityTransaction(()->{var blob=read("cape-image",hash);if(!blob.has("png"))throw new IllegalArgumentException(Messages.text("rivet.cape.missing"));return Base64.getDecoder().decode(Json.str(blob,"png"));});}
}
