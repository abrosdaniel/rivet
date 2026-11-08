package dev.abros.rivet.client;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class RivetChatIdentityHarness {
 static boolean complete(){return stage==10;}
 private static boolean connecting;private static int stage;private static long next;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(System.getenv("RIVET_CHAT_IDENTITY_REVIEW")==null||stage==10)return;var mc=Minecraft.getInstance();long now=System.currentTimeMillis();
  if(!connecting&&mc.screen instanceof TitleScreen){connecting=true;mc.options.pauseOnLostFocus=false;var server=new net.minecraft.client.multiplayer.ServerData("Rivet test","127.0.0.1:25569",net.minecraft.client.multiplayer.ServerData.Type.OTHER);ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(server.ip),server,false,null);}
  if(mc.player==null||!SocialClient.available()||now<next)return;
  if(stage==0){mc.options.guiScale().set(3);mc.resizeDisplay();mc.getToasts().clear();mc.setScreen(new ChatScreen(""));stage=1;next=now+2500;return;}
  if(stage==1){ServerMenuClient.request("state");var request=new JsonObject();request.addProperty("action","community");request.addProperty("section","groups");request.addProperty("op","create");request.addProperty("title","RivetChatReview");request.addProperty("description","Local chat presentation review");request.addProperty("type","Команда");if(!RivetHud.snapshot.toString().contains("RivetChatReview"))ServerMenuClient.request(request);stage=2;next=now+2500;return;}
  if(stage==2){mc.player.connection.sendChat("Проверка локального канала [item] [pos]");stage=3;next=now+1300;return;}
  if(stage==3){mc.player.connection.sendChat("Проверка повторного локального сообщения");stage=13;next=now+1300;return;}
  if(stage==13){SocialClient.players.clear();mc.player.connection.sendChat("!Проверка общего канала [pos]");stage=4;next=now+1300;return;}
  if(stage==4){mc.player.connection.sendChat("#RivetChatReview: Проверка канала объединения [pos]");stage=5;next=now+2500;return;}
  if(stage==5){var messages=((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages();for(String label:java.util.List.of("локального","общего","объединения")){var message=messages.stream().filter(m->m.content().getString().contains("Проверка ")&&m.content().getString().contains(label)).findFirst().orElseThrow(()->new IllegalStateException("Missing channel: "+label));if(dev.abros.rivet.core.Json.opt(ServerMenuClient.state,"chatFormat","").startsWith("<"))check(message.content().getString().startsWith("<")&&message.content().getString().contains(">: "),"Server template not applied: "+label);check(message.content().getString().contains("[ENGINEER] Dev [TEST]"),"Missing LP metadata: "+label+message.content().getString());var lines=mc.font.split(message.content(),160);check(mc.player.getUUID().equals(SocialClient.chatHead(lines.getFirst())),"Missing head: "+label);for(int n=1;n<lines.size();n++)check(SocialClient.chatHead(lines.get(n))==null,"Duplicate head on wrapped line");boolean heads=SocialSettings.INSTANCE.heads;SocialSettings.INSTANCE.heads=false;check(SocialClient.chatHead(lines.getFirst())==null,"Heads toggle ignored");SocialSettings.INSTANCE.heads=heads;}
   check(messages.stream().filter(m->m.content().getString().contains("Рядом нет игроков.")).count()==1&&messages.stream().noneMatch(m->m.content().getString().contains("Рядом нет игроков.")&&m.content().getString().contains("×")),"Empty audience warning repeated or missing");check(HudProfile.fullText().contains("[ENGINEER]")&&HudProfile.fullText().contains("[TEST]"),"HUD metadata absent");capture("all-chat-channels");mc.options.keyPlayerList.setDown(true);stage=6;next=now+1600;return;}
  if(stage==6){verifyPlayerText();verifyChatFormats();verifyHiddenChat();verifyMentions();verifyChatIndicator();verifyNameplates();var profile=SocialClient.players.get(mc.player.getUUID());check(profile!=null&&profile.get("prefix").getAsString().contains("[ENGINEER]")&&profile.get("suffix").getAsString().contains("[TEST]"),"TAB metadata absent");mc.setScreen(new HudInteractionScreen(false,null,true));stage=7;next=now+300;return;}
  if(stage==7){capture("tab-widget-metadata");mc.options.keyPlayerList.setDown(false);mc.setScreen(new ChatScreen("#"));stage=8;next=now+400;return;}
  if(stage==8){var chat=(ChatScreen)mc.screen;var input=((dev.abros.rivet.mixin.ChatInputAccessor)chat).rivet$input();var nativeList=((dev.abros.rivet.mixin.ChatInputAccessor)chat).rivet$suggestions();check(SocialChatControls.groupSuggestions("#",1).getList().stream().anyMatch(v->v.getText().equals("RivetChatReview: ")),"Membership missing");check(nativeList.isVisible(),"Native completion list hidden");capture("group-suggestions");ServerMenuClient.state.addProperty("chatEnabled",false);check(!SocialSettings.INSTANCE.chat()&&SocialChatControls.groupSuggestions("#",1)==null,"Server disable ignored");ServerMenuClient.state.addProperty("chatEnabled",true);input.setValue("#RivetChatR");input.setCursorPosition(input.getValue().length());nativeList.updateCommandInfo();check(SocialChatControls.groupSuggestions(input.getValue(),input.getCursorPosition()).getList().size()==1,"Group filtering failed");check(chat.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB,0,0)&&input.getValue().equals("#RivetChatReview: "),"Native Tab completion failed");check(SocialChatControls.groupSuggestions(input.getValue(),input.getCursorPosition())==null,"Suggestions interfere with body");input.setValue("/");input.setCursorPosition(1);nativeList.updateCommandInfo();verifyChannelPicker();mc.setScreen(new ChatScreen("#"));System.out.println("RIVET_CHAT_IDENTITY_OK native group suggestions + Tab completion + server switch + chat metadata");stage=9;next=now+1000;}
  if(stage==9){
   var chat=new ChatScreen("");mc.setScreen(chat);check(chat.charTyped('D',0)&&chat.charTyped('e',0),"Nickname typing failed");var input=((dev.abros.rivet.mixin.ChatInputAccessor)chat).rivet$input();var suggestions=((dev.abros.rivet.mixin.ChatInputAccessor)chat).rivet$suggestions();suggestions.updateCommandInfo();chat.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB,0,0);if(!input.getValue().equals("Dev"))chat.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_TAB,0,0);System.out.println("RIVET_NATIVE_NICKNAME_TAB value="+input.getValue());check(input.getValue().equals("Dev"),"Vanilla nickname Tab completion failed");check(chat.charTyped('x',0),"Native typing failed");chat.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE,0,0);check(input.getValue().equals("Dev"),"Native backspace failed");
   var root=mc.player.connection.getCommands().getRoot().getChild("rivet");var status=root.getChild("status");check(status!=null&&status.getChild("available")==null&&status.getChild("busy")==null&&status.getChild("help")==null&&status.getChild("afk")==null,"Manual status commands remain");
   var messages=((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages();var local=messages.stream().filter(m->m.content().getString().contains("Проверка локального канала [item]")).findFirst().orElseThrow(()->new IllegalStateException("Item token was transformed"));var position=coordinateStyle(local.content());check(position!=null&&position.getClickEvent().getValue().endsWith(" Dev"),"Coordinates lack click or sender name");
   String targetDimension=position.getClickEvent().getValue().split(" ")[2],otherDimension=targetDimension.equals("minecraft:the_nether")?"minecraft:overworld":"minecraft:the_nether";
   check(local.content().getString().contains("["+dev.abros.rivet.network.DimensionLabels.name(targetDimension).getString()+" · ")&&!local.content().getString().contains(targetDimension),"Raw coordinate dimension ID remains");
   check(CoordinateLinkTooltip.text(position,otherDimension).getString().contains("Цель в другом измерении."),"Cross-dimension tooltip missing");
   check(!CoordinateLinkTooltip.text(position,targetDimension).getString().contains("другом измерении"),"Tooltip stale after dimension change");
   check(CoordinateLinkTooltip.text(position,otherDimension).getString().startsWith("Место от Dev."),"Tooltip author missing");
   check(CoordinateLinkTooltip.text(net.minecraft.network.chat.Style.EMPTY,"minecraft:overworld")==null,"Unrelated tooltip overridden");
   check(dev.abros.rivet.network.DimensionLabels.name("minecraft:the_nether").getString().equals("Ад")&&dev.abros.rivet.network.DimensionLabels.name("minecraft:the_end").getString().equals("Край")&&dev.abros.rivet.network.DimensionLabels.name("unknown:world").getString().equals("unknown:world"),"Dimension names/fallback incorrect");
   System.out.println("RIVET_DIMENSION_LABELS_OK coordinate names + author hover + live dimension warning + mod fallback");
   mc.player.connection.sendCommand("tp @s ~100 ~ ~100");stage=11;next=now+1000;return;
  }
  if(stage==11){var messages=((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages();var message=messages.stream().filter(m->m.content().getString().contains("Проверка локального канала [item]")).findFirst().orElseThrow();var chat=new ChatScreen("");mc.setScreen(chat);try{var method=Screen.class.getDeclaredMethod("handleComponentClicked",net.minecraft.network.chat.Style.class);method.setAccessible(true);check((boolean)method.invoke(chat,coordinateStyle(message.content())),"Coordinate click not handled");}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}stage=12;next=now+1200;return;}
  if(stage==12){check(DirectionCue.hasTarget(),"Coordinate click did not start navigation");check(DirectionCue.mapRows().get(0).getAsJsonObject().get("title").getAsString().equals("Место от Dev"),"Navigation sender missing");check(DirectionCue.bounds.width()>0,"Navigation not rendered with chat open");check(ChatHints.lines("@Dev").stream().noneMatch(line->line.contains("@")||line.contains("упомин")),"Mention hint remains");check(!SettingsSearchScreen.search("упомин").isEmpty(),"Mention setting missing");capture("navigation-with-chat");var point=DirectionCue.mapRows().get(0).getAsJsonObject().getAsJsonObject("location").deepCopy();point.addProperty("name","Место от Dev");check(DirectionCue.click(DirectionCue.bounds.right()-9,DirectionCue.bounds.y()+8),"Stop button ignored");check(!DirectionCue.hasTarget()&&DirectionCue.mapRows().isEmpty(),"Stopped route remains active");DirectionCue.receive(point);check(DirectionCue.hasTarget(),"New route cannot start after stop");System.out.println("RIVET_FINAL_CHAT_OK native nickname completion + typing/backspace + literal item token + removed status commands + coordinate click/navigation with chat + manual stop + removed mention hints/settings");stage=14;FeatureListScreen.searchPlayer(null,"Dev");next=now+1600;return;}
  if(stage==14){var row=mc.screen.children().stream().filter(child->child instanceof PlayerRow).findFirst().orElseThrow(()->new IllegalStateException("Player row absent"));((net.minecraft.client.gui.components.Button)row).onPress();stage=15;next=now+300;return;}
  if(stage==15){capture("player-metadata-spacing");if(mc.screen instanceof PlayerActionsScreen){stage=16;next=now+300;return;}check(mc.screen instanceof FeatureListScreen,"Expected player list");for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.getMessage().getString().equals("Профиль игрока…")){button.onPress();break;}check(mc.screen instanceof PlayerActionsScreen,"Expected player profile dialog");stage=16;next=now+300;return;}
  if(stage==16){capture("player-profile-spacing");stage=10;return;}



 }

 private static void verifyMentions(){
  var mc=Minecraft.getInstance();var sender=java.util.UUID.randomUUID();boolean setting=SocialSettings.INSTANCE.mentions;var saved=RivetHud.snapshot;var muted=new java.util.HashSet<>(HudSettings.INSTANCE.muted);
  try{
   var notices=ServerMenuClient.class.getDeclaredField("notices");var sound=ServerMenuClient.class.getDeclaredField("sound");notices.setAccessible(true);sound.setAccessible(true);boolean oldNotices=notices.getBoolean(null),oldSound=sound.getBoolean(null);
   try{
    notices.setBoolean(null,true);sound.setBoolean(null,false);HudSettings.INSTANCE.muted.clear();SocialSettings.INSTANCE.mentions=true;RivetHud.snapshot=new JsonObject();RivetHud.QUEUE.clear();ChatMentionNotice.reset();
    String text="Hello @"+mc.player.getGameProfile().getName();var identity=dev.abros.rivet.network.ChatPresentation.identity("","MentionSender","");
    var body=Component.literal(text).withStyle(style->style.withInsertion(new dev.abros.rivet.core.ChatEffectEnvelope(0,1).encode()));
    var line=dev.abros.rivet.network.ChatPresentation.format("$nickname: $message","[$channel] ","Local",sender,identity,body).copy().withStyle(style->style.withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_ENTITY,new net.minecraft.network.chat.HoverEvent.EntityTooltipInfo(net.minecraft.world.entity.EntityType.PLAYER,sender,identity))));
    ClientChat.system(new net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System(line,false));
    var rows=RivetHud.QUEUE.advance(net.minecraft.Util.getMillis(),5,true);check(rows.size()==1&&rows.getFirst().notice().event().equals("mention"),"Live mention did not produce notification");
    ClientChat.system(new net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System(line,false));check(RivetHud.QUEUE.advance(net.minecraft.Util.getMillis(),5,true).getFirst().count()==1,"Mention cooldown ignored");
    RivetHud.QUEUE.clear();ChatMentionNotice.reset();SocialSettings.INSTANCE.mentions=false;ChatMentionNotice.received(sender,text);check(RivetHud.QUEUE.advance(net.minecraft.Util.getMillis(),5,true).isEmpty(),"Disabled mentions still notify");
    SocialSettings.INSTANCE.mentions=true;mc.getPlayerSocialManager().hidePlayer(sender);ChatMentionNotice.received(sender,text);check(RivetHud.QUEUE.advance(net.minecraft.Util.getMillis(),5,true).isEmpty(),"Hidden sender still notifies");mc.getPlayerSocialManager().showPlayer(sender);
    var history=new JsonObject();history.addProperty("kind","chatHistory");history.addProperty("id",Long.MAX_VALUE-102);history.add("message",com.google.gson.JsonParser.parseString(Component.Serializer.toJson(line,mc.level.registryAccess())));ClientChat.receive(history);check(RivetHud.QUEUE.advance(net.minecraft.Util.getMillis(),5,true).isEmpty(),"History replay produced mention notification");
   }finally{notices.setBoolean(null,oldNotices);sound.setBoolean(null,oldSound);}
  }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}finally{mc.getPlayerSocialManager().showPlayer(sender);SocialSettings.INSTANCE.mentions=setting;RivetHud.snapshot=saved;HudSettings.INSTANCE.muted.clear();HudSettings.INSTANCE.muted.addAll(muted);RivetHud.QUEUE.clear();ChatMentionNotice.reset();}
 }
 private static void verifyHiddenChat(){
  var mc=Minecraft.getInstance();var id=java.util.UUID.randomUUID();var social=mc.getPlayerSocialManager();
  var identity=dev.abros.rivet.network.ChatPresentation.identity("","HiddenPlayer","");
  var line=Component.literal("hidden fixture").withStyle(style->style.withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_ENTITY,new net.minecraft.network.chat.HoverEvent.EntityTooltipInfo(net.minecraft.world.entity.EntityType.PLAYER,id,identity))));
  social.hidePlayer(id);
  try{
   var event=new net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System(line,false);ClientChat.system(event);check(event.isCanceled(),"Hidden player leaked through local/group chat");
   var alert=new net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System(Component.literal("Server restarting"),false);ClientChat.system(alert);check(!alert.isCanceled(),"Ignore hid server alert");
   var rows=((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages();int before=rows.size();
   var packet=new JsonObject();packet.addProperty("kind","chatHistory");packet.addProperty("id",Long.MAX_VALUE-100);packet.add("message",com.google.gson.JsonParser.parseString(Component.Serializer.toJson(line,mc.level.registryAccess())));ClientChat.receive(packet);check(rows.size()==before,"Hidden player leaked through saved history");
   var legacy=dev.abros.rivet.network.ChatPresentation.format("$head$nickname: $message","[$channel] ","",id,identity,Component.literal("old history"));
   packet.addProperty("id",Long.MAX_VALUE-101);packet.add("message",com.google.gson.JsonParser.parseString(Component.Serializer.toJson(legacy,mc.level.registryAccess())));ClientChat.receive(packet);check(rows.size()==before,"Hidden player leaked through legacy history");
   social.showPlayer(id);check(!ClientChat.hiddenChat(line),"Unhide did not take effect");
  }finally{social.showPlayer(id);}
 }
 private static void verifyPlayerText(){
  var player=new JsonObject();player.addProperty("name","Dev");player.addProperty("prefix","&a[ENGINEER]");player.addProperty("suffix","&b[TEST]");
  var name=PlayerText.name(player);check(name.getString().equals("[ENGINEER] Dev [TEST]"),"Player metadata joined without spaces");
  check(name.getSiblings().get(2).getStyle().getColor()==null,"Prefix color leaked into nickname");
  player.addProperty("prefix","  &a[ENGINEER]  ");player.addProperty("suffix"," &b[TEST] ");check(PlayerText.name(player).getString().equals("[ENGINEER] Dev [TEST]"),"Duplicate metadata spacing");
  player.addProperty("prefix","");check(PlayerText.name(player).getString().equals("Dev [TEST]"),"Empty prefix adds space");
  player.addProperty("suffix","");check(PlayerText.name(player).getString().equals("Dev"),"Empty metadata adds spaces");
  player.addProperty("prefix","&a[ENGINEER]");check(PlayerText.name(player).getString().equals("[ENGINEER] Dev"),"Empty suffix adds space");
  System.out.println("RIVET_PLAYER_TEXT_OK metadata spacing + empty fields + padded fields + independent nickname color");
 }
 private static void verifyChatFormats(){
  var mc=Minecraft.getInstance();var id=mc.player.getUUID();
  var identity=dev.abros.rivet.network.ChatPresentation.identity("&a[ENGINEER]","Dev","&b[TEST]");
  var message=Component.literal("$nickname literal coords").withStyle(style->style.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,"/rivet direction minecraft:overworld 0 0 -60 Dev")));
  var format="<$channel$head$prefix$nickname$suffix>: $message";
  var line=dev.abros.rivet.network.ChatPresentation.format(format,"$channel | ","Рядом",id,identity,message);
  check(line.getString().equals("<Рядом | [ENGINEER] Dev [TEST]>: $nickname literal coords"),"Custom format order/spacing");
  var visible=dev.abros.rivet.network.ChatPresentation.heads(line,true);
  check(id.equals(SocialClient.chatHead(mc.font.split(visible,1000).getFirst())),"Custom head slot missing");
  check(coordinateStyle(visible)!=null,"Template lost coordinate click");
  check(SocialClient.chatHead(mc.font.split(dev.abros.rivet.network.ChatPresentation.heads(line,false),1000).getFirst())==null,"Disabled head slot present");
  var plain=dev.abros.rivet.network.ChatPresentation.format(format,"[$channel] | ","",id,dev.abros.rivet.network.ChatPresentation.identity("","Dev",""),Component.literal("hello"));
  check(plain.getString().equals("<Dev>: hello"),"Empty metadata/channel left punctuation or spaces");
  var noHead=dev.abros.rivet.network.ChatPresentation.format("$message ← $nickname", "[$channel] | ","Рядом",id,identity,Component.literal("hello"));
  check(noHead.getString().equals("hello ← Dev")&&SocialClient.chatHead(mc.font.split(dev.abros.rivet.network.ChatPresentation.heads(noHead,true),1000).getFirst())==null,"Optional tokens/reordering ignored");
  var decoded=Component.Serializer.fromJson(Component.Serializer.toJson(line,mc.level.registryAccess()),mc.level.registryAccess());
  check(decoded!=null&&id.equals(SocialClient.chatHead(mc.font.split(dev.abros.rivet.network.ChatPresentation.heads(decoded,true),1000).getFirst())),"Head marker lost in network serialization");
  var colored=dev.abros.rivet.network.ChatPresentation.format(format,"[$channel] | ","Рядом",id,identity,message,0x112233);
  check(colored.getSiblings().get(1).getSiblings().getFirst().getStyle().getColor().getValue()==0x112233,"Channel delimiter color ignored");
  check(colored.getSiblings().get(3).getSiblings().getFirst().getStyle().getColor().getValue()==0x55FF55,"Channel color overwrote LuckPerms prefix");
  System.out.println("RIVET_CHAT_FORMAT_OK custom order + empty metadata/channel + explicit head slot/toggle + links + literal token payload + network serialization");
 }
 private static void verifyChatIndicator(){
  var original=ServerMenuClient.state.deepCopy();boolean hints=SocialSettings.INSTANCE.hints;
  try{
   ServerMenuClient.state.addProperty("chatLocalEnabled",true);ServerMenuClient.state.addProperty("chatGroupEnabled",true);
   ServerMenuClient.state.addProperty("chatLocalName","Рядом");ServerMenuClient.state.addProperty("chatLocalRadius",42);ServerMenuClient.state.addProperty("chatGlobalName","");
   SocialSettings.INSTANCE.hints=false;
   check(ChatChannelIndicator.label("hello").equals("Рядом · радиус 42 блоков"),"Local destination or radius missing when help disabled");
   check(ChatChannelIndicator.label("!hello").equals("Общий чат"),"Global destination missing");
   check(ChatChannelIndicator.label("#Город: hello").equals("Город"),"Group destination missing");
   check(ChatChannelIndicator.label("/tell Dev hello").isEmpty(),"Destination shown on command");
   ServerMenuClient.state.addProperty("chatLocalEnabled",false);check(ChatChannelIndicator.label("hello").equals("Общий чат"),"Disabled local routing ignored");
   ServerMenuClient.state.addProperty("chatGroupEnabled",false);check(ChatChannelIndicator.label("#Город: hello").contains("выключен"),"Disabled group routing ignored");
   ServerMenuClient.state.addProperty("chatGlobalColor","#123456");check(ChatChannelIndicator.color(dev.abros.rivet.core.ChatChannels.Channel.GLOBAL)==0x123456,"Server color ignored");
  }finally{ServerMenuClient.state=original;SocialSettings.INSTANCE.hints=hints;}
  System.out.println("RIVET_CHAT_CHANNELS_OK input destinations + server radius/colors + help independence + empty local audience warning");
 }
 private static void verifyNameplates(){
  var mc=Minecraft.getInstance();var state=ServerMenuClient.state.deepCopy();var profile=SocialClient.players.get(mc.player.getUUID());
  check(PlayerNameplates.enabled(),"Server nameplate setting missing");
  try{
   var styled=nameplate();PlayerNameplates.render(styled);check(styled.getContent().getString().equals("[ENGINEER] Dev [TEST]"),"Nameplate metadata missing: "+styled.getContent().getString());check(styled.canRender()==net.neoforged.neoforge.common.util.TriState.DEFAULT,"Visibility forced");
   ServerMenuClient.state.addProperty("nameplateMode","base");var disabled=nameplate();PlayerNameplates.render(disabled);check(disabled.getContent().equals(disabled.getOriginalContent()),"Server disable ignored");
   ServerMenuClient.state.addProperty("nameplateMode","rivet");var hidden=nameplate();hidden.setCanRender(net.neoforged.neoforge.common.util.TriState.FALSE);PlayerNameplates.render(hidden);check(hidden.getContent().equals(hidden.getOriginalContent())&&hidden.canRender()==net.neoforged.neoforge.common.util.TriState.FALSE,"Hidden label modified");
   ServerMenuClient.state.addProperty("nameplateMode","hidden");var suppressed=nameplate();suppressed.setContent(Component.literal("Custom name"));PlayerNameplates.render(suppressed);check(suppressed.canRender()==net.neoforged.neoforge.common.util.TriState.FALSE,"Hidden mode did not suppress label");ServerMenuClient.state.addProperty("nameplateMode","rivet");
   var custom=nameplate();custom.setContent(Component.literal("Custom name"));PlayerNameplates.render(custom);check(custom.getContent().getString().equals("Custom name"),"Other mod name overwritten");
   var plain=new JsonObject();plain.addProperty("prefix","");plain.addProperty("suffix","");SocialClient.players.put(mc.player.getUUID(),plain);var empty=nameplate();PlayerNameplates.render(empty);check(empty.getContent().equals(empty.getOriginalContent()),"Absent metadata modified name");
   ServerMenuClient.state.remove("nameplateMode");ServerMenuClient.state.remove("nameplatesEnabled");check(!PlayerNameplates.enabled(),"Old server unexpectedly enabled nameplates");
   System.out.println("RIVET_NAMEPLATES_OK LuckPerms prefix/name/suffix + server disable + vanilla visibility + other mod ownership + absent metadata + old server");
  }finally{for(var key:java.util.Set.copyOf(ServerMenuClient.state.keySet()))ServerMenuClient.state.remove(key);for(var entry:state.entrySet())ServerMenuClient.state.add(entry.getKey(),entry.getValue());if(profile!=null)SocialClient.players.put(mc.player.getUUID(),profile);}
 }
 private static net.neoforged.neoforge.client.event.RenderNameTagEvent nameplate(){var mc=Minecraft.getInstance();return new net.neoforged.neoforge.client.event.RenderNameTagEvent(mc.player,Component.literal(mc.player.getGameProfile().getName()),mc.getEntityRenderDispatcher().getRenderer(mc.player),new com.mojang.blaze3d.vertex.PoseStack(),mc.renderBuffers().bufferSource(),0,0);}
 private static net.minecraft.network.chat.Style coordinateStyle(Component component){var click=component.getStyle().getClickEvent();if(click!=null&&click.getValue().startsWith("/rivet direction "))return component.getStyle();for(var child:component.getSiblings()){var found=coordinateStyle(child);if(found!=null)return found;}return null;}
 private static void capture(String name){var dir=new java.io.File("/private/tmp/rivet-chat-identity");dir.mkdirs();var mc=Minecraft.getInstance();UiCaptureHarness.grab(dir,name+".png",mc.getMainRenderTarget(),msg->{});}
 private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 private static void verifyChannelPicker(){
  var mc=Minecraft.getInstance();var chat=new ChatScreen("draft retained");mc.setScreen(chat);
  check(SocialChatControls.chooseChannel(chat),"Channel picker unavailable");mc.screen.onClose();
  check(mc.screen instanceof ChatScreen,"Cancel did not return to chat");
  check(((dev.abros.rivet.mixin.ChatInputAccessor)mc.screen).rivet$input().getValue().equals("draft retained"),"Cancel lost draft");
  check(SocialChatControls.chooseChannel((ChatScreen)mc.screen),"Channel picker failed to reopen");
  String global=ChatChannelIndicator.label("! draft retained");
  var choice=mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(global)).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow();choice.onPress();
  check(mc.screen instanceof ChatScreen,"Channel choice did not return to chat");
  check(((dev.abros.rivet.mixin.ChatInputAccessor)mc.screen).rivet$input().getValue().equals("! draft retained"),"Channel choice lost message body");
  mc.setScreen(new ChatScreen("/tell Dev hello"));check(!SocialChatControls.chooseChannel((ChatScreen)mc.screen),"Channel picker rewrote command");
 }
}
