package dev.abros.rivet.mixin;
import dev.abros.rivet.client.UiCanvas;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(TooltipRenderUtil.class)
abstract class UiTooltipBackgroundMixin {
    @Inject(method="renderTooltipBackground(Lnet/minecraft/client/gui/GuiGraphics;IIIIIIIII)V",at=@At("HEAD"),cancellable=true)
    private static void rivet$background(GuiGraphics g,int x,int y,int w,int h,int z,int top,int bottom,int edgeTop,int edgeBottom,CallbackInfo ci){
        if(UiCanvas.active()&&UiCanvas.tooltipBackground(g,x,y,w,h))ci.cancel();
    }
}
