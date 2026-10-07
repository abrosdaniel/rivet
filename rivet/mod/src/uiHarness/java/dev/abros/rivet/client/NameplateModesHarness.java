package dev.abros.rivet.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;

/** Runs actual render-event policy checks on a connected Minecraft player. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class NameplateModesHarness {
 private static boolean done;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(System.getenv("RIVET_NAMEPLATE_MODES_CHECK")==null||done)return;
  var mc=Minecraft.getInstance();if(mc.player==null||!SocialClient.available()||!ServerMenuClient.state.has("nameplateMode"))return;
  done=true;var state=ServerMenuClient.state.deepCopy();var old=SocialClient.players.get(mc.player.getUUID());
  try{
   var metadata=new JsonObject();metadata.addProperty("prefix","&a[Role]");metadata.addProperty("suffix","&b[Suffix]");SocialClient.players.put(mc.player.getUUID(),metadata);
   ServerMenuClient.state.addProperty("nameplateMode","rivet");var styled=label();PlayerNameplates.render(styled);check(styled.getContent().getString().equals("[Role] "+mc.player.getGameProfile().getName()+" [Suffix]"),"Rivet formatting missing");check(styled.canRender()==TriState.DEFAULT,"Rivet forced visibility");
   var alreadyHidden=label();alreadyHidden.setCanRender(TriState.FALSE);PlayerNameplates.render(alreadyHidden);check(alreadyHidden.getContent().equals(alreadyHidden.getOriginalContent()),"Hidden vanilla label decorated");
   ServerMenuClient.state.addProperty("nameplateMode","base");var base=label();PlayerNameplates.render(base);check(base.getContent().equals(base.getOriginalContent())&&base.canRender()==TriState.DEFAULT,"Base label modified");
   ServerMenuClient.state.addProperty("nameplateMode","hidden");var hidden=label();hidden.setContent(Component.literal("Another mod label"));hidden.setCanRender(TriState.TRUE);PlayerNameplates.render(hidden);check(hidden.canRender()==TriState.FALSE,"Hidden mode ignored custom/forced label");
   SocialClient.players.remove(mc.player.getUUID());var missing=label();PlayerNameplates.render(missing);check(missing.canRender()==TriState.FALSE,"Hidden requires LuckPerms metadata");
   var pig=new net.minecraft.world.entity.animal.Pig(net.minecraft.world.entity.EntityType.PIG,mc.level);var entity=new RenderNameTagEvent(pig,Component.literal("Pig"),mc.getEntityRenderDispatcher().getRenderer(pig),new com.mojang.blaze3d.vertex.PoseStack(),mc.renderBuffers().bufferSource(),0,0);PlayerNameplates.render(entity);check(entity.canRender()==TriState.DEFAULT,"Hidden affected non-player entity");
   ServerMenuClient.state.remove("nameplateMode");ServerMenuClient.state.addProperty("nameplatesEnabled",true);check(PlayerNameplates.enabled(),"Legacy server true ignored");ServerMenuClient.state.addProperty("nameplatesEnabled",false);check(PlayerNameplates.mode().equals("base"),"Legacy false changed meaning");ServerMenuClient.state.remove("nameplatesEnabled");check(PlayerNameplates.mode().equals("base"),"Absent server mode hides names");
   ServerMenuClient.state.addProperty("nameplateMode","hidden");ServerMenuClient.state.remove("features");check(PlayerNameplates.mode().equals("base"),"Unavailable server retained hidden policy");
   System.out.println("RIVET_NAMEPLATE_MODES_OK rivet/base/hidden + native visibility + absent metadata + custom labels + non-player + legacy server + unavailable server");
  }catch(Throwable failure){System.out.println("RIVET_NAMEPLATE_MODES_FAILED");failure.printStackTrace();}
  finally{ServerMenuClient.state=state;if(old==null)SocialClient.players.remove(mc.player.getUUID());else SocialClient.players.put(mc.player.getUUID(),old);mc.stop();}
 }
 private static RenderNameTagEvent label(){var mc=Minecraft.getInstance();return new RenderNameTagEvent(mc.player,Component.literal(mc.player.getGameProfile().getName()),mc.getEntityRenderDispatcher().getRenderer(mc.player),new com.mojang.blaze3d.vertex.PoseStack(),mc.renderBuffers().bufferSource(),0,0);}
 private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
