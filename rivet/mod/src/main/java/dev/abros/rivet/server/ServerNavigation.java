package dev.abros.rivet.server;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.network.Protocol;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Coordinate links and narrowly scoped, server-authorized map teleportation. */
final class ServerNavigation {
 private static final Map<UUID,Boolean> teleportAccess=new HashMap<>();
 static void install(){
  NeoForge.EVENT_BUS.addListener(ServerNavigation::commands);
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->{
   if(e.getServer().getTickCount()%20!=0)return;
   for(var player:e.getServer().getPlayerList().getPlayers()){
    boolean allowed=canTeleport(player);Boolean previous=teleportAccess.put(player.getUUID(),allowed);
    if(previous==null||previous!=allowed)e.getServer().getCommands().sendCommands(player);
   }
  });
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e)->teleportAccess.remove(e.getEntity().getUUID()));
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e)->teleportAccess.clear());
 }
 static boolean canTeleport(ServerPlayer player){
  if(!player.connection.getConnection().isConnected()||!AuthServer.authenticated(player)||!ServerDatabase.settings().flag("map.enabled"))return false;
  if(player.hasPermissions(2))return true;
  if(!ServerIntegration.luckPermsEnabled())return false;
  return ServerPermissions.allowed(player,"rivet.map.teleport");
 }
 private static int teleport(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c)throws com.mojang.brigadier.exceptions.CommandSyntaxException{
  var player=c.getSource().getPlayerOrException();
  // Recheck here: an already parsed command or open map must not retain a revoked grant.
  if(!canTeleport(player))return 0;
  var dimension=net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"dimension");
  var level=player.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,dimension));
  int x=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"x"),y=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"y"),z=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"z");
  var pos=new net.minecraft.core.BlockPos(x,y,z);
  if(level==null||level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos))return 0;
  player.stopRiding();
  return player.teleportTo(level,x+.5,y+.5,z+.5,Set.of(),player.getYRot(),player.getXRot())?1:0;
 }
 private static int direction(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,boolean withY)throws com.mojang.brigadier.exceptions.CommandSyntaxException{var p=c.getSource().getPlayerOrException();if(!ServerDatabase.settings().flag("map.enabled")||!AuthServer.authenticated(p)||!ServerIntegration.supports(p,"social-display"))return 0;var dimension=net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"dimension");if(dimension==null)return 0;var j=new JsonObject();j.addProperty("kind","socialDirection");String author="";try{author=com.mojang.brigadier.arguments.StringArgumentType.getString(c,"author");}catch(IllegalArgumentException absent){/* Old coordinate links have no author. */}if(author.matches("[A-Za-z0-9_]{1,16}"))j.addProperty("name",dev.abros.rivet.core.Messages.text("rivet.ui.location_from_7537c568")+author);j.addProperty("dimension",dimension.toString());j.addProperty("y",withY?com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"y"):p.getBlockY());j.addProperty("x",com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"x"));j.addProperty("z",com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"z"));PacketDistributor.sendToPlayer(p,new Protocol.FeatureState(j.toString()));return 1;}

 private static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e){var root=Commands.literal("rivet");root.then(Commands.literal("direction").then(Commands.argument("dimension",net.minecraft.commands.arguments.ResourceLocationArgument.id()).then(Commands.argument("x",com.mojang.brigadier.arguments.IntegerArgumentType.integer(-30000000,30000000)).then(Commands.argument("z",com.mojang.brigadier.arguments.IntegerArgumentType.integer(-30000000,30000000)).executes(c->direction(c,false)).then(Commands.argument("y",com.mojang.brigadier.arguments.IntegerArgumentType.integer(-2048,2048)).executes(c->direction(c,true)).then(Commands.argument("author",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c->direction(c,true))))))));
  for(String action:List.of("shareMarker","addMarker"))root.then(Commands.literal(action).then(Commands.argument("snapshot",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c->{
   var p=c.getSource().getPlayerOrException();if(!AuthServer.authenticated(p)||!ServerDatabase.settings().flag("map.enabled")||!ServerIntegration.supports(p,"map-marker-links"))return 0;
   try{var marker=dev.abros.rivet.core.map.MapMarkerLink.decode(com.mojang.brigadier.arguments.StringArgumentType.getString(c,"snapshot"));
    if(action.equals("shareMarker"))return ServerChat.shareMarker(p,marker);
    var packet=new JsonObject();packet.addProperty("kind","mapMarkerLink");packet.add("marker",dev.abros.rivet.core.map.MapMarkerLink.json(marker));PacketDistributor.sendToPlayer(p,new Protocol.FeatureState(packet.toString()));return 1;
   }catch(IllegalArgumentException bad){p.sendSystemMessage(Component.translatable("rivet.map.invalidLink"));return 0;}
  })));
  root.then(Commands.literal("mapTeleport").requires(source->source.getEntity() instanceof ServerPlayer player&&canTeleport(player))
   .then(Commands.argument("dimension",net.minecraft.commands.arguments.ResourceLocationArgument.id())
    .then(Commands.argument("x",com.mojang.brigadier.arguments.IntegerArgumentType.integer(-29999984,29999983))
     .then(Commands.argument("y",com.mojang.brigadier.arguments.IntegerArgumentType.integer(-2048,2048))
      .then(Commands.argument("z",com.mojang.brigadier.arguments.IntegerArgumentType.integer(-29999984,29999983)).executes(ServerNavigation::teleport))))));
  e.getDispatcher().register(root);
 }

}
