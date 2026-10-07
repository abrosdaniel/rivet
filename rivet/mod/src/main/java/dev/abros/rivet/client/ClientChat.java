package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.*;
import net.neoforged.neoforge.common.NeoForge;
import java.util.UUID;

/** Chat presentation owns its handlers and repeat history, independently of TAB. */
final class ClientChat {
 static void install(){NeoForge.EVENT_BUS.addListener(ClientChat::chat);NeoForge.EVENT_BUS.addListener(ClientChat::system);SocialChatControls.install();}
 static void reset(){lastSystem="";lastAt=0;repeat=0;}
 private static HoverEvent.EntityTooltipInfo sender(Component message){var hover=message.getStyle().getHoverEvent();if(hover==null)return null;var value=hover.getValue(HoverEvent.Action.SHOW_ENTITY);return value!=null&&value.type==net.minecraft.world.entity.EntityType.PLAYER?value:null;}
 private static Component withHead(Component message,UUID id){if(!SocialSettings.INSTANCE.heads)return message;return Component.empty().append(Component.literal("   ").withStyle(style->style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,new HoverEvent.EntityTooltipInfo(net.minecraft.world.entity.EntityType.PLAYER,id,java.util.Optional.empty()))))).append(message); }
 public static UUID chatHead(net.minecraft.util.FormattedCharSequence sequence){var id=new UUID[1];sequence.accept((i,style,code)->{if(code==' '&&style.getHoverEvent()!=null){var entity=style.getHoverEvent().getValue(HoverEvent.Action.SHOW_ENTITY);if(entity!=null&&entity.type==net.minecraft.world.entity.EntityType.PLAYER&&entity.name.isEmpty()){id[0]=entity.id;return false;}}return true;});return SocialClient.available()&&SocialSettings.INSTANCE.heads&&SocialSettings.INSTANCE.chat()?id[0]:null;}
 public static void drawChatHead(net.minecraft.client.gui.GuiGraphics g,net.minecraft.client.gui.Font font,net.minecraft.util.FormattedCharSequence sequence,int x,int y){var id=chatHead(sequence);if(id==null)return;var preceding=Component.empty();sequence.accept((i,style,code)->{if(code==' '&&style.getHoverEvent()!=null){var entity=style.getHoverEvent().getValue(HoverEvent.Action.SHOW_ENTITY);if(entity!=null&&entity.type==net.minecraft.world.entity.EntityType.PLAYER&&entity.name.isEmpty())return false;}preceding.append(Component.literal(new String(Character.toChars(code))).withStyle(style));return true;});drawHead(g,id,x+font.width(preceding),y);}
 private static Component decorateSender(Component message,UUID id){var identity=sender(message);if(identity!=null&&identity.id.equals(id)&&identity.name.isPresent())return withHead(message,id);var out=message.plainCopy().withStyle(message.getStyle());for(var child:message.getSiblings())out.append(decorateSender(child,id));return out;}
 public static void drawHead(net.minecraft.client.gui.GuiGraphics g,UUID id,int x,int y){net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,SkinClient.skin(id),x,y,8);}
 private static void chat(net.neoforged.neoforge.client.event.ClientChatReceivedEvent.Player e){
  if(!SocialClient.available()||!SocialSettings.INSTANCE.chat()||e.isCanceled())return;
  var original=e.getPlayerChatMessage().decoratedContent();var author=sender(original);var row=SocialClient.players.get(e.getSender());
  Component name;if(author!=null&&author.id.equals(e.getSender())&&author.name.isPresent())name=author.name.get();
  else {String prefix=row==null?"":Json.opt(row,"prefix",""),suffix=row==null?"":Json.opt(row,"suffix","");var info=Minecraft.getInstance().getConnection().getPlayerInfo(e.getSender());name=dev.abros.rivet.network.ChatPresentation.identity(prefix,row==null?(info==null?"Игрок":info.getProfile().getName()):Json.opt(row,"name","Игрок"),suffix);}
  original=original.copy().withStyle(style->style.withHoverEvent(null));
  var line=dev.abros.rivet.network.ChatPresentation.format(Json.opt(ServerMenuClient.state,"chatFormat",dev.abros.rivet.core.ChatFormat.DEFAULT),Json.opt(ServerMenuClient.state,"chatChannelFormat",dev.abros.rivet.core.ChatFormat.CHANNEL_DEFAULT),Json.opt(ServerMenuClient.state,"chatGlobalName",""),e.getSender(),name,original,ChatChannelIndicator.color(dev.abros.rivet.core.ChatChannels.Channel.GLOBAL));
  e.setMessage(dev.abros.rivet.network.ChatPresentation.heads(line,SocialSettings.INSTANCE.heads));
 }
 private static String lastSystem="";private static long lastAt;private static int repeat;
 private static void system(net.neoforged.neoforge.client.event.ClientChatReceivedEvent.System e){if(!SocialClient.available()||!SocialSettings.chatEnabled()||e.isCanceled()||e.isOverlay())return;var mc=Minecraft.getInstance();var author=sender(e.getMessage());if(author!=null&&SocialSettings.INSTANCE.chat())e.setMessage(ServerMenuClient.state.has("chatFormat")?dev.abros.rivet.network.ChatPresentation.heads(e.getMessage().copy().withStyle(style->style.withHoverEvent(null)),SocialSettings.INSTANCE.heads):decorateSender(e.getMessage().copy().withStyle(style->style.withHoverEvent(null)),author.id));String text=e.getMessage().getString();long now=System.currentTimeMillis();if(author==null&&SocialSettings.INSTANCE.dedupe&&text.equals(lastSystem)&&now-lastAt<3000&&removeNewestSystem(mc,lastSystem)){repeat++;e.setCanceled(true);mc.gui.getChat().addMessage(e.getMessage().copy().append(Component.literal(" ×"+repeat)));}else{lastSystem=text;repeat=1;}lastAt=now;}

 private static boolean removeNewestSystem(Minecraft mc,String raw){var history=(dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat();var rows=history.rivet$messages();if(rows.isEmpty())return false;var newest=rows.getFirst();String s=newest.content().getString();if(newest.signature()==null&&(s.equals(raw)||s.matches(java.util.regex.Pattern.quote(raw)+" ×[0-9]+"))){rows.removeFirst();history.rivet$refresh();return true;}return false;}
}
