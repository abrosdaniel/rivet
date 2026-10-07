package dev.abros.rivet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceKey;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;

/** Checks chat policies on native components and the actual command receive path. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class ChatCompatibilityHarness {
 private static int stage;private static long sentAt;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post ignored){
  if(System.getenv("RIVET_CHAT_COMPAT_CHECK")==null||stage==2)return;
  var mc=Minecraft.getInstance();if(mc.player==null||!SocialClient.available()||!ServerMenuClient.state.has("chatFormat"))return;
  try{
   if(stage==0){
    var state=ServerMenuClient.state.deepCopy();boolean dedupe=SocialSettings.INSTANCE.dedupe;
    try{ServerMenuClient.state.addProperty("chatEnabled",true);SocialSettings.INSTANCE.dedupe=true;verify();}
    finally{ServerMenuClient.state=state;SocialSettings.INSTANCE.dedupe=dedupe;ClientChat.reset();}
    mc.player.connection.sendCommand("tell "+mc.player.getGameProfile().getName()+" RIVET_PRIVATE_PROBE");
    mc.player.connection.sendCommand("me RIVET_EMOTE_PROBE");stage=1;sentAt=System.currentTimeMillis();return;
   }
   if(System.currentTimeMillis()-sentAt<2500)return;
   var rows=((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages();
   check(rows.stream().anyMatch(row->nativeMessage(row.content(),"RIVET_PRIVATE_PROBE","commands.message.display.incoming")),"Actual incoming whisper lost its native type");
   check(rows.stream().anyMatch(row->nativeMessage(row.content(),"RIVET_PRIVATE_PROBE","commands.message.display.outgoing")),"Actual outgoing whisper lost its native type");
   check(rows.stream().anyMatch(row->nativeMessage(row.content(),"RIVET_EMOTE_PROBE","chat.type.emote")),"Actual /me lost its native type");
   System.out.println("RIVET_CHAT_COMPAT_OK public + private + team + emote + say + foreign formatting + rich duplicate identity + actual tell/me commands");stage=2;mc.stop();
  }catch(Throwable failure){System.out.println("RIVET_CHAT_COMPAT_FAILED");failure.printStackTrace();stage=2;mc.stop();}
 }
 private static boolean nativeMessage(Component message,String probe,String key){return message.getString().contains(probe)&&message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated&&translated.getKey().equals(key);}
 private static void verify(){
  var normal=player(ChatType.CHAT);var before=normal.getMessage();ClientChat.chat(normal);check(!normal.getMessage().equals(before),"Public chat not formatted");
  for(var type:java.util.List.of(ChatType.MSG_COMMAND_INCOMING,ChatType.MSG_COMMAND_OUTGOING,ChatType.TEAM_MSG_COMMAND_INCOMING,ChatType.TEAM_MSG_COMMAND_OUTGOING,ChatType.EMOTE_COMMAND,ChatType.SAY_COMMAND)){
   var event=player(type);var original=event.getMessage();ClientChat.chat(event);check(event.getMessage().equals(original)&&!event.isCanceled(),"Command type modified: "+type);
  }
  var replacement=player(ChatType.CHAT);replacement.setMessage(link("Foreign message","/help").withStyle(style->style.withColor(0x123456)));var foreign=replacement.getMessage();ClientChat.chat(replacement);check(replacement.getMessage().equals(foreign),"Foreign replacement lost");
  var styled=player(ChatType.CHAT);styled.setMessage(styled.getMessage().copy().withStyle(style->style.withColor(0x123456)));foreign=styled.getMessage();ClientChat.chat(styled);check(styled.getMessage().equals(foreign),"Foreign style lost");
  var canceled=player(ChatType.CHAT);canceled.setCanceled(true);before=canceled.getMessage();ClientChat.chat(canceled);check(canceled.isCanceled()&&canceled.getMessage().equals(before),"Canceled chat changed");
  ServerMenuClient.state.addProperty("chatEnabled",false);var disabled=player(ChatType.CHAT);before=disabled.getMessage();ClientChat.chat(disabled);check(disabled.getMessage().equals(before),"Disabled chat changed");ServerMenuClient.state.addProperty("chatEnabled",true);
  ClientChat.reset();system(link("Same text","/help"));var changed=system(link("Same text","/list"));check(!changed.isCanceled(),"Different click actions merged");
  ClientChat.reset();system(Component.literal("Tooltip").withStyle(style->style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal("A")))));changed=system(Component.literal("Tooltip").withStyle(style->style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,Component.literal("B")))));check(!changed.isCanceled(),"Different tooltips merged");
  ClientChat.reset();system(Component.literal("Colour").withStyle(style->style.withColor(0x123456)));changed=system(Component.literal("Colour").withStyle(style->style.withColor(0x654321)));check(!changed.isCanceled(),"Different colors merged");
  ClientChat.reset();var same=link("Repeated","/help");system(same);check(system(same.copy()).isCanceled(),"Equal rich messages not merged");check(system(same.copy()).isCanceled(),"Third repeat not merged");var latest=((dev.abros.rivet.mixin.ChatHistoryAccessor)Minecraft.getInstance().gui.getChat()).rivet$messages().getFirst().content();check(latest.getString().equals("Repeated ×3")&&latest.getStyle().getClickEvent().getValue().equals("/help"),"Repeat count/action lost");
  ClientChat.reset();system(same);Minecraft.getInstance().gui.getChat().addMessage(Component.literal("Intervening message"));check(!system(same).isCanceled(),"Removed message across intervening history");
  var custom=Component.literal("Other mod player hover").withStyle(style->style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,new HoverEvent.EntityTooltipInfo(net.minecraft.world.entity.EntityType.PLAYER,Minecraft.getInstance().player.getUUID(),Component.literal("Other mod")))));check(system(custom).getMessage().equals(custom),"Foreign player hover lost");
 }
 private static ClientChatReceivedEvent.Player player(ResourceKey<ChatType> type){var mc=Minecraft.getInstance();var bound=ChatType.bind(type,mc.level.registryAccess(),Component.literal("Sender")).withTargetName(Component.literal("Recipient"));var message=PlayerChatMessage.unsigned(mc.player.getUUID(),"Test body");return new ClientChatReceivedEvent.Player(bound,bound.decorate(message.decoratedContent()),message,mc.player.getUUID());}
 private static MutableComponent link(String text,String command){return Component.literal(text).withStyle(style->style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,command)));}
 private static ClientChatReceivedEvent.System system(Component message){var event=new ClientChatReceivedEvent.System(message,false);ClientChat.system(event);if(!event.isCanceled())Minecraft.getInstance().gui.getChat().addMessage(event.getMessage());return event;}
 private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
