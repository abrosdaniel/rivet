package dev.abros.rivet.client;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
/** Client presentation preferences. The server alone selects compatibility modes. */
final class SocialSettings {
 static final SocialSettings INSTANCE=new SocialSettings();
 int grouping=0,density=1,columnWidth=190;double hintOpacity=.95;double tabOpacity=.65;boolean hints=true,heads=true,ping=false,footer=true,dedupe=true;
 private SocialSettings(){try{var p=file();if(java.nio.file.Files.exists(p)){var j=Json.read(p);hintOpacity=j.has("hintOpacity")?HudSettings.clamp(j.get("hintOpacity").getAsFloat(),0,1):.95;grouping=mode(j,"grouping");density=j.has("density")?mode(j,"density"):1;columnWidth=j.has("columnWidth")?Math.max(160,Math.min(260,j.get("columnWidth").getAsInt())):190;tabOpacity=j.has("tabOpacity")?Math.max(0,Math.min(1,j.get("tabOpacity").getAsDouble())):.65;hints=HudSettings.bool(j,"hints",true);heads=HudSettings.bool(j,"heads",true);ping=HudSettings.bool(j,"ping",false);footer=HudSettings.bool(j,"footer",true);dedupe=HudSettings.bool(j,"dedupe",true);}}catch(Exception failure){com.mojang.logging.LogUtils.getLogger().warn("Cannot load social preferences",failure);}}
 private static int mode(com.google.gson.JsonObject j,String k){return j.has(k)?Math.max(0,Math.min(2,j.get(k).getAsInt())):0;}
 private static java.nio.file.Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("rivet/social.json");}
 void save(){try{var j=new com.google.gson.JsonObject();j.addProperty("hintOpacity",hintOpacity);j.addProperty("grouping",grouping);j.addProperty("density",density);j.addProperty("columnWidth",columnWidth);j.addProperty("tabOpacity",tabOpacity);j.addProperty("hints",hints);j.addProperty("heads",heads);j.addProperty("ping",ping);j.addProperty("footer",footer);j.addProperty("dedupe",dedupe);Json.write(file(),j);}catch(Exception error){Client.failure(error);}}
 boolean tab(){return serverMode("tabMode").equals("rivet")||serverMode("tabMode").equals("auto")&&!conflict("tab","bettertab","tabby","tablist");}
 static boolean chatEnabled(){var state=ServerMenuClient.state;return !state.has("chatEnabled")||state.get("chatEnabled").getAsBoolean();}
 boolean chat(){return chatEnabled();}
 static String serverMode(String key){return Json.opt(ServerMenuClient.state,key,"compatible");}
 private static boolean conflict(String...ids){for(String id:ids)if(net.neoforged.fml.ModList.get().isLoaded(id))return true;return false;}
}
