package dev.abros.rivet.mixin;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Packet section replacements do not emit ClientChunkCache.onLightUpdate notifications. */
@Mixin(LevelLightEngine.class)
abstract class MapLightUpdatesMixin {
    @Unique private final Set<ChunkPos> rivet$lightChunks=new HashSet<>();
    @Unique private boolean rivet$clientEngine(){var level=Minecraft.getInstance().level;return level!=null&&level.getLightEngine()==(Object)this;}
    @Inject(method="queueSectionData",at=@At("TAIL"))
    private void rivet$lightQueued(LightLayer layer,SectionPos section,DataLayer data,CallbackInfo info){
        if(rivet$clientEngine()&&data!=null)rivet$lightChunks.add(section.chunk());
    }
    @Inject(method="runLightUpdates",at=@At("RETURN"))
    private void rivet$lightApplied(CallbackInfoReturnable<Integer> info){
        if(rivet$clientEngine())for(var chunk:rivet$lightChunks)dev.abros.rivet.client.WorldMapClient.changed(chunk.getWorldPosition());
        rivet$lightChunks.clear();
    }
}
