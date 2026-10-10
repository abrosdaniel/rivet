package dev.abros.rivet.mixin;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.*;
@Mixin(ModelPart.class)
public interface MapModelPartAccessor {
 @Accessor("cubes") List<ModelPart.Cube> rivet$cubes();
 @Accessor("children") Map<String,ModelPart> rivet$children();
}
