package dev.abros.rivet.mixin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiGraphics.class)
abstract class CoordinateTooltipMixin {
 @Inject(method="renderComponentHoverEffect",at=@At("HEAD"),cancellable=true)
 private void rivet$coordinateTooltip(Font font,Style style,int x,int y,CallbackInfo ci){var tooltip=dev.abros.rivet.client.CoordinateLinkTooltip.current(style);if(tooltip!=null){((GuiGraphics)(Object)this).renderTooltip(font,tooltip,x,y);ci.cancel();}}
}
