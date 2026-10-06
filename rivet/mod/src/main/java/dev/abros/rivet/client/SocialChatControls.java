package dev.abros.rivet.client;
import net.minecraft.client.gui.screens.ChatScreen;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.*;
/** Supplies group names to Minecraft's native completion list. */
public final class SocialChatControls {
 static void install(){NeoForge.EVENT_BUS.addListener(SocialChatControls::render);NeoForge.EVENT_BUS.addListener(SocialChatControls::click);}
 private static void render(ScreenEvent.Render.Post e){if(e.getScreen() instanceof ChatScreen)DirectionCue.tooltip(e.getGuiGraphics(),e.getMouseX(),e.getMouseY());if(e.getScreen() instanceof ChatScreen chat&&SocialClient.available()&&SocialSettings.chatEnabled()){String text=((dev.abros.rivet.mixin.ChatInputAccessor)chat).rivet$input().getValue();ChatHints.draw(e.getGuiGraphics(),text,false);ChatChannelIndicator.draw(e.getGuiGraphics(),chat,text);}}
 private static void click(ScreenEvent.MouseButtonPressed.Pre e){if(e.getScreen() instanceof ChatScreen&&e.getButton()==0&&DirectionCue.click(e.getMouseX(),e.getMouseY()))e.setCanceled(true);}
 public static com.mojang.brigadier.suggestion.Suggestions groupSuggestions(String text,int cursor){
  int colon=text.indexOf(':');if(!SocialClient.available()||!SocialSettings.chatEnabled()||!ChatHints.enabled("chatGroupEnabled")||!text.startsWith("#")||cursor<1||colon>=0&&cursor>colon)return null;
  String query=text.substring(1,cursor).toLowerCase(Locale.ROOT);int end=colon>=0?colon:text.length();var range=com.mojang.brigadier.context.StringRange.between(1,end);
  var names=SocialClient.chatGroups.stream().filter(name->name.toLowerCase(Locale.ROOT).startsWith(query)).map(name->new com.mojang.brigadier.suggestion.Suggestion(range,name+(colon>=0?"":": "))).toList();return new com.mojang.brigadier.suggestion.Suggestions(range,names);
 }
}
