package dev.abros.rivet.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sample only after the received block entity data has been applied. */
@Mixin(ClientPacketListener.class)
abstract class MapBlockEntityUpdatesMixin {
 @Inject(method="handleBlockEntityData",at=@At("TAIL"))
 private void rivet$blockEntityChanged(ClientboundBlockEntityDataPacket packet,CallbackInfo info){
  dev.abros.rivet.client.WorldMapClient.changed(packet.getPos());
 }
}
