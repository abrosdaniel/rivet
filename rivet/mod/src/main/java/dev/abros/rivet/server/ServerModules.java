package dev.abros.rivet.server;

import dev.abros.rivet.core.modules.ModuleRuntime;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.*;
import java.util.List;

/** Owns server feature resources after configuration and PostgreSQL are ready. */
final class ServerModules {
 private static final ModuleRuntime<ServerStartingEvent> runtime=new ModuleRuntime<>(List.of(
  module("base",List.of(),e->ServerDatabase.get(),e->{}),
  new ModuleRuntime.Module<>("auth",List.of("base"),e->!ServerDatabase.settings().text("auth.mode").equals("false"),AuthServer::start,e->AuthServer.stop()),
  module("skins",List.of("base"),e->ServerSkins.start(),e->ServerSkins.stop()),
  module("social",List.of("base"),ServerFeatures::start,e->ServerFeatures.stop()),
  module("statistics",List.of("social"),e->ServerPlayerStatistics.start(),e->ServerPlayerStatistics.stop()),
  module("votes",List.of("social"),e->ServerModerationVotes.start(),e->ServerModerationVotes.stop()),
  module("display",List.of("social"),e->ServerSocial.start(),e->ServerSocial.stop()),
  new ModuleRuntime.Module<>("chat",List.of("display"),e->ServerDatabase.settings().flag("chat.enabled"),e->ServerChat.start(),e->ServerChat.stop())
 ));
 private static ModuleRuntime.Module<ServerStartingEvent> module(String id,List<String> requires,
   ModuleRuntime.Action<ServerStartingEvent> start,ModuleRuntime.Action<ServerStartingEvent> stop){
  return new ModuleRuntime.Module<>(id,requires,e->true,start,stop);
 }
 static void install(IEventBus bus,ModContainer container){
  AuthServer.install(bus,container);ServerSkins.install();ServerFeatures.install();
  ServerPlayerStatistics.install();ServerModerationVotes.install();ServerSocial.install();ServerChat.install();ServerNavigation.install();
  NeoForge.EVENT_BUS.addListener((ServerStartingEvent e)->{
   try{runtime.start(e);}catch(Exception failure){throw new IllegalStateException("Cannot start Rivet modules",failure);}
  });
  NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e)->stop());
  // Also covers a startup failure before ServerStoppingEvent.
  NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->stop());
 }
 private static void stop(){try{runtime.stop();}catch(Exception failure){com.mojang.logging.LogUtils.getLogger().error("Cannot stop Rivet modules",failure);}}
}
