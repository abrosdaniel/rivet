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
  if(!WorldMapClient.allowed()){reset();}else {MapLayerClient.tick();MapActivities.tick();MapGroupClient.tick();MapPositions.tick();MapPublications.tick();MapIntegrationLayers.tick();}
 }
 static void reset(){DirectionCue.clear();MapLayerClient.reset();MapActivities.reset();MapGroupClient.reset();MapPositions.reset();MapPublications.reset();MapIntegrationLayers.reset();}
 static boolean receive(JsonObject packet){
  if(Json.opt(packet,"kind","").equals("mapMarkerLink")){
   if(WorldMapClient.markersReady()&&WorldMapClient.allowed())try{
    var marker=dev.abros.rivet.core.map.MapMarkerLink.read(packet.getAsJsonObject("marker"));var mc=Minecraft.getInstance();var parent=mc.screen;
    mc.setScreen(new UiConfirmDialog(yes->{mc.setScreen(parent);if(yes){try{WorldMapClient.put(marker);}catch(RuntimeException error){mc.setScreen(new TextScreen(parent,Client.tr("map.title"),error.getMessage()));}}},Client.tr("map.addShared"),net.minecraft.network.chat.Component.literal(marker.name()+" · "+marker.x()+", "+marker.y()+", "+marker.z())));
   }catch(RuntimeException ignored){}return true;
  }

  if(Json.opt(packet,"kind","").equals("socialDirection")){DirectionCue.receive(packet);return true;}
  return MapIntegrationLayers.receive(packet)||MapPublications.receive(packet)||MapPositions.receive(packet)||MapLayerClient.receive(packet)||MapActivities.receive(packet)||MapGroupClient.receive(packet);
 }
}
