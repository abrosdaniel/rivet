package dev.abros.rivet.mixin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Font.class)
public interface MapFontAccessor {
    @Invoker("getFontSet") FontSet rivet$fontSet(ResourceLocation id);
}
