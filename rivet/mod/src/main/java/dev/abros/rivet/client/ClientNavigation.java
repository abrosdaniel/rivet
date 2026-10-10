package dev.abros.rivet.client;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;

/** Navigation survives screen changes, and ends with its connection. */
final class ClientNavigation {
 private static Object connection;
 static void tick(){
  var current=Minecraft.getInstance().getConnection();
  if(connection!=current){connection=current;reset();}
  if(!WorldMapClient.allowed()){reset();}else {MapLayerClient.tick();MapActivities.tick();MapGroupClient.tick();MapPositions.tick();}
 }
 static void reset(){DirectionCue.clear();MapLayerClient.reset();MapActivities.reset();MapGroupClient.reset();MapPositions.reset();}
 static boolean receive(JsonObject packet){
  if(Json.opt(packet,"kind","").equals("socialDirection")){DirectionCue.receive(packet);return true;}
  return MapPositions.receive(packet)||MapLayerClient.receive(packet)||MapActivities.receive(packet)||MapGroupClient.receive(packet);
 }
}
