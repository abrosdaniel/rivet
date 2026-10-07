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

/** Server channel routing, permission checks and message presentation. */
final class ServerChat {
 private static long nextSample;
 private static final Set<UUID> emptyLocalWarned=new HashSet<>();
 static void start(){nextSample=0;emptyLocalWarned.clear();}
 static void stop(){emptyLocalWarned.clear();}
 static void install(){
  NeoForge.EVENT_BUS.addListener(ServerChat::chat);
  NeoForge.EVENT_BUS.addListener(ServerChat::commands);
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e)->emptyLocalWarned.remove(e.getEntity().getUUID()));
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->{
   long now=System.currentTimeMillis();if(now<nextSample)return;nextSample=now+1000;
   var settings=ServerDatabase.settings();if(!settings.flag("chat.enabled")||!settings.flag("chat.local")||emptyLocalWarned.isEmpty())return;
   int radius=settings.number("chat.localRadius");emptyLocalWarned.removeIf(id->{var sender=e.getServer().getPlayerList().getPlayer(id);return sender==null||e.getServer().getPlayerList().getPlayers().stream().anyMatch(recipient->recipient!=sender&&localRecipient(sender,recipient,radius));});
  });
 }
 private static Component name(ServerPlayer p){var lp=ServerSocial.metadata(p);return dev.abros.rivet.network.ChatPresentation.identity(Json.opt(lp,"prefix",""),p.getGameProfile().getName(),Json.opt(lp,"suffix",""));}
 private static Component formatted(ServerPlayer p,String label,Component content,String colorKey){var settings=ServerDatabase.settings();return dev.abros.rivet.network.ChatPresentation.format(settings.text("chat.format"),settings.text("chat.channelFormat"),label,p.getUUID(),name(p),content,dev.abros.rivet.core.ChatChannels.color(settings.text("chat."+colorKey)));}
 private static Component sender(Component message,ServerPlayer p){return message.copy().withStyle(style->style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,new HoverEvent.EntityTooltipInfo(net.minecraft.world.entity.EntityType.PLAYER,p.getUUID(),name(p)))));}
 private static void active(ServerPlayer p){ServerSocial.active(p);}
 private static void chat(net.neoforged.neoforge.event.ServerChatEvent e){
  var player=e.getPlayer();if(!AuthServer.authenticated(player)||e.isCanceled()||!ServerDatabase.settings().flag("chat.enabled"))return;active(player);
  var parsed=dev.abros.rivet.core.ChatChannels.parse(e.getRawText(),ServerDatabase.settings().flag("chat.local"));
  if(parsed.text().isBlank()){e.setCanceled(true);return;}
  if(parsed.channel()==dev.abros.rivet.core.ChatChannels.Channel.LOCAL){e.setCanceled(true);channel(player,"local",parsed.text());return;}
  if(parsed.channel()==dev.abros.rivet.core.ChatChannels.Channel.GROUP){e.setCanceled(true);groupChat(player,parsed);return;}
  // Keep Minecraft's signed broadcast for the global channel.
  if(e.getMessage().getString().equals(e.getRawText()))e.setMessage(sender(content(player,parsed.text()),player));
 }
 private static void groupChat(ServerPlayer player,dev.abros.rivet.core.ChatChannels.Message message){
  if((!ServerDatabase.settings().flag("chat.group")||!ServerDatabase.settings().modules().enabled("groups"))){player.sendSystemMessage(Component.literal("Чат объединений выключен на сервере."));return;}
  var store=ServerFeatures.communityStore();if(store==null)return;Object epoch=ServerSocial.generation();
  try{ServerFeatures.storage(()->{try{var own=store.playerGroups(player.getUUID().toString());var matches=own.stream().filter(row->message.group().isBlank()||Json.str(row,"title").equalsIgnoreCase(message.group())||Json.str(row,"id").equals(message.group())).toList();
   player.server.execute(()->{if(epoch!=ServerSocial.generation()||player.server.getPlayerList().getPlayer(player.getUUID())!=player||!AuthServer.authenticated(player))return;
    if(matches.size()==1)channel(player,Json.str(matches.getFirst(),"id"),message.text());
    else player.sendSystemMessage(Component.literal(matches.isEmpty()?"Объединение не найдено или вы не состоите в нём.":"Укажите объединение: #Название: сообщение. Доступны: "+own.stream().map(row->Json.str(row,"title")).collect(java.util.stream.Collectors.joining(", "))));
   });}catch(Exception failure){player.server.execute(()->{if(epoch==ServerSocial.generation())player.sendSystemMessage(Component.literal("Не удалось проверить участие в объединении. Повторите сообщение."));});}});
  }catch(java.util.concurrent.RejectedExecutionException full){player.sendSystemMessage(Component.literal("Чат занят. Повторите сообщение."));}
 }
 private static Component content(ServerPlayer p,String message){var out=Component.empty();String[] parts=message.split("(?<=\\[pos\\])|(?=\\[pos\\])");for(String part:parts){if(part.equals("[pos]")&&ServerDatabase.settings().flag("chat.coordinates")){var place=Component.literal("[").append(dev.abros.rivet.network.DimensionLabels.name(p.level().dimension().location().toString())).append(" · "+p.getBlockX()+", "+p.getBlockY()+", "+p.getBlockZ()+"]");out.append(place.withStyle(s->s.withColor(0x83C6C4).withUnderlined(true).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal("Место от "+p.getGameProfile().getName()+".\nНажмите, чтобы начать навигацию."))).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/rivet direction "+p.level().dimension().location()+" "+p.getBlockX()+" "+p.getBlockZ()+" "+p.getBlockY()+" "+p.getGameProfile().getName()))));}else out.append(Component.literal(part));}return out;}
 private static boolean localRecipient(ServerPlayer sender,ServerPlayer recipient,int radius){return AuthServer.authenticated(recipient)&&recipient.level()==sender.level()&&recipient.distanceToSqr(sender)<=radius*(double)radius;}
 private static int channel(ServerPlayer p,String channel,String message){if(!ServerDatabase.settings().flag("chat.enabled")||!AuthServer.authenticated(p)||message.isBlank()||message.length()>256)return 0;active(p);var settings=ServerDatabase.settings();
  if(channel.equals("local")){if(!settings.flag("chat.local"))return 0;int radius=settings.number("chat.localRadius");var audience=p.server.getPlayerList().getPlayers().stream().filter(v->localRecipient(p,v,radius)).toList();deliver(p,settings.text("chat.localName"),message,audience);boolean alone=audience.stream().noneMatch(recipient->recipient!=p);if(!alone)emptyLocalWarned.remove(p.getUUID());if(alone&&emptyLocalWarned.add(p.getUUID()))p.sendSystemMessage(Component.literal("Рядом нет игроков. Для общего чата начните сообщение с !").withStyle(style->style.withColor(0xE2BE75)));return audience.size();}
  if(!settings.flag("chat.group"))return 0;try{UUID.fromString(channel);}catch(IllegalArgumentException bad){return 0;}var store=ServerFeatures.communityStore();if(store==null)return 0;Object epoch=ServerSocial.generation();var body=content(p,message);
  try{ServerFeatures.storage(()->{try{var members=store.chatMembers(channel,p.getUUID().toString());var own=store.playerGroups(p.getUUID().toString());String label=own.stream().filter(j->Json.str(j,"id").equals(channel)).map(j->Json.str(j,"title")).findFirst().orElse("Объединение");p.server.execute(()->{if(epoch!=ServerSocial.generation()||p.server.getPlayerList().getPlayer(p.getUUID())!=p||!AuthServer.authenticated(p))return;var audience=p.server.getPlayerList().getPlayers().stream().filter(v->AuthServer.authenticated(v)&&members.contains(v.getUUID().toString())).toList();var formatted=formatted(p,label,body,"groupColor");for(var recipient:audience)recipient.sendSystemMessage(sender(formatted,p));com.mojang.logging.LogUtils.getLogger().info("[Rivet group] {}: {}",p.getGameProfile().getName(),message);});}catch(Exception denied){p.server.execute(()->{if(epoch==ServerSocial.generation())p.sendSystemMessage(Component.literal("Сообщение не отправлено: проверьте участие в объединении."));});}});}catch(java.util.concurrent.RejectedExecutionException full){p.sendSystemMessage(Component.literal("Чат занят. Повторите сообщение."));return 0;}return 1;
 }
 private static void deliver(ServerPlayer p,String label,String message,List<ServerPlayer> audience){var line=formatted(p,label,content(p,message),"localColor");for(var recipient:audience)recipient.sendSystemMessage(sender(line,p));com.mojang.logging.LogUtils.getLogger().info("[Rivet {}] {}: {}",label,p.getGameProfile().getName(),message);}
 private static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e){var root=Commands.literal("rivet");
  var chat=Commands.literal("chat").requires(source->ServerDatabase.settings().flag("chat.enabled"));chat.then(Commands.literal("local").then(Commands.argument("message",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c->channel(c.getSource().getPlayerOrException(),"local",com.mojang.brigadier.arguments.StringArgumentType.getString(c,"message")))));chat.then(Commands.literal("group").then(Commands.argument("group",com.mojang.brigadier.arguments.StringArgumentType.word()).suggests((c,b)->{var p=c.getSource().getPlayer();if(p!=null){ServerSocial.suggestGroups(p,b);}return b.buildFuture();}).then(Commands.argument("message",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c->channel(c.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"group"),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"message"))))));root.then(chat);e.getDispatcher().register(root);
 }
}
