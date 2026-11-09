package dev.abros.rivet.mixin;
import net.minecraft.client.Minecraft;
import dev.abros.rivet.client.UiCanvas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
abstract class UiCanvasLifecycleMixin {
    @Inject(method="close",at=@At("HEAD")) private void rivet$close(CallbackInfo ci){UiCanvas.close();}
}
