package dev.abros.rivet.mixin;
import dev.abros.rivet.server.TradeMenu;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Vanilla accepts stale clicks and then resyncs; escrow must reject before moving items. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class TradeContainerRevisionMixin {
 @Shadow public ServerPlayer player;
 @Inject(method="handleContainerClick",at=@At("HEAD"),cancellable=true)
 private void rivet$tradeRevision(ServerboundContainerClickPacket packet,CallbackInfo ci){
  if(!player.server.isSameThread())return;
  if(player.containerMenu instanceof TradeMenu menu&&packet.getContainerId()==menu.containerId&&packet.getStateId()!=menu.getStateId()){menu.broadcastFullState();ci.cancel();}
 }
}
