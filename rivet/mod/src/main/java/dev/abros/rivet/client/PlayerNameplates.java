package dev.abros.rivet.client;

import dev.abros.rivet.core.Json;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Decorates the native label only; visibility, sneaking and scoreboard lines remain vanilla. */
final class PlayerNameplates {
 private PlayerNameplates(){}
 static void install(){NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,PlayerNameplates::render);}
 static boolean enabled(){return SocialClient.available()&&ServerMenuClient.state.has("nameplatesEnabled")&&ServerMenuClient.state.get("nameplatesEnabled").getAsBoolean();}
 static void render(RenderNameTagEvent event){
  if(!enabled()||!(event.getEntity() instanceof Player player)||event.canRender()==net.neoforged.neoforge.common.util.TriState.FALSE)return;
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
