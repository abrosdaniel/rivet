package dev.abros.rivet.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.model.data.ModelDataManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dynamic materials can change without any BlockState update. */
@Mixin(ModelDataManager.class)
abstract class MapModelDataUpdatesMixin {
 @Inject(method="requestRefresh",at=@At("TAIL"),remap=false)
 private void rivet$materialChanged(BlockEntity entity,CallbackInfo info){
  if(entity.getLevel()==Minecraft.getInstance().level)
   dev.abros.rivet.client.WorldMapClient.changed(entity.getBlockPos());
 }
}
