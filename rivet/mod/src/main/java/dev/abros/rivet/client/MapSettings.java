package dev.abros.rivet.client;

import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;

/** Local display choices; terrain and markers remain scoped to the world and player. */
final class MapSettings {
 static final int DEFAULT_SIZE=112;
 static void initialize(){}
 static final MapSettings INSTANCE=new MapSettings();
 dev.abros.rivet.core.map.MapHudPreferences hud=new dev.abros.rivet.core.map.MapHudPreferences();
 final MapLayers layers=new MapLayers();
 boolean deathMap=true,deathMinimap=true;
 boolean enabled=true,round=false,rotate=true,coordinates=true,biome=false,time=false,weather=false;
 int size=DEFAULT_SIZE;float zoom=1,opacity=1;String error="";
 private MapSettings(){try{var p=file();var j=java.nio.file.Files.exists(p)?Json.read(p):new com.google.gson.JsonObject();var legacy=p.resolveSibling("hud.json");hud=dev.abros.rivet.core.map.MapHudPreferences.read(j,!j.has("hud")&&java.nio.file.Files.exists(legacy)?Json.read(legacy):new com.google.gson.JsonObject());{MapRenderSettings.INSTANCE.load(j.has("display")?j.getAsJsonObject("display"):null);MapCaves.load(j.has("caves")?j.getAsJsonObject("caves"):null);layers.load(j.has("layers")?j.getAsJsonObject("layers"):null);deathMap=bool(j,"deathMap",true);deathMinimap=bool(j,"deathMinimap",true);enabled=bool(j,"enabled",true);round=bool(j,"round",false);rotate=bool(j,"rotate",true);coordinates=bool(j,"coordinates",true);biome=bool(j,"biome",false);time=bool(j,"time",false);weather=bool(j,"weather",false);size=(int)clamp(num(j,"size",DEFAULT_SIZE),80,192);zoom=clamp(num(j,"zoom",1),.25f,4);opacity=clamp(num(j,"opacity",1),.2f,1);}if(!j.has("hud"))save();}catch(Exception ex){error=Client.text("ui.could_not_read_map_settings_9d3bfaf2");}}
 private static boolean bool(com.google.gson.JsonObject j,String k,boolean d){return j.has(k)?j.get(k).getAsBoolean():d;}
 private static float num(com.google.gson.JsonObject j,String k,float d){return j.has(k)?j.get(k).getAsFloat():d;}
 private static float clamp(float value,float min,float max){return Float.isFinite(value)?Math.clamp(value,min,max):min;}
 private static java.nio.file.Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("rivet/map-settings.json");}
 void save(){try{var j=new com.google.gson.JsonObject();j.add("layers",layers.json());j.add("hud",hud.json());j.add("caves",MapCaves.json());j.add("display",MapRenderSettings.INSTANCE.json());j.addProperty("deathMap",deathMap);j.addProperty("deathMinimap",deathMinimap);j.addProperty("enabled",enabled);j.addProperty("round",round);j.addProperty("rotate",rotate);j.addProperty("coordinates",coordinates);j.addProperty("biome",biome);j.addProperty("time",time);j.addProperty("weather",weather);j.addProperty("size",size);j.addProperty("zoom",zoom);j.addProperty("opacity",opacity);Json.write(file(),j);error="";}catch(Exception ex){error=Client.text("ui.could_not_save_map_settings_996967ff");}}
 void reset(){MapRenderSettings.INSTANCE.copy(new MapRenderSettings());MapCaves.clearPreferences();deathMap=deathMinimap=true;enabled=true;round=false;rotate=true;coordinates=true;biome=time=weather=false;size=DEFAULT_SIZE;zoom=1;opacity=1;save();}
}
