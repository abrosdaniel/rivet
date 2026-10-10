package dev.abros.rivet.mixin;
import net.minecraft.client.model.LlamaModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(LlamaModel.class)
public interface MapLlamaModelAccessor { @Accessor("head") ModelPart rivet$head(); }
