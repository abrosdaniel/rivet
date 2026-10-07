package dev.abros.rivet.client;

import dev.abros.rivet.core.Json;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Server-controlled player labels; never changes scoreboard teams or other entity labels. */
final class PlayerNameplates {
 private PlayerNameplates(){}
 static void install(){NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,PlayerNameplates::render);}
 static String mode(){
  if(!SocialClient.available())return "base";
  String mode=Json.opt(ServerMenuClient.state,"nameplateMode","");
  if(java.util.Set.of("rivet","base","hidden").contains(mode))return mode;
  return ServerMenuClient.state.has("nameplatesEnabled")&&ServerMenuClient.state.get("nameplatesEnabled").getAsBoolean()?"rivet":"base";
 }
 static boolean enabled(){return mode().equals("rivet");}
 static void render(RenderNameTagEvent event){
  if(!(event.getEntity() instanceof Player player))return;
  String mode=mode();
  if(mode.equals("hidden")){event.setCanRender(net.neoforged.neoforge.common.util.TriState.FALSE);return;}
  if(!mode.equals("rivet")||event.canRender()==net.neoforged.neoforge.common.util.TriState.FALSE)return;
  // A renderer or another mod owns an already customized label.
  if(!event.getContent().equals(event.getOriginalContent())||!event.getContent().getString().equals(player.getGameProfile().getName()))return;
  var metadata=SocialClient.players.get(player.getUUID());if(metadata==null)return;
  String prefix=Json.opt(metadata,"prefix","").strip(),suffix=Json.opt(metadata,"suffix","").strip();
  if(prefix.isEmpty()&&suffix.isEmpty())return;
  var name=Component.empty();if(!prefix.isEmpty())name.append(PlayerText.text(prefix)).append(" ");
  name.append(event.getContent().copy());if(!suffix.isEmpty())name.append(" ").append(PlayerText.text(suffix));
  event.setContent(name);
 }
}
