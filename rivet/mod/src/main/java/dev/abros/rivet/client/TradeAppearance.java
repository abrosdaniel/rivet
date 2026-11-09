package dev.abros.rivet.client;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
/** Local presentation only: switching never replaces the container or its carried stack. */
final class TradeAppearance {
 private static boolean loaded,rivet=true;
 private static java.nio.file.Path file(){return Minecraft.getInstance().gameDirectory.toPath().resolve("rivet/trade-ui.json");}
 static boolean rivet(){if(!loaded){loaded=true;try{if(java.nio.file.Files.exists(file()))rivet=Json.opt(Json.read(file()),"style","rivet").equals("rivet");}catch(Exception failure){com.mojang.logging.LogUtils.getLogger().warn("Cannot read trade appearance",failure);}}return rivet;}
 static void toggle(){boolean next=!rivet();try{Json.write(file(),java.util.Map.of("style",next?"rivet":"minecraft"));rivet=next;}catch(Exception failure){Client.failure(failure);}}
 private TradeAppearance(){}
}
