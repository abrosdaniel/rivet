package dev.abros.rivet.mixin;

import dev.abros.rivet.Rivet;
import java.util.List;
import java.util.function.BiConsumer;
import net.neoforged.neoforge.internal.BrandingControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Use the native branding renderer, including its spacing and title-screen fade. */
@Mixin(BrandingControl.class)
abstract class TitleBrandingMixin {
    @Shadow private static List<String> brandings;
    @Inject(method="forEachLine",at=@At("TAIL"))
    private static void rivet$version(boolean includeMinecraft,boolean reverse,BiConsumer<Integer,String> consumer,CallbackInfo ci){
        if(includeMinecraft)consumer.accept(brandings.size(),"Rivet "+Rivet.VERSION);
    }
}
