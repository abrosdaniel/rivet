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
  if(System.getenv("RIVET_PRIVATE_WATCH_ADDRESS")!=null){watch();return;}
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
    if(System.getenv("RIVET_PRIVATE_TARGET")!=null&&mc.getConnection().getPlayerInfo(System.getenv("RIVET_PRIVATE_TARGET"))==null)return;
    var state=ServerMenuClient.state.deepCopy();boolean dedupe=SocialSettings.INSTANCE.dedupe;
    try{ServerMenuClient.state.addProperty("chatEnabled",true);SocialSettings.INSTANCE.dedupe=true;verifyTriggers();verify();}
    finally{ServerMenuClient.state=state;SocialSettings.INSTANCE.dedupe=dedupe;ClientChat.reset();}
    if(System.getenv("RIVET_CHAT_EMPTY_EXPECTED")!=null){mc.player.connection.sendChat(ChatHints.localTrigger()+"RIVET_LOCAL_ONE");mc.player.connection.sendChat(ChatHints.localTrigger()+"RIVET_LOCAL_TWO");}
    mc.player.connection.sendChat(ChatHints.globalTrigger()+"RIVET_PUBLIC_PROBE");
    mc.player.connection.sendCommand("tell "+mc.player.getGameProfile().getName()+" RIVET_PRIVATE_PROBE");
    mc.player.connection.sendCommand("w "+mc.player.getGameProfile().getName()+" RIVET_W_PROBE");
    mc.player.connection.sendCommand("msg "+mc.player.getGameProfile().getName()+" RIVET_MSG_PROBE");
    var chat=new net.minecraft.client.gui.screens.ChatScreen("");mc.setScreen(chat);chat.handleChatInput(ChatHints.personalTrigger()+mc.player.getGameProfile().getName()+": RIVET_SHORTCUT_PROBE",true);mc.setScreen(null);
    mc.player.connection.sendCommand("msg RivetMissingXYZ RIVET_OFFLINE_PRIVATE_PROBE");
    if(System.getenv("RIVET_PRIVATE_TARGET")!=null){
     String target=System.getenv("RIVET_PRIVATE_TARGET");
     for(String command:java.util.List.of("msg","w","tell"))mc.player.connection.sendCommand(command+" "+target+" RIVET_TARGET_ONLY_"+command);
     var direct=new net.minecraft.client.gui.screens.ChatScreen("");mc.setScreen(direct);direct.handleChatInput(ChatHints.personalTrigger()+target+": RIVET_TARGET_ONLY_shortcut",true);mc.setScreen(null);
    }
    mc.player.connection.sendCommand("me RIVET_EMOTE_PROBE");stage=1;sentAt=System.currentTimeMillis();return;
   }
   var rows=((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages();
   boolean arrived=rows.stream().anyMatch(row->row.content().getString().contains("RIVET_PUBLIC_PROBE"))&&rows.stream().anyMatch(row->personalMessage(row.content(),"RIVET_PRIVATE_PROBE"))&&rows.stream().anyMatch(row->personalMessage(row.content(),"RIVET_SHORTCUT_PROBE"))&&rows.stream().anyMatch(row->nativeMessage(row.content(),"RIVET_EMOTE_PROBE","chat.type.emote"));
   if(!arrived&&System.currentTimeMillis()-sentAt<15000)return;
   check(rows.stream().anyMatch(row->row.content().getString().contains("RIVET_PUBLIC_PROBE")),"Actual public packet lost");
   check(rows.stream().anyMatch(row->personalMessage(row.content(),"RIVET_PRIVATE_PROBE")),"Actual tell was not privately formatted");
   check(rows.stream().anyMatch(row->personalMessage(row.content(),"RIVET_SHORTCUT_PROBE")),"Actual UI shortcut did not reach native private delivery");
   check(rows.stream().anyMatch(row->nativeMessage(row.content(),"RIVET_EMOTE_PROBE","chat.type.emote")),"Actual /me lost its native type");
   for(String probe:java.util.List.of("RIVET_PRIVATE_PROBE","RIVET_W_PROBE","RIVET_MSG_PROBE","RIVET_SHORTCUT_PROBE"))check(rows.stream().filter(row->personalMessage(row.content(),probe)).count()==2,"Native self whisper must retain incoming/outgoing delivery: "+probe);
   if(System.getenv("RIVET_PRIVATE_TARGET")!=null)for(String command:java.util.List.of("msg","w","tell","shortcut"))check(rows.stream().filter(row->personalMessage(row.content(),"RIVET_TARGET_ONLY_"+command)).count()==1,"Outgoing private delivery missing: "+command);
   check(rows.stream().noneMatch(row->row.content().getString().contains("RIVET_OFFLINE_PRIVATE_PROBE")),"Unavailable target leaked into chat");
   String empty=System.getenv("RIVET_CHAT_EMPTY_EXPECTED");if(empty!=null)check(rows.stream().filter(row->row.content().getString().equals(empty)).count()==2,"Local warning must appear for each message with configured text");
   System.out.println("RIVET_CHAT_COMPAT_OK public + private + team + emote + say + foreign formatting + rich duplicate identity + actual tell/me commands");if(System.getenv("RIVET_TLS_CLOSE_CHECK")!=null){stage=2;mc.player.connection.sendCommand("rivet_test_tls_timeout");}else{stage=99;mc.stop();}
  }catch(Throwable failure){System.out.println("RIVET_CHAT_COMPAT_FAILED");failure.printStackTrace();stage=99;mc.stop();}
 }
 private static void watch(){
  if(stage==99)return;var mc=Minecraft.getInstance();String address=System.getenv("RIVET_PRIVATE_WATCH_ADDRESS");
  if(!connecting&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen){connecting=true;net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),new net.minecraft.client.multiplayer.ServerData("Private audience",address,net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);}
  if(mc.player==null||!SocialClient.available())return;
  if(stage==0){System.out.println("RIVET_PRIVATE_WATCH_READY");stage=1;}
  var rows=((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages();
  if(rows.stream().noneMatch(row->row.content().getString().contains("RIVET_EMOTE_PROBE")))return;
  if(sentAt==0){sentAt=System.currentTimeMillis();return;}if(System.currentTimeMillis()-sentAt<1000)return;
  try{
   boolean target=mc.player.getGameProfile().getName().equals("RivetWhisperTo");
   for(String command:java.util.List.of("msg","w","tell","shortcut")){
    String probe="RIVET_TARGET_ONLY_"+command;
    check(rows.stream().filter(row->row.content().getString().contains(probe)).count()==(target?1:0),"Private audience mismatch: "+command);
    if(target)check(rows.stream().anyMatch(row->personalMessage(row.content(),probe)),"Incoming private presentation missing: "+command);
   }
   for(String probe:java.util.List.of("RIVET_PRIVATE_PROBE","RIVET_W_PROBE","RIVET_MSG_PROBE","RIVET_SHORTCUT_PROBE","RIVET_OFFLINE_PRIVATE_PROBE"))check(rows.stream().noneMatch(row->row.content().getString().contains(probe)),"Third-party private message leaked");
   System.out.println("RIVET_PRIVATE_AUDIENCE_OK "+(target?"recipient":"observer"));
  }catch(Throwable failure){failure.printStackTrace();System.out.println("RIVET_PRIVATE_AUDIENCE_FAILED");}stage=99;mc.stop();
 }
 private static void verifyTriggers(){
  var mc=Minecraft.getInstance();ServerMenuClient.state.addProperty("chatGlobalTrigger","!!");ServerMenuClient.state.addProperty("chatGroupTrigger","@@");ServerMenuClient.state.addProperty("chatLocalTrigger","~");ServerMenuClient.state.addProperty("chatPersonalTrigger","%");
  check(ChatHints.lines("").contains(Client.text("chat.personalHint").replace("{trigger}","%")),"Private instruction visible before entering trigger");
  for(String command:java.util.List.of("/msg","/w","/tell"))check(ChatHints.lines(command+" RivetMapTest hello").isEmpty(),"Vanilla command has no Rivet hint "+command);
  check(ChatHints.parse("~hello").channel()==dev.abros.rivet.core.ChatChannels.Channel.LOCAL,"Custom local indicator");
  check(ChatHints.parse("!!hello").channel()==dev.abros.rivet.core.ChatChannels.Channel.GLOBAL,"Custom global indicator");
  check(ChatHints.lines("@@Town: hello").stream().anyMatch(line->line.startsWith("@@")),"Custom trigger hint");
  var suggestions=SocialChatControls.groupSuggestions("@@",2);check(suggestions!=null&&suggestions.getRange().getStart()==2,"Custom completion range");
  ServerMenuClient.state.addProperty("chatGlobalTrigger","");
  check(ChatHints.parse("hello").channel()==dev.abros.rivet.core.ChatChannels.Channel.GLOBAL,"Empty global trigger");
  check(ChatHints.parse("@@Town: hello").channel()==dev.abros.rivet.core.ChatChannels.Channel.GROUP,"Empty global stole group");
  ServerMenuClient.state.addProperty("chatGlobalTrigger","!!");ServerMenuClient.state.addProperty("chatGroupTrigger","");
  check(ChatHints.parse("Town: hello").channel()==dev.abros.rivet.core.ChatChannels.Channel.GROUP,"Empty group trigger");
  check(SocialChatControls.groupSuggestions("",0)!=null,"Empty group completion");
  ServerMenuClient.state.addProperty("chatGroupTrigger","@@");
  var chat=new net.minecraft.client.gui.screens.ChatScreen("@@Town: hello");mc.setScreen(chat);
  check(SocialChatControls.chooseChannel(chat),"Custom channel picker");String label=ChatChannelIndicator.label("!!hello");
  mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(label)).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow().onPress();
  check(((dev.abros.rivet.mixin.ChatInputAccessor)mc.screen).rivet$input().getValue().equals("!! hello"),"Picker lost custom trigger or body");mc.setScreen(null);
 }
 private static boolean personalMessage(Component message,String probe){return message.getString().contains(probe)&&message.getString().contains(" → ")&&message.getSiblings().stream().anyMatch(c->c.getStyle().getColor()!=null&&c.getStyle().getColor().getValue()==dev.abros.rivet.core.ChatChannels.color(dev.abros.rivet.core.ChatChannels.PERSONAL_COLOR));}
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
    var utils=Class.forName("umpaz.brewinandchewin.client.utility.BnCClientTextUtils");utils.getField("tipsyMessageLevel").setInt(null,4);utils.getField("randomSeed").setLong(null,12345L);utils.getField("generatedRandom").setBoolean(null,true);listener.invoke(null,real);
    check(ClientChat.formatted(real.getMessage()),"Actual B&C tipsy effect lost Rivet format");check(!real.getMessage().equals(sober),"Actual B&C effect was not applied");
    for(var privateType:java.util.List.of(ChatType.MSG_COMMAND_INCOMING,ChatType.MSG_COMMAND_OUTGOING)){
     var whisper=player(privateType,"Testing beautifully preserved private communication");var signed=whisper.getPlayerChatMessage();var bound=whisper.getBoundChatType();ClientChat.chat(whisper);var clean=whisper.getMessage();
     utils.getField("tipsyMessageLevel").setInt(null,0);listener.invoke(null,whisper);
     check(whisper.getMessage().equals(clean),"B&C changed sober private message");
     utils.getField("tipsyMessageLevel").setInt(null,4);utils.getField("randomSeed").setLong(null,12345L);utils.getField("generatedRandom").setBoolean(null,true);listener.invoke(null,whisper);
     check(!whisper.getMessage().equals(clean)&&whisper.getMessage().getString().contains(" → "),"Private effect or direction lost");
     check(whisper.getPlayerChatMessage()==signed&&whisper.getBoundChatType()==bound&&!whisper.isCanceled(),"Private signature/delivery changed");
    }
    for(String channel:java.util.List.of("Local","Group","API website")){
     var identity=dev.abros.rivet.network.ChatPresentation.identity("STAFF","Reader","VIP");
     var body=link("Testing beautifully preserved clickable navigation coordinates", "/rivet direction minecraft:overworld 1 2 64 Reader")
       .withStyle(style->style.withInsertion(new dev.abros.rivet.core.ChatEffectEnvelope(4,12345L).encode()));
     var line=dev.abros.rivet.network.ChatPresentation.format(dev.abros.rivet.core.ChatFormat.DEFAULT,dev.abros.rivet.core.ChatFormat.CHANNEL_DEFAULT,channel,mc.player.getUUID(),identity,body)
       .copy().withStyle(style->style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,new HoverEvent.EntityTooltipInfo(net.minecraft.world.entity.EntityType.PLAYER,mc.player.getUUID(),identity))));
     utils.getField("tipsyMessageLevel").setInt(null,2);long oldSeed=utils.getField("randomSeed").getLong(null);
     ClientChat.reset();var received=system(line).getMessage();
     check(received.getString().contains("Reader")&&received.getString().contains("STAFF")&&received.getString().contains("VIP")&&received.getString().contains(channel),"Channel or identity scrambled");
     check(!received.getString().contains(body.getString()),"Channel body effect missing");
     check(hasClick(received,"/rivet direction minecraft:overworld 1 2 64 Reader"),"Channel action lost");
     check(utils.getField("tipsyMessageLevel").getInt(null)==2&&utils.getField("randomSeed").getLong(null)==oldSeed,"Channel effect consumed signed chat state");
     check(dev.abros.rivet.compat.BrewinChannelEffects.apply(received).equals(received),"Channel effect applied twice");
     check(dev.abros.rivet.network.ChatPresentation.heads(line,false).getString().equals(line.getString()),"History text changed");
     var inherited=Component.empty().withStyle(style->style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/help")).withColor(0x83C6C4)
       .withInsertion(new dev.abros.rivet.core.ChatEffectEnvelope(4,12345L).encode())).append("Testing inherited clickable navigation");
     var transformed=dev.abros.rivet.compat.BrewinChannelEffects.apply(inherited);
     check(transformed.getStyle().getClickEvent().equals(inherited.getStyle().getClickEvent())&&transformed.getStyle().getColor().equals(inherited.getStyle().getColor()),"Empty body container lost inherited style");
    }
    utils.getField("tipsyMessageLevel").setInt(null,0);
    System.out.println("RIVET_BREWIN_ADAPTER_OK actual 4.5.0 sober/tipsy + private incoming/outgoing + channel envelopes");
   }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
  }
  var normal=player(ChatType.CHAT);var before=normal.getMessage();ClientChat.chat(normal);check(!normal.getMessage().equals(before),"Public chat not formatted");
  for(var type:java.util.List.of(ChatType.TEAM_MSG_COMMAND_INCOMING,ChatType.TEAM_MSG_COMMAND_OUTGOING,ChatType.EMOTE_COMMAND,ChatType.SAY_COMMAND)){
   var event=player(type);var original=event.getMessage();ClientChat.chat(event);check(event.getMessage().equals(original)&&!event.isCanceled(),"Command type modified: "+type);
  }
  for(var type:java.util.List.of(ChatType.MSG_COMMAND_INCOMING,ChatType.MSG_COMMAND_OUTGOING)){
   var event=player(type);var signed=event.getPlayerChatMessage();var bound=event.getBoundChatType();ClientChat.chat(event);
   check(personalMessage(event.getMessage(),"Test body")&&!event.isCanceled()&&event.getPlayerChatMessage()==signed&&event.getBoundChatType()==bound,"Private formatting changed delivery/signature");
   var clean=event.getMessage();
   dev.abros.rivet.compat.BrewinChatAdapter.receive(event,nativeEvent->{
    var envelope=(net.minecraft.network.chat.contents.TranslatableContents)nativeEvent.getMessage().getContents();
    check(envelope.getArgs()[1] instanceof Component,"Private envelope body missing");
   });check(event.getMessage().equals(clean),"Sober private adapter changed message");
   dev.abros.rivet.compat.BrewinChatAdapter.receive(event,nativeEvent->nativeEvent.setMessage(Component.literal("prefix").append(link("Private effect","/help"))));
   check(personalMessage(event.getMessage(),"Private effect")&&hasClick(event.getMessage(),"/help"),"Private effect lost colour/direction/action");
   check(event.getPlayerChatMessage()==signed&&event.getBoundChatType()==bound,"Private adapter changed signed message/type");
   var canceledPrivate=player(type);ClientChat.chat(canceledPrivate);dev.abros.rivet.compat.BrewinChatAdapter.receive(canceledPrivate,nativeEvent->nativeEvent.setCanceled(true));check(canceledPrivate.isCanceled(),"Private cancellation lost");
   var foreignPrivate=player(type);foreignPrivate.setMessage(link("Foreign private","/help"));var beforePrivate=foreignPrivate.getMessage();ClientChat.chat(foreignPrivate);check(foreignPrivate.getMessage().equals(beforePrivate),"Foreign private formatting replaced");
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
 private static ClientChatReceivedEvent.Player player(ResourceKey<ChatType> type){return player(type,"Test body");}
 private static ClientChatReceivedEvent.Player player(ResourceKey<ChatType> type,String text){var mc=Minecraft.getInstance();var bound=ChatType.bind(type,mc.level.registryAccess(),Component.literal("Sender")).withTargetName(Component.literal("Recipient"));var message=PlayerChatMessage.unsigned(mc.player.getUUID(),text);return new ClientChatReceivedEvent.Player(bound,bound.decorate(message.decoratedContent()),message,mc.player.getUUID());}
 private static MutableComponent link(String text,String command){return Component.literal(text).withStyle(style->style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,command)));}
 private static ClientChatReceivedEvent.System system(Component message){var event=new ClientChatReceivedEvent.System(message,false);ClientChat.system(event);if(!event.isCanceled())Minecraft.getInstance().gui.getChat().addMessage(event.getMessage());return event;}
 private static boolean hasClick(Component value,String command){if(value.getStyle().getClickEvent()!=null&&value.getStyle().getClickEvent().getValue().equals(command))return true;for(var child:value.getSiblings())if(hasClick(child,command))return true;return false;}
 private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
