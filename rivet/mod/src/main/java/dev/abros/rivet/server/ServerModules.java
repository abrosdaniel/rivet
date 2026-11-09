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
  new ModuleRuntime.Module<>("pack",List.of("base"),e->ServerDatabase.settings().flag("pack.enabled"),e->ServerPack.start(),e->ServerPack.stop()),
  module("base",List.of(),e->ServerDatabase.get(),e->{}),
  new ModuleRuntime.Module<>("map",List.of("base"),e->ServerDatabase.settings().flag("map.enabled"),e->ServerMap.start(e.getServer()),e->ServerMap.stop()),
  new ModuleRuntime.Module<>("auth",List.of("base"),e->!ServerDatabase.settings().text("auth.mode").equals("false"),AuthServer::start,e->AuthServer.stop()),
  new ModuleRuntime.Module<>("skins",List.of("base"),e->ServerDatabase.settings().flag("skins.enabled"),e->ServerSkins.start(),e->ServerSkins.stop()),
  module("data",List.of("base","notifications"),e->ServerDataRuntime.start(),e->ServerDataRuntime.stop()),
  module("notifications",List.of("base"),e->ServerNotifications.start(),e->ServerNotifications.stop()),
  // Recovery and withdrawals remain available when new exchanges are disabled.
  module("trade",List.of("base","notifications"),e->ServerTrades.start(e.getServer()),e->ServerTrades.stop()),
  module("social",List.of("data","notifications"),ServerFeatures::start,e->ServerFeatures.stop()),
  module("changes",List.of("data","notifications"),e->ServerChangeDelivery.start(),e->ServerChangeDelivery.stop()),
  new ModuleRuntime.Module<>("server",List.of("social"),e->ServerDatabase.settings().modules().enabled("server"),ServerManagement::start,e->ServerManagement.stop()),
  new ModuleRuntime.Module<>("reports",List.of("social"),e->ServerDatabase.settings().modules().enabled("reports"),e->ServerReportsModule.start(),e->ServerReportsModule.stop()),
  module("statistics",List.of("social"),e->ServerPlayerStatistics.start(),e->ServerPlayerStatistics.stop()),
  new ModuleRuntime.Module<>("votes",List.of("social"),e->ServerDatabase.settings().flag("votes.enabled"),e->ServerModerationVotes.start(),e->ServerModerationVotes.stop()),
  module("display",List.of("social"),e->ServerSocial.start(),e->ServerSocial.stop()),
  new ModuleRuntime.Module<>("terrain",List.of("base"),e->ServerDatabase.settings().flag("api.terrain.enabled"),e->ServerTerrain.start(e.getServer()),e->ServerTerrain.stop()),
  module("api",List.of("social","notifications"),e->ServerApi.start(e.getServer()),e->ServerApi.stop()),
  new ModuleRuntime.Module<>("rest",List.of("api","social"),e->ServerDatabase.settings().flag("api.http.enabled")||ServerDatabase.settings().flag("api.websocket.enabled"),e->ServerRest.start(),e->ServerRest.stop()),
  new ModuleRuntime.Module<>("chat",List.of("display"),e->ServerDatabase.settings().flag("chat.enabled"),e->ServerChat.start(),e->ServerChat.stop())
 ));
 private static ModuleRuntime.Module<ServerStartingEvent> module(String id,List<String> requires,
   ModuleRuntime.Action<ServerStartingEvent> start,ModuleRuntime.Action<ServerStartingEvent> stop){
  return new ModuleRuntime.Module<>(id,requires,e->true,start,stop);
 }
 static void install(IEventBus bus,ModContainer container){
  ServerDataRuntime.install();ServerChangeDelivery.install();ServerNotifications.install();ServerTerrain.install();ServerRest.install();ServerApi.install();ServerApiKeys.install();ServerPack.install();AuthServer.install(bus,container);ServerSkins.install();ServerFeatures.install();
  ServerManagement.install();ServerReportsModule.install();ServerPlayerStatistics.install();ServerModerationVotes.install();ServerSocial.install();ServerChat.install();ServerNavigation.install();
  NeoForge.EVENT_BUS.addListener((ServerStartingEvent e)->{
   try{runtime.start(e);}catch(Exception failure){throw new IllegalStateException("Cannot start Rivet modules",failure);}
  });
  NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e)->stop());
  // Also covers a startup failure before ServerStoppingEvent.
  NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->stop());
 }
 private static void stop(){try{runtime.stop();}catch(Exception failure){com.mojang.logging.LogUtils.getLogger().error("Cannot stop Rivet modules",failure);}}
}
