package dev.abros.rivet.client;
import net.minecraft.network.chat.*;
/** Evaluate the current dimension at hover time, including after travelling through a portal. */
public final class CoordinateLinkTooltip {
 public static Component current(Style style){var mc=net.minecraft.client.Minecraft.getInstance();return !SocialClient.available()||!SocialSettings.chatEnabled()||mc.level==null?null:text(style,mc.level.dimension().location().toString());}
 static Component text(Style style,String dimension){
  if(style==null||style.getHoverEvent()==null)return null;
  Component original=style.getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT);if(original==null||!original.getString().startsWith("Место от "))return null;
  var click=style.getClickEvent();if(click==null||click.getAction()!=ClickEvent.Action.RUN_COMMAND)return null;
  String[] args=click.getValue().split(" ");if(args.length!=7||!args[0].equals("/rivet")||!args[1].equals("direction")||!args[6].matches("[A-Za-z0-9_]{1,16}"))return null;
  var tooltip=Component.literal("Место от "+args[6]+".\nНажмите, чтобы начать навигацию.");
  if(!args[2].equals(dimension))tooltip.append("\nЦель в другом измерении.");return tooltip;
 }
 private CoordinateLinkTooltip(){}
}
