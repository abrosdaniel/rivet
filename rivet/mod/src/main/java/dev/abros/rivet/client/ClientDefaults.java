package dev.abros.rivet.client;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import java.nio.file.Files;
/** Apply the initial preference once, then preserve the player's chosen scale. */
final class ClientDefaults {
 static void initialize(){
  var minecraft=Minecraft.getInstance();
  var file=minecraft.gameDirectory.toPath().resolve("rivet/client-defaults.json");
  if(Files.exists(file))return;
  try{
   minecraft.options.guiScale().set(3);
   minecraft.options.save();
   var state=new com.google.gson.JsonObject();state.addProperty("guiScaleInitialized",true);
   Json.write(file,state);
   minecraft.execute(minecraft::resizeDisplay);
  }catch(Exception failure){Client.failure(failure);}
 }
 private ClientDefaults(){}
}
