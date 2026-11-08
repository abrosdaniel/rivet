package dev.abros.rivet.client;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.*;
/** Supplies group names to Minecraft's native completion list. */
public final class SocialChatControls {
 static void install(){NeoForge.EVENT_BUS.addListener(SocialChatControls::render);NeoForge.EVENT_BUS.addListener(SocialChatControls::click);NeoForge.EVENT_BUS.addListener(SocialChatControls::key);}
 private static void render(ScreenEvent.Render.Post e){if(e.getScreen() instanceof ChatScreen)DirectionCue.tooltip(e.getGuiGraphics(),e.getMouseX(),e.getMouseY());if(e.getScreen() instanceof ChatScreen chat&&SocialClient.available()&&SocialSettings.chatEnabled()){String text=((dev.abros.rivet.mixin.ChatInputAccessor)chat).rivet$input().getValue();ChatHints.draw(e.getGuiGraphics(),text,false);ChatChannelIndicator.draw(e.getGuiGraphics(),chat,text);}}
 private static void click(ScreenEvent.MouseButtonPressed.Pre e){if(e.getScreen() instanceof ChatScreen chat&&e.getButton()==0&&ChatChannelIndicator.hit(chat,e.getMouseX(),e.getMouseY())&&chooseChannel(chat)){e.setCanceled(true);return;}if(e.getScreen() instanceof ChatScreen&&e.getButton()==0&&DirectionCue.click(e.getMouseX(),e.getMouseY()))e.setCanceled(true);}
 private static void key(ScreenEvent.KeyPressed.Pre e){if(e.getScreen() instanceof ChatScreen chat&&e.getKeyCode()==258&&(e.getModifiers()&2)!=0&&chooseChannel(chat))e.setCanceled(true);}
 static boolean chooseChannel(ChatScreen chat){
  if(!SocialClient.available()||!SocialSettings.chatEnabled())return false;
  var input=((dev.abros.rivet.mixin.ChatInputAccessor)chat).rivet$input();String draft=input.getValue();if(draft.startsWith("/"))return false;
  // Vanilla chat packets and the fresh return screen both accept at most 256 characters.
  int limit=256;if(draft.length()>limit)return false;boolean local=ChatHints.enabled("chatLocalEnabled");
  var values=new ArrayList<String>();var labels=new ArrayList<String>();
  java.util.function.BiConsumer<dev.abros.rivet.core.ChatChannels.Channel,String> add=(channel,group)->dev.abros.rivet.core.ChatChannels.draft(draft,local,channel,group,limit,ChatHints.globalTrigger(),ChatHints.groupTrigger(),ChatHints.localTrigger(),ChatHints.personalTrigger()).ifPresent(value->{values.add(value);labels.add(ChatChannelIndicator.label(value));});
  if(ChatHints.enabled("chatGlobalEnabled"))add.accept(dev.abros.rivet.core.ChatChannels.Channel.GLOBAL,"");
  if(local)add.accept(dev.abros.rivet.core.ChatChannels.Channel.LOCAL,"");
  if(ChatHints.enabled("chatGroupEnabled"))for(String group:SocialClient.chatGroups)add.accept(dev.abros.rivet.core.ChatChannels.Channel.GROUP,group);
  for(String name:onlineNames())add.accept(dev.abros.rivet.core.ChatChannels.Channel.PERSONAL,name);
  if(values.isEmpty())return false;
  var mc=net.minecraft.client.Minecraft.getInstance();var returnTo=new ChatScreen(draft);
  mc.setScreen(new ChoicePopup(returnTo,Client.text("chat.chooseChannel"),labels,n->{
   String value=values.get(n);var parsed=ChatHints.parse(value);
   if(parsed.channel()==dev.abros.rivet.core.ChatChannels.Channel.GROUP&&(!ChatHints.enabled("chatGroupEnabled")||!SocialClient.chatGroups.contains(parsed.group())))return;
   if(parsed.channel()==dev.abros.rivet.core.ChatChannels.Channel.GLOBAL&&!ChatHints.enabled("chatGlobalEnabled"))return;
   if(parsed.channel()==dev.abros.rivet.core.ChatChannels.Channel.LOCAL&&!ChatHints.enabled("chatLocalEnabled"))return;
   if(parsed.channel()==dev.abros.rivet.core.ChatChannels.Channel.PERSONAL&&!onlineNames().contains(parsed.group()))return;
   if(!SocialClient.available()||!SocialSettings.chatEnabled())return;
   ((dev.abros.rivet.mixin.ChatInputAccessor)returnTo).rivet$input().setValue(value);
  }));return true;
 }
 private static List<String> onlineNames(){var mc=net.minecraft.client.Minecraft.getInstance();return mc.getConnection()==null?List.of():mc.getConnection().getOnlinePlayers().stream().map(p->p.getProfile().getName()).sorted(String.CASE_INSENSITIVE_ORDER).toList();}
 public static com.mojang.brigadier.suggestion.Suggestions groupSuggestions(String text,int cursor){
  int colon=text.indexOf(':');if(!SocialClient.available()||!SocialSettings.chatEnabled()||text.startsWith("/"))return null;
  var channel=ChatHints.parse(text).channel();boolean personal=channel==dev.abros.rivet.core.ChatChannels.Channel.PERSONAL;
  if(!personal&&(channel!=dev.abros.rivet.core.ChatChannels.Channel.GROUP||!ChatHints.enabled("chatGroupEnabled")))return null;
  int start=(personal?ChatHints.personalTrigger():ChatHints.groupTrigger()).length();
  if(cursor<start||colon>=0&&cursor>colon)return null;
  String query=text.substring(start,cursor).toLowerCase(Locale.ROOT);int end=colon>=0?colon:text.length();var range=com.mojang.brigadier.context.StringRange.between(start,end);
  var names=(personal?onlineNames():SocialClient.chatGroups).stream().filter(name->name.toLowerCase(Locale.ROOT).startsWith(query)).map(name->new com.mojang.brigadier.suggestion.Suggestion(range,name+(colon>=0?"":": "))).toList();return new com.mojang.brigadier.suggestion.Suggestions(range,names);
 }
}
