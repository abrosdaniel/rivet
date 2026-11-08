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
 static void start(){ServerChatHistory.start();}
 static void stop(){markerShares.clear();ServerChatHistory.stop();}
 static void install(){
  NeoForge.EVENT_BUS.addListener(ServerChat::chat);
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,ServerChat::rememberGlobal);
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof ServerPlayer p)ServerChatHistory.joined(p);});
  NeoForge.EVENT_BUS.addListener(ServerChat::commands);
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e)->{markerShares.remove(e.getEntity().getUUID());if(e.getEntity() instanceof ServerPlayer p)ServerChatHistory.left(p);});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->ServerChatHistory.tick(e.getServer()));
 }
 private static void rememberGlobal(net.neoforged.neoforge.event.ServerChatEvent e){
  if(e.isCanceled()||!AuthServer.authenticated(e.getPlayer())||!ServerDatabase.settings().flag("chat.enabled"))return;
  var parsed=dev.abros.rivet.core.ChatChannels.parse(e.getRawText(),ServerDatabase.settings().flag("chat.local"),ServerDatabase.settings().text("chat.globalTrigger"),ServerDatabase.settings().text("chat.groupTrigger"),ServerDatabase.settings().text("chat.localTrigger"),ServerDatabase.settings().text("chat.personalTrigger"));
  if(parsed.channel()==dev.abros.rivet.core.ChatChannels.Channel.GLOBAL)ServerChatHistory.remember(e.getPlayer(),"global",formatted(e.getPlayer(),ServerDatabase.settings().text("chat.globalName"),e.getMessage(),"globalColor"),List.of());
 }
 private static Component name(ServerPlayer p){var lp=ServerSocial.metadata(p);return dev.abros.rivet.network.ChatPresentation.identity(Json.opt(lp,"prefix",""),p.getGameProfile().getName(),Json.opt(lp,"suffix",""));}
 private static Component formatted(ServerPlayer p,String label,Component content,String colorKey){var settings=ServerDatabase.settings();return dev.abros.rivet.network.ChatPresentation.format(settings.text("chat.format"),settings.text("chat.channelFormat"),label,p.getUUID(),name(p),content,dev.abros.rivet.core.ChatChannels.color(settings.text("chat."+colorKey)));}
 private static Component sender(Component message,ServerPlayer p){return message.copy().withStyle(style->style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,new HoverEvent.EntityTooltipInfo(net.minecraft.world.entity.EntityType.PLAYER,p.getUUID(),name(p)))));}
 private static void active(ServerPlayer p){ServerSocial.active(p);}
 private static void chat(net.neoforged.neoforge.event.ServerChatEvent e){try(var locale=dev.abros.rivet.core.Messages.locale(e.getPlayer().clientInformation().language())){chatLocalized(e);}}
 private static void chatLocalized(net.neoforged.neoforge.event.ServerChatEvent e){
  var player=e.getPlayer();if(!AuthServer.authenticated(player)||e.isCanceled()||!ServerDatabase.settings().flag("chat.enabled"))return;active(player);
  var parsed=dev.abros.rivet.core.ChatChannels.parse(e.getRawText(),ServerDatabase.settings().flag("chat.local"),ServerDatabase.settings().text("chat.globalTrigger"),ServerDatabase.settings().text("chat.groupTrigger"),ServerDatabase.settings().text("chat.localTrigger"),ServerDatabase.settings().text("chat.personalTrigger"));
  if(parsed.text().isBlank()){e.setCanceled(true);return;}
  if(parsed.channel()==dev.abros.rivet.core.ChatChannels.Channel.PERSONAL){e.setCanceled(true);player.sendSystemMessage(Component.translatable("rivet.chat.personalUseCommand"));return;}
  if(parsed.channel()==dev.abros.rivet.core.ChatChannels.Channel.LOCAL){e.setCanceled(true);channel(player,"local",parsed.text());return;}
  if(parsed.channel()==dev.abros.rivet.core.ChatChannels.Channel.GROUP){e.setCanceled(true);groupChat(player,parsed);return;}
  // Keep Minecraft's signed broadcast for the global channel.
  if(e.getMessage().getString().equals(e.getRawText()))e.setMessage(sender(content(player,parsed.text()),player));
 }
 private static void groupChat(ServerPlayer player,dev.abros.rivet.core.ChatChannels.Message message){
  if((!ServerDatabase.settings().flag("chat.group")||!ServerDatabase.settings().modules().enabled("groups"))){player.sendSystemMessage(Component.translatable("rivet.core.group_chat_is_disabled_on_the_417a8155"));return;}
  var store=ServerFeatures.communityStore();if(store==null)return;Object epoch=ServerSocial.generation();
  try{ServerFeatures.storage(()->{try{var own=store.playerGroups(player.getUUID().toString());var matches=own.stream().filter(row->message.group().isBlank()||Json.str(row,"title").equalsIgnoreCase(message.group())||Json.str(row,"id").equals(message.group())).toList();
   player.server.execute(dev.abros.rivet.core.Messages.capture(()->{if(epoch!=ServerSocial.generation()||player.server.getPlayerList().getPlayer(player.getUUID())!=player||!AuthServer.authenticated(player))return;
    if(matches.size()==1)channel(player,Json.str(matches.getFirst(),"id"),message.text());
    else player.sendSystemMessage(Component.literal(matches.isEmpty()?dev.abros.rivet.core.Messages.text("rivet.core.group_not_found_or_you_are_8e017395"):dev.abros.rivet.core.Messages.text("rivet.core.specify_a_group_name_message_available_8d80dedb")+own.stream().map(row->Json.str(row,"title")).collect(java.util.stream.Collectors.joining(", "))));
   }));}catch(Exception failure){player.server.execute(dev.abros.rivet.core.Messages.capture(()->{if(epoch==ServerSocial.generation()&&player.server.getPlayerList().getPlayer(player.getUUID())==player&&AuthServer.authenticated(player))player.sendSystemMessage(Component.translatable("rivet.core.could_not_verify_group_membership_send_07d2d28f"));}));}});
  }catch(java.util.concurrent.RejectedExecutionException full){player.sendSystemMessage(Component.translatable("rivet.core.chat_busy_send_your_message_again_36311b98"));}
 }
 private static Component content(ServerPlayer p,String message){var out=Component.empty();String[] parts=message.split("(?<=\\[pos\\])|(?=\\[pos\\])");for(String part:parts){if(part.equals("[pos]")&&ServerDatabase.settings().flag("chat.coordinates")){var place=Component.literal("[").append(dev.abros.rivet.network.DimensionLabels.name(p.level().dimension().location().toString())).append(" · "+p.getBlockX()+", "+p.getBlockY()+", "+p.getBlockZ()+"]");out.append(place.withStyle(s->s.withColor(0x83C6C4).withUnderlined(true).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.translatable("rivet.ui.location_from_7537c568").append(p.getGameProfile().getName()).append(Component.translatable("rivet.ui.click_to_start_navigation_4640992c")))).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/rivet direction "+p.level().dimension().location()+" "+p.getBlockX()+" "+p.getBlockZ()+" "+p.getBlockY()+" "+p.getGameProfile().getName()))));}else out.append(Component.literal(part));}return out;}
 private static boolean localRecipient(ServerPlayer sender,ServerPlayer recipient,int radius){return AuthServer.authenticated(recipient)&&recipient.level()==sender.level()&&recipient.distanceToSqr(sender)<=radius*(double)radius;}
 private static int channel(ServerPlayer p,String channel,String message){try(var locale=dev.abros.rivet.core.Messages.locale(p.clientInformation().language())){return channelLocalized(p,channel,message);}}
 private static int channelLocalized(ServerPlayer p,String channel,String message){if(!ServerDatabase.settings().flag("chat.enabled")||!AuthServer.authenticated(p)||message.isBlank()||message.length()>256)return 0;active(p);var settings=ServerDatabase.settings();
  if(channel.equals("local")){if(!settings.flag("chat.local"))return 0;int radius=settings.number("chat.localRadius");var audience=p.server.getPlayerList().getPlayers().stream().filter(v->localRecipient(p,v,radius)).toList();deliver(p,settings.text("chat.localName"),message,audience);boolean alone=audience.stream().noneMatch(recipient->recipient!=p);if(alone)p.sendSystemMessage(Component.literal(settings.text("chat.localEmptyMessage").replace("{globalTrigger}",settings.text("chat.globalTrigger"))).withStyle(style->style.withColor(0xE2BE75)));return audience.size();}
  if(!settings.flag("chat.group"))return 0;try{UUID.fromString(channel);}catch(IllegalArgumentException bad){return 0;}var store=ServerFeatures.communityStore();if(store==null)return 0;Object epoch=ServerSocial.generation();var body=liveBody(p,content(p,message));
  try{ServerFeatures.storage(()->{try{store.deliverGroupChat(channel,p.getUUID().toString(),(label,members)->dev.abros.rivet.core.BoundedDelivery.run(p.server,dev.abros.rivet.core.Messages.capture(()->{if(epoch!=ServerSocial.generation()||store!=ServerFeatures.communityStore()||!ServerDatabase.settings().flag("chat.enabled")||!ServerDatabase.settings().flag("chat.group")||!ServerDatabase.settings().modules().enabled("groups")||p.server.getPlayerList().getPlayer(p.getUUID())!=p||!AuthServer.authenticated(p))return;var audience=p.server.getPlayerList().getPlayers().stream().filter(v->AuthServer.authenticated(v)&&members.contains(v.getUUID().toString())).toList();var formatted=formatted(p,label,body,"groupColor");for(var recipient:audience)recipient.sendSystemMessage(sender(formatted,p));ServerChatHistory.remember(p,channel,formatted,audience);com.mojang.logging.LogUtils.getLogger().info("[Rivet group] {}: {}",p.getGameProfile().getName(),message);}),3000));}catch(Exception denied){p.server.execute(dev.abros.rivet.core.Messages.capture(()->{if(epoch==ServerSocial.generation()&&p.server.getPlayerList().getPlayer(p.getUUID())==p&&AuthServer.authenticated(p))p.sendSystemMessage(Component.translatable("rivet.core.message_not_sent_check_your_group_b09287a8"));}));}});}catch(java.util.concurrent.RejectedExecutionException full){p.sendSystemMessage(Component.translatable("rivet.core.chat_busy_send_your_message_again_36311b98"));return 0;}return 1;
 }
 static void deliverApi(net.minecraft.server.MinecraftServer server,dev.abros.rivet.core.api.ApiChat.Envelope message){
  Component line;
  if(message.body().isEmpty())line=Component.literal(message.text()); // Persisted pre-envelope deliveries.
  else {
   UUID id=UUID.fromString(message.sender());var player=server.getPlayerList().getPlayer(id);
   var identity=player==null?dev.abros.rivet.network.ChatPresentation.identity("",message.author(),""):name(player);
   Component body=Component.literal(message.body());
   body=player==null?body.copy().withStyle(style->style.withInsertion(new dev.abros.rivet.core.ChatEffectEnvelope(0,server.overworld().random.nextLong()).encode())):liveBody(player,body);
   var settings=ServerDatabase.settings();String label=message.label()+" · API "+message.application();
   String color=message.channel().equals("global")?"globalColor":message.channel().equals("local")?"localColor":"groupColor";
   line=dev.abros.rivet.network.ChatPresentation.format(settings.text("chat.format"),settings.text("chat.channelFormat"),label,id,identity,body,dev.abros.rivet.core.ChatChannels.color(settings.text("chat."+color)))
    .copy().withStyle(style->style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,new HoverEvent.EntityTooltipInfo(net.minecraft.world.entity.EntityType.PLAYER,id,identity))));
  }
  for(var recipient:server.getPlayerList().getPlayers())if(AuthServer.authenticated(recipient)&&(message.channel().equals("global")||message.recipients().contains(recipient.getUUID().toString())))recipient.sendSystemMessage(line);
 }
 private static Component liveBody(ServerPlayer sender,Component body){
  var effect=net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("brewinandchewin","tipsy"));
  int level=effect.map(sender::getEffect).map(net.minecraft.world.effect.MobEffectInstance::getAmplifier).orElse(0);
  var envelope=new dev.abros.rivet.core.ChatEffectEnvelope(Math.max(0,Math.min(255,level)),sender.getRandom().nextLong());
  return body.copy().withStyle(style->style.withInsertion(envelope.encode()));
 }
 private static void deliver(ServerPlayer p,String label,String message,List<ServerPlayer> audience){var line=formatted(p,label,liveBody(p,content(p,message)),"localColor");for(var recipient:audience)recipient.sendSystemMessage(sender(line,p));ServerChatHistory.remember(p,"local",line,audience);com.mojang.logging.LogUtils.getLogger().info("[Rivet {}] {}: {}",label,p.getGameProfile().getName(),message);}
 private static final Map<UUID,Long> markerShares=new HashMap<>();
 static int shareMarker(ServerPlayer player,dev.abros.rivet.core.map.MapMarker marker){
  if(!ServerDatabase.settings().flag("chat.enabled"))return 0;
  long now=System.currentTimeMillis();if(now-markerShares.getOrDefault(player.getUUID(),0L)<2000)return 0;
  markerShares.put(player.getUUID(),now);
  var body=Component.literal("["+marker.name()+"]").withStyle(style->style.withColor(0x83C6C4).withUnderlined(true)
   .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.translatable("rivet.map.addShared")))
   .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/rivet addMarker "+dev.abros.rivet.core.map.MapMarkerLink.encode(marker))));
  var line=formatted(player,ServerDatabase.settings().text("chat.globalName"),liveBody(player,body),"globalColor");
  for(var recipient:player.server.getPlayerList().getPlayers())if(AuthServer.authenticated(recipient))recipient.sendSystemMessage(sender(line,player));
  ServerChatHistory.remember(player,"global",line,List.of());return 1;
 }
 private static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e){var root=Commands.literal("rivet");
  var chat=Commands.literal("chat").requires(source->ServerDatabase.settings().flag("chat.enabled"));chat.then(Commands.literal("local").then(Commands.argument("message",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c->channel(c.getSource().getPlayerOrException(),"local",com.mojang.brigadier.arguments.StringArgumentType.getString(c,"message")))));chat.then(Commands.literal("group").then(Commands.argument("group",com.mojang.brigadier.arguments.StringArgumentType.word()).suggests((c,b)->{var p=c.getSource().getPlayer();if(p!=null){ServerSocial.suggestGroups(p,b);}return b.buildFuture();}).then(Commands.argument("message",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c->channel(c.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"group"),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"message"))))));root.then(chat);e.getDispatcher().register(root);
 }
}
