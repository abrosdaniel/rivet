package dev.abros.rivet.client;
import net.minecraft.network.chat.*;
/** Evaluate the current dimension at hover time, including after travelling through a portal. */
public final class CoordinateLinkTooltip {
 public static Component current(Style style){var mc=net.minecraft.client.Minecraft.getInstance();return !SocialClient.available()||!SocialSettings.chatEnabled()||mc.level==null?null:text(style,mc.level.dimension().location().toString());}
 static Component text(Style style,String dimension){
  if(style==null||style.getHoverEvent()==null)return null;
  Component original=style.getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT);if(original==null||!original.getString().startsWith(Client.text("ui.location_from_7537c568")))return null;
  var click=style.getClickEvent();if(click==null||click.getAction()!=ClickEvent.Action.RUN_COMMAND)return null;
  String[] args=click.getValue().split(" ");if(args.length!=7||!args[0].equals("/rivet")||!args[1].equals("direction")||!args[6].matches("[A-Za-z0-9_]{1,16}"))return null;
  var tooltip=Component.literal(Client.text("ui.location_from_7537c568")+args[6]+Client.text("ui.click_to_start_navigation_4640992c"));
  if(!args[2].equals(dimension))tooltip.append(Client.text("ui.the_destination_is_in_another_dimension_73afb290"));return tooltip;
 }
 private CoordinateLinkTooltip(){}
}
