package dev.abros.rivet.mixin;

import dev.abros.rivet.server.ServerTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla change notification includes commands, fluids and neighbour updates, not only player events. */
@Mixin(ServerChunkCache.class)
public abstract class ServerTerrainUpdatesMixin {
 @Shadow @Final private ServerLevel level;
 @Inject(method="blockChanged",at=@At("TAIL"))
 private void rivet$terrainChanged(BlockPos pos,CallbackInfo ci){ServerTerrain.changed(level,Math.floorDiv(pos.getX(),16),Math.floorDiv(pos.getZ(),16));}
}
