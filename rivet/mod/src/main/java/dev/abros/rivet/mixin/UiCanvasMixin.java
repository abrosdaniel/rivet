package dev.abros.rivet.mixin;

import dev.abros.rivet.client.UiCanvas;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Native drawing boundaries preserve ordering between Minecraft resources and Skia batches. */
@Mixin(GuiGraphics.class)
abstract class UiCanvasMixin {
    private GuiGraphics self(){return (GuiGraphics)(Object)this;}
    @Inject(method={"flush","innerBlit","renderItem","renderItemDecorations","applyScissor","setColor","fillGradient","fillRenderType"},at=@At("HEAD"))
    private void rivet$nativeBoundary(CallbackInfo ci){UiCanvas.flush(self());}
    @Redirect(method="renderTooltipInternal",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;renderText(Lnet/minecraft/client/gui/Font;IILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V"))
    private void rivet$tooltip(net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent component,Font font,int x,int y,org.joml.Matrix4f pose,net.minecraft.client.renderer.MultiBufferSource.BufferSource buffer){
        if(UiCanvas.active()&&component instanceof UiTooltipTextAccessor text){UiCanvas.tooltipText(self(),font,text.rivet$text(),x,y);return;}
        UiCanvas.flush(self());component.renderText(font,x,y,pose,buffer);
    }
    @Inject(method="bufferSource",at=@At("HEAD"))
    private void rivet$buffer(CallbackInfoReturnable<net.minecraft.client.renderer.MultiBufferSource.BufferSource> cir){UiCanvas.flush(self());}
    @Inject(method="fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V",at=@At("HEAD"),cancellable=true)
    private void rivet$fill(RenderType type,int x,int y,int x2,int y2,int z,int color,CallbackInfo ci){
        if(UiCanvas.active()&&type==RenderType.guiTextHighlight()&&UiCanvas.selection(self(),x,y,x2,y2)){ci.cancel();return;}
        if(UiCanvas.active()&&type==RenderType.gui()&&UiCanvas.rounded(self(),Math.min(x,x2),Math.min(y,y2),Math.abs(x2-x),Math.abs(y2-y),0,color,false))ci.cancel();
        else UiCanvas.flush(self());
    }
    @Inject(method="drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I",at=@At("HEAD"),cancellable=true)
    private void rivet$text(Font font,String value,float x,float y,int color,boolean shadow,CallbackInfoReturnable<Integer> cir){
        if(value!=null&&UiCanvas.active()&&UiCanvas.drawText(self(),font,UiCanvas.sequence(font,value),x,y,color,shadow))cir.setReturnValue((int)x+font.width(value)+(shadow?1:0));else UiCanvas.flush(self());
    }
    @Inject(method="drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I",at=@At("HEAD"),cancellable=true)
    private void rivet$styled(Font font,FormattedCharSequence value,float x,float y,int color,boolean shadow,CallbackInfoReturnable<Integer> cir){
        if(UiCanvas.active()&&UiCanvas.drawText(self(),font,value,x,y,color,shadow))cir.setReturnValue((int)x+font.width(value)+(shadow?1:0));else UiCanvas.flush(self());
    }
}
