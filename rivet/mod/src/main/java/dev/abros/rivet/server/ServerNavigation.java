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

/** Stateless coordinate-link command; it shares no chat or display caches. */
final class ServerNavigation {
 static void install(){NeoForge.EVENT_BUS.addListener(ServerNavigation::commands);}
 private static int direction(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c,boolean withY)throws com.mojang.brigadier.exceptions.CommandSyntaxException{var p=c.getSource().getPlayerOrException();if(!ServerDatabase.settings().flag("map.enabled")||!AuthServer.authenticated(p)||!ServerIntegration.supports(p,"social-display"))return 0;var dimension=net.minecraft.commands.arguments.ResourceLocationArgument.getId(c,"dimension");if(dimension==null)return 0;var j=new JsonObject();j.addProperty("kind","socialDirection");String author="";try{author=com.mojang.brigadier.arguments.StringArgumentType.getString(c,"author");}catch(IllegalArgumentException absent){/* Old coordinate links have no author. */}if(author.matches("[A-Za-z0-9_]{1,16}"))j.addProperty("name",dev.abros.rivet.core.Messages.text("rivet.ui.location_from_7537c568")+author);j.addProperty("dimension",dimension.toString());j.addProperty("y",withY?com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"y"):p.getBlockY());j.addProperty("x",com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"x"));j.addProperty("z",com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"z"));PacketDistributor.sendToPlayer(p,new Protocol.FeatureState(j.toString()));return 1;}

 private static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e){var root=Commands.literal("rivet");root.then(Commands.literal("direction").then(Commands.argument("dimension",net.minecraft.commands.arguments.ResourceLocationArgument.id()).then(Commands.argument("x",com.mojang.brigadier.arguments.IntegerArgumentType.integer(-30000000,30000000)).then(Commands.argument("z",com.mojang.brigadier.arguments.IntegerArgumentType.integer(-30000000,30000000)).executes(c->direction(c,false)).then(Commands.argument("y",com.mojang.brigadier.arguments.IntegerArgumentType.integer(-2048,2048)).executes(c->direction(c,true)).then(Commands.argument("author",com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c->direction(c,true))))))));
  e.getDispatcher().register(root);
 }

}
