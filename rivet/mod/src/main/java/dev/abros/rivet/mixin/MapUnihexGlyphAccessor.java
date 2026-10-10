package dev.abros.rivet.mixin;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.minecraft.client.gui.font.providers.UnihexProvider$Glyph")
public interface MapUnihexGlyphAccessor {
    @Accessor("contents") UnihexProvider.LineData rivet$contents();
    @Accessor("left") int rivet$left();
    @Accessor("right") int rivet$right();
}
