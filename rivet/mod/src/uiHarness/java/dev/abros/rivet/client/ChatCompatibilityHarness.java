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
 private static boolean connecting,submitted;
 private static int stage;private static long sentAt;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post ignored){
  if((System.getenv("RIVET_CHAT_COMPAT_CHECK")==null&&System.getenv("RIVET_CHAT_COMPAT_ADDRESS")==null)||stage==99)return;
  var mc=Minecraft.getInstance();String address=System.getenv("RIVET_CHAT_COMPAT_ADDRESS");
  if(address!=null&&!connecting&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen){connecting=true;net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),new net.minecraft.client.multiplayer.ServerData("Chat compatibility",address,net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);}
  if(mc.screen instanceof AuthScreen auth&&!submitted){try{for(var child:auth.children())if(child instanceof net.minecraft.client.gui.components.EditBox box)box.setValue("isolated chat TLS test password");var field=AuthScreen.class.getDeclaredField("submit");field.setAccessible(true);((net.minecraft.client.gui.components.Button)field.get(auth)).onPress();submitted=true;}catch(Exception failure){throw new IllegalStateException(failure);}return;}
  if(stage==2&&mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen){stage=3;submitted=false;net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),new net.minecraft.client.multiplayer.ServerData("TLS reconnect",address,net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);return;}
  if(mc.player==null||!SocialClient.available()||!ServerMenuClient.state.has("chatFormat"))return;
  if(stage==3){System.out.println("RIVET_TLS_CLOSE_OK timeout then authenticated reconnect");stage=99;mc.stop();return;}
  if(stage==2)return;
  try{
   if(stage==0){
    var state=ServerMenuClient.state.deepCopy();boolean dedupe=SocialSettings.INSTANCE.dedupe;
    try{ServerMenuClient.state.addProperty("chatEnabled",true);SocialSettings.INSTANCE.dedupe=true;verify();}
    finally{ServerMenuClient.state=state;SocialSettings.INSTANCE.dedupe=dedupe;ClientChat.reset();}
    mc.player.connection.sendChat("!RIVET_PUBLIC_PROBE");
    mc.player.connection.sendCommand("tell "+mc.player.getGameProfile().getName()+" RIVET_PRIVATE_PROBE");
    mc.player.connection.sendCommand("me RIVET_EMOTE_PROBE");stage=1;sentAt=System.currentTimeMillis();return;
   }
   var rows=((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages();
   boolean arrived=rows.stream().anyMatch(row->row.content().getString().contains("RIVET_PUBLIC_PROBE"))&&rows.stream().anyMatch(row->nativeMessage(row.content(),"RIVET_PRIVATE_PROBE","commands.message.display.incoming"))&&rows.stream().anyMatch(row->nativeMessage(row.content(),"RIVET_PRIVATE_PROBE","commands.message.display.outgoing"))&&rows.stream().anyMatch(row->nativeMessage(row.content(),"RIVET_EMOTE_PROBE","chat.type.emote"));
   if(!arrived&&System.currentTimeMillis()-sentAt<15000)return;
   check(rows.stream().anyMatch(row->row.content().getString().contains("RIVET_PUBLIC_PROBE")),"Actual public packet lost");
   check(rows.stream().anyMatch(row->nativeMessage(row.content(),"RIVET_PRIVATE_PROBE","commands.message.display.incoming")),"Actual incoming whisper lost its native type");
   check(rows.stream().anyMatch(row->nativeMessage(row.content(),"RIVET_PRIVATE_PROBE","commands.message.display.outgoing")),"Actual outgoing whisper lost its native type");
   check(rows.stream().anyMatch(row->nativeMessage(row.content(),"RIVET_EMOTE_PROBE","chat.type.emote")),"Actual /me lost its native type");
   System.out.println("RIVET_CHAT_COMPAT_OK public + private + team + emote + say + foreign formatting + rich duplicate identity + actual tell/me commands");if(System.getenv("RIVET_TLS_CLOSE_CHECK")!=null){stage=2;mc.player.connection.sendCommand("rivet_test_tls_timeout");}else{stage=99;mc.stop();}
  }catch(Throwable failure){System.out.println("RIVET_CHAT_COMPAT_FAILED");failure.printStackTrace();stage=99;mc.stop();}
 }
 private static boolean nativeMessage(Component message,String probe,String key){return message.getString().contains(probe)&&message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated&&translated.getKey().equals(key);}
 private static void verify(){
  var mc=Minecraft.getInstance();var access=(dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat();
  int oldLimit=SocialSettings.INSTANCE.history;SocialSettings.INSTANCE.history=1000;
  mc.gui.getChat().clearMessages(false);
  for(int n=0;n<1100;n++)mc.gui.getChat().addMessage(Component.literal("History "+n));
  check(access.rivet$messages().size()==1000,"Extended chat limit not applied");
  check(access.rivet$messages().getLast().content().getString().equals("History 100"),"Wrong oldest retained message");
  SocialSettings.INSTANCE.history=100;ClientChat.applyHistoryLimit();
  check(access.rivet$messages().size()==100&&access.rivet$messages().getLast().content().getString().equals("History 1000"),"Lower limit did not trim oldest messages immediately");
  mc.gui.getChat().clearMessages(false);mc.gui.getChat().addMessage(Component.literal("Live message"));ClientChat.reset();
  var packet=new com.google.gson.JsonObject();packet.addProperty("kind","chatHistory");packet.addProperty("id",1);packet.addProperty("at",0);packet.add("message",com.google.gson.JsonParser.parseString(Component.Serializer.toJson(Component.literal("Historical message"),mc.level.registryAccess())));
  ClientChat.receive(packet);ClientChat.receive(packet);
  check(access.rivet$messages().size()==2&&access.rivet$messages().getFirst().content().getString().equals("Live message")&&access.rivet$messages().getLast().signature()==null,"Replay duplicated, reordered live chat or forged signature");
  SocialSettings.INSTANCE.history=oldLimit;ClientChat.reset();

  var adapted=player(ChatType.CHAT);ClientChat.chat(adapted);var formatted=adapted.getMessage();
  dev.abros.rivet.compat.BrewinChatAdapter.receive(adapted,event->{
   var envelope=(net.minecraft.network.chat.contents.TranslatableContents)event.getMessage().getContents();
   check(envelope.getArgs()[1] instanceof Component,"Adapter did not restore vanilla body");
  });check(adapted.getMessage().equals(formatted),"Sober chat changed by adapter");
  dev.abros.rivet.compat.BrewinChatAdapter.receive(adapted,event->event.setMessage(Component.literal("prefix").append(link("Tipsy body","/help"))));
  check(adapted.getMessage().getString().contains("Tipsy body"),"Tipsy effect lost");check(ClientChat.formatted(adapted.getMessage()),"Rivet formatting lost");
  if(net.neoforged.fml.ModList.get().isLoaded("brewinandchewin")){
   try{
    var type=Class.forName("umpaz.brewinandchewin.neoforge.client.TipsyEffects");var listener=type.getMethod("iCanHear",ClientChatReceivedEvent.Player.class);
    var real=player(ChatType.CHAT);ClientChat.chat(real);var sober=real.getMessage();listener.invoke(null,real);check(real.getMessage().equals(sober),"Actual B&C sober adapter changed message");
    var utils=Class.forName("umpaz.brewinandchewin.client.utility.BnCClientTextUtils");utils.getField("tipsyMessageLevel").setInt(null,4);utils.getField("randomSeed").setLong(null,12345L);listener.invoke(null,real);
    check(ClientChat.formatted(real.getMessage()),"Actual B&C tipsy effect lost Rivet format");check(!real.getMessage().equals(sober),"Actual B&C effect was not applied");
    System.out.println("RIVET_BREWIN_ADAPTER_OK actual 4.5.0 sober/tipsy");
   }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
  }
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
