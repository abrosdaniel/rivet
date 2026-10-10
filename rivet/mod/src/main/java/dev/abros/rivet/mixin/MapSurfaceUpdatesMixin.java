package dev.abros.rivet.mixin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Prioritize terrain affected by received block updates instead of waiting for a full map sweep. */
@Mixin(ClientLevel.class)
abstract class MapSurfaceUpdatesMixin{
 @Inject(method="setBlocksDirty",at=@At("TAIL"))
 private void rivet$mapChanged(BlockPos position,BlockState previous,BlockState next,CallbackInfo info){if(previous!=next)dev.abros.rivet.client.WorldMapClient.changed(position);}
}
