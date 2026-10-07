package dev.abros.rivet.client;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.common.NeoForge;

/** Navigation survives screen changes, and ends with its connection. */
final class ClientNavigation {
 private static Object connection;
 static void install(){
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiEvent.Post e)->DirectionCue.draw(e.getGuiGraphics()));
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e)->{
   var current=Minecraft.getInstance().getConnection();
   if(connection!=current){connection=current;DirectionCue.clear();MapLayerClient.reset();}
   MapLayerClient.tick();
  });
 }
 static boolean receive(JsonObject packet){
  if(Json.opt(packet,"kind","").equals("socialDirection")){DirectionCue.receive(packet);return true;}
  return MapLayerClient.receive(packet);
 }
}
