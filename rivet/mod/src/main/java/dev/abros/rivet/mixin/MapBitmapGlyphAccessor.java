package dev.abros.rivet.mixin;
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.minecraft.client.gui.font.providers.BitmapProvider$Glyph")
public interface MapBitmapGlyphAccessor {
    @Accessor("image") NativeImage rivet$image();
    @Accessor("offsetX") int rivet$x();
    @Accessor("offsetY") int rivet$y();
    @Accessor("width") int rivet$width();
    @Accessor("height") int rivet$height();
}
