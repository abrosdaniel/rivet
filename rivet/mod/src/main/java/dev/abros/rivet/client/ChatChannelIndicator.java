package dev.abros.rivet.client;
import dev.abros.rivet.core.ChatChannels;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
/** Destination feedback is independent of the optional chat help panel. */
final class ChatChannelIndicator {
 static String label(String input){
  if(input.startsWith("/"))return "";
  var state=ServerMenuClient.state;
  var parsed=ChatChannels.parse(input,ChatHints.enabled("chatLocalEnabled"));
  return switch(parsed.channel()){
   case LOCAL -> name("chatLocalName",Client.text("ui.nearby_fa01903f"))+Client.text("ui.radius_1c9c5e55")+(state.has("chatLocalRadius")?state.get("chatLocalRadius").getAsInt():100)+Client.text("ui.blocks_daf64a5f");
   case GLOBAL -> name("chatGlobalName",Client.text("ui.global_chat_4b272a96"));
   case GROUP -> !ChatHints.enabled("chatGroupEnabled")?Client.text("ui.group_chat_disabled_c16b05dc"):parsed.group().isBlank()?Client.text("ui.group_select_a_name_3bc1d38d"):parsed.group();
  };
 }
 private static String name(String key,String fallback){String name=Json.opt(ServerMenuClient.state,key,"");return name.isBlank()?fallback:name;}
 static int color(ChatChannels.Channel channel){String key=switch(channel){case LOCAL->"chatLocalColor";case GLOBAL->"chatGlobalColor";case GROUP->"chatGroupColor";};String fallback=switch(channel){case LOCAL->ChatChannels.LOCAL_COLOR;case GLOBAL->ChatChannels.GLOBAL_COLOR;case GROUP->ChatChannels.GROUP_COLOR;};try{return ChatChannels.color(Json.opt(ServerMenuClient.state,key,fallback));}catch(IllegalArgumentException invalid){return ChatChannels.color(fallback);}}
 static void draw(GuiGraphics g,ChatScreen chat,String text){
  String label=label(text);if(label.isEmpty())return;
  var mc=Minecraft.getInstance();var accessor=(dev.abros.rivet.mixin.ChatInputAccessor)chat;var input=accessor.rivet$input();
  int max=Math.min(input.getWidth(),g.guiWidth()-8);if(max<12)return;String fitted=UiKit.fit(mc.font,label,max-8);int w=mc.font.width(fitted)+8,h=13;
  // Native completions occupy the left edge above input; move the badge to the right while they are open.
  int x=accessor.rivet$suggestions().isVisible()?g.guiWidth()-w-4:Math.max(4,input.getX());int y=Math.max(0,input.getY()-h-2);
  g.fill(x,y,x+w,y+h,0xD0000000);g.fill(x,y,x+2,y+h,0xFF000000|color(ChatChannels.parse(text,ChatHints.enabled("chatLocalEnabled")).channel()));
  g.drawString(mc.font,fitted,x+4,y+2,0xFF000000|color(ChatChannels.parse(text,ChatHints.enabled("chatLocalEnabled")).channel()),false);
 }
 private ChatChannelIndicator(){}
}
