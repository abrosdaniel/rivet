package dev.abros.rivet.client;
import dev.abros.rivet.core.Json;
import java.util.*;
import net.minecraft.client.Minecraft;
/** One client-wide configuration, deliberately independent of server addresses. */
final class HudSettings {
 static final List<String> BLOCKS=List.of("server","profile","groups","unread");
 static final List<String> PROFILE_FIELDS=List.of("head","name","prefix","suffix","session","ping");
 static final float DEFAULT_SCALE=1.25f;
 static final HudSettings INSTANCE=new HudSettings();
 boolean enabled=true,chat=true,inventory=false,pause=false,debug=false,dimMoving=true,quietHours=false;int quietStart=22,quietEnd=8;float scale=DEFAULT_SCALE,opacity=.82f;
 boolean digest=true,hintsPositioned=false;int hintAnchorX=2,hintAnchorY=2,hintOffsetX=8,hintOffsetY=48;
 float hintScale=1,toastScale=1;
 int anchorX=2,anchorY=1,offsetX=8,offsetY=0,toastCount=2;
 final List<String> order=new ArrayList<>(BLOCKS);final Set<String> hidden=new HashSet<>(List.of("groups")),muted=new HashSet<>();
 final Set<String> profileHidden=new HashSet<>(List.of("session","ping"));
 String error="";
 private HudSettings(){try{var file=file();if(java.nio.file.Files.exists(file)){var j=Json.read(file);hintScale=clamp(num(j,"hintScale",1),.65f,2);toastScale=clamp(num(j,"toastScale",1),.65f,2);digest=bool(j,"digest",true);hintsPositioned=bool(j,"hintsPositioned",false);hintAnchorX=(int)clamp(num(j,"hintAnchorX",2),0,2);hintAnchorY=(int)clamp(num(j,"hintAnchorY",2),0,2);hintOffsetX=(int)clamp(num(j,"hintOffsetX",8),-4096,4096);hintOffsetY=(int)clamp(num(j,"hintOffsetY",48),-4096,4096);dimMoving=bool(j,"dimMoving",true);quietHours=bool(j,"quietHours",false);quietStart=(int)clamp(num(j,"quietStart",22),0,23);quietEnd=(int)clamp(num(j,"quietEnd",8),0,23);enabled=bool(j,"enabled",true);chat=bool(j,"chat",true);inventory=bool(j,"inventory",false);pause=bool(j,"pause",false);debug=bool(j,"debug",false);scale=clamp(num(j,"scale",DEFAULT_SCALE),.65f,1.5f);opacity=clamp(num(j,"opacity",.82f),.35f,1);anchorX=(int)clamp(num(j,"anchorX",2),0,2);anchorY=(int)clamp(num(j,"anchorY",1),0,2);offsetX=(int)clamp(num(j,"offsetX",8),-4096,4096);offsetY=(int)clamp(num(j,"offsetY",0),-4096,4096);toastCount=(int)clamp(num(j,"toastCount",2),1,5);if(j.has("order")){order.clear();for(var e:j.getAsJsonArray("order"))if(BLOCKS.contains(e.getAsString())&&!order.contains(e.getAsString()))order.add(e.getAsString());for(String k:BLOCKS)if(!order.contains(k))order.add(k.equals("profile")?Math.min(1,order.size()):order.size(),k);}for(String key:List.of("hidden","muted"))if(j.has(key)){var target=key.equals("hidden")?hidden:muted;target.clear();for(var e:j.getAsJsonArray(key))target.add(e.getAsString());}if(j.has("profileHidden")){profileHidden.clear();for(var e:j.getAsJsonArray("profileHidden"))if(PROFILE_FIELDS.contains(e.getAsString()))profileHidden.add(e.getAsString());}}}catch(Exception e){error=Client.text("ui.could_not_read_hud_settings_656eebf1");}}
 private static java.nio.file.Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("rivet/hud.json");}
 static boolean bool(com.google.gson.JsonObject j,String k,boolean d){return j.has(k)?j.get(k).getAsBoolean():d;}
 static float num(com.google.gson.JsonObject j,String k,float d){return j.has(k)?j.get(k).getAsFloat():d;}
 static float clamp(float v,float min,float max){return Float.isFinite(v)?Math.max(min,Math.min(max,v)):min;}
 void save(){try{var j=java.nio.file.Files.exists(file())?Json.read(file()):new com.google.gson.JsonObject();j.addProperty("hintScale",hintScale);j.addProperty("toastScale",toastScale);j.addProperty("digest",digest);j.addProperty("hintsPositioned",hintsPositioned);j.addProperty("hintAnchorX",hintAnchorX);j.addProperty("hintAnchorY",hintAnchorY);j.addProperty("hintOffsetX",hintOffsetX);j.addProperty("hintOffsetY",hintOffsetY);j.addProperty("dimMoving",dimMoving);j.addProperty("quietHours",quietHours);j.addProperty("quietStart",quietStart);j.addProperty("quietEnd",quietEnd);j.addProperty("enabled",enabled);j.addProperty("chat",chat);j.addProperty("inventory",inventory);j.addProperty("pause",pause);j.addProperty("debug",debug);j.addProperty("scale",scale);j.addProperty("opacity",opacity);j.addProperty("anchorX",anchorX);j.addProperty("anchorY",anchorY);j.addProperty("offsetX",offsetX);j.addProperty("offsetY",offsetY);j.addProperty("toastCount",toastCount);j.add("order",Json.GSON.toJsonTree(order));j.add("hidden",Json.GSON.toJsonTree(hidden));j.add("muted",Json.GSON.toJsonTree(muted));j.add("profileHidden",Json.GSON.toJsonTree(profileHidden));Json.write(file(),j);error="";}catch(Exception e){error=Client.text("ui.could_not_save_hud_settings_c3e063a3");}}
 void preset(int preset){
  if(preset<0||preset>2)throw new IllegalArgumentException("Unknown HUD preset");
  enabled=true;hidden.removeAll(BLOCKS);profileHidden.clear();
  if(preset==0)hidden.addAll(List.of("server","profile","groups"));
  if(preset==1)hidden.add("groups");
  if(preset<2)profileHidden.addAll(List.of("session","ping"));
 }
 static String label(String key){return switch(key){case "profile"->Client.text("ui.mini_profile_53da0746");case "server"->Client.text("ui.server_and_players_affd6ba4");case "groups"->Client.text("server.groups");case "unread"->Client.text("ui.unread_cf3ac2d4");default->key;};}
}
