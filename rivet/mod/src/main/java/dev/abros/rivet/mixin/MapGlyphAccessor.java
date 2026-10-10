package dev.abros.rivet.mixin;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(BakedGlyph.class)
public interface MapGlyphAccessor {
    @Accessor("left") float rivet$left();
    @Accessor("right") float rivet$right();
    @Accessor("up") float rivet$up();
    @Accessor("down") float rivet$down();
}
