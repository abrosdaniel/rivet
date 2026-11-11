package dev.abros.rivet.mixin;
import dev.abros.rivet.client.UiTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Screen.class)
abstract class MenuScreenMixin {
 @org.spongepowered.asm.mixin.Shadow protected net.minecraft.client.gui.Font font;
 @Inject(method="init(Lnet/minecraft/client/Minecraft;II)V",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/screens/Screen;font:Lnet/minecraft/client/gui/Font;",opcode=181,shift=At.Shift.AFTER))
 private void rivet$font(net.minecraft.client.Minecraft mc,int width,int height,CallbackInfo ci){if(dev.abros.rivet.client.UiCanvas.owns((Screen)(Object)this))font=dev.abros.rivet.client.UiFont.get();}
 @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method="renderWithTooltip")
 private void rivet$render(GuiGraphics g,int x,int y,float delta,com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original){dev.abros.rivet.client.UiCanvas.screen(g,(Screen)(Object)this,()->original.call(g,x,y,delta));}

 @Inject(method="renderBackground",at=@At("RETURN"))
 private void rivet$shell(GuiGraphics g,int x,int y,float delta,CallbackInfo ci){UiTheme.shell((Screen)(Object)this,g);}
 @Inject(method="keyPressed",at=@At("HEAD"),cancellable=true)
 private void rivet$keys(int key,int scan,int mods,CallbackInfoReturnable<Boolean> ci){if(UiTheme.keyboard((Screen)(Object)this,key))ci.setReturnValue(true);}
}
