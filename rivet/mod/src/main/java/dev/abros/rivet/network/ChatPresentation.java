package dev.abros.rivet.network;
import dev.abros.rivet.core.ChatFormat;
import dev.abros.rivet.core.LegacyText;
import net.minecraft.network.chat.*;
import net.minecraft.world.entity.EntityType;
import java.util.*;
/** Component templates preserve link actions and carry a zero-width head slot to Rivet clients. */
public final class ChatPresentation {
 private static final String IDENTITY="rivet:chat-identity";
 public static Component text(String value){var out=Component.empty();for(var part:LegacyText.parse(value)){var style=Style.EMPTY.withBold(part.bold()).withItalic(part.italic());if(part.color()!=null)style=style.withColor(part.color());out.append(Component.literal(part.text()).withStyle(style));}return out;}
 public static Component identity(String prefix,String nickname,String suffix){
  return Component.empty().withStyle(style->style.withInsertion(IDENTITY))
   .append(prefix.isBlank()?Component.empty():text(prefix.strip()).copy().append(" "))
   .append(Component.literal(nickname))
   .append(suffix.isBlank()?Component.empty():Component.literal(" ").append(text(suffix.strip())));
 }
 public static boolean structured(Component identity){return IDENTITY.equals(identity.getStyle().getInsertion())&&identity.getSiblings().size()==3;}
 public static Component format(String format,String channelFormat,String channel,UUID id,Component identity,Component message){return format(format,channelFormat,channel,id,identity,message,dev.abros.rivet.core.ChatChannels.color(dev.abros.rivet.core.ChatChannels.LOCAL_COLOR));}
 public static Component format(String format,String channelFormat,String channel,UUID id,Component identity,Component message,int channelColor){
  Component prefix=Component.empty(),nickname=identity,suffix=Component.empty();
  if(structured(identity)){prefix=identity.getSiblings().get(0);nickname=identity.getSiblings().get(1);suffix=identity.getSiblings().get(2);}
  var values=Map.of("channel",channel.isBlank()?Component.empty():expand(ChatFormat.parse(channelFormat,true),Map.of("channel",Component.literal(channel).withStyle(s->s.withColor(channelColor))),channelColor),
   "head",Component.literal("").withStyle(s->s.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY,new HoverEvent.EntityTooltipInfo(EntityType.PLAYER,id,Optional.empty())))),
   "prefix",prefix,"nickname",nickname,"suffix",suffix,"message",message);
  return expand(ChatFormat.parse(format,false),values);
 }
 private static Component expand(List<ChatFormat.Part> parts,Map<String,Component> values){return expand(parts,values,0xA4B5C0);}
 private static Component expand(List<ChatFormat.Part> parts,Map<String,Component> values,int literalColor){var out=Component.empty();for(var part:parts)out.append(part.token()==null?Component.literal(part.literal()).withStyle(s->s.withColor(literalColor)):values.get(part.token()).copy());return out;}
 public static Component heads(Component message,boolean enabled){
  var hover=message.getStyle().getHoverEvent();var entity=hover==null?null:hover.getValue(HoverEvent.Action.SHOW_ENTITY);
  boolean slot=entity!=null&&entity.type==EntityType.PLAYER&&entity.name.isEmpty()&&message.plainCopy().getString().isEmpty();
  var out=slot?Component.literal(enabled?"   ":"").withStyle(message.getStyle()):message.plainCopy().withStyle(message.getStyle());
  for(var child:message.getSiblings())out.append(heads(child,enabled));return out;
 }
 private ChatPresentation(){}
}
