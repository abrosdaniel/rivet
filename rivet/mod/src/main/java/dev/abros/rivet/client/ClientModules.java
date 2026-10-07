package dev.abros.rivet.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.client.Minecraft;

/** Composition root: feature registration is explicit, never nested in another feature. */
final class ClientModules {
 private static Object connection;
 static void install(IEventBus bus){
  CompatibilityClient.install();
  AuthClient.install();
  SkinClient.install();
  ServerMenuClient.install(bus);
  SocialClient.install();
  RivetTab.install();
  PlayerNameplates.install();
  ClientChat.install();
  ClientNavigation.install();
  RivetHud.install(bus);
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e)->{
   var current=Minecraft.getInstance().getConnection();
   if(connection!=current){connection=current;ClientChat.reset();}
  });
 }
}
