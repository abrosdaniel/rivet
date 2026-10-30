package dev.abros.rivet.mixin.compat;
import dev.abros.rivet.compat.*;
import java.util.Optional;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo @Mixin(targets="io.github.mortuusars.envelope.world.mail.MailService",remap=false)
public abstract class EnvelopeMailServiceMixin {
 @Inject(method="getPlayerDefaultAddress",at=@At("HEAD"),cancellable=true,require=1)
 private void rivet$address(@Coerce Object address,CallbackInfoReturnable<Optional<?>> callback){
  if(EnvelopeBindings.route())callback.setReturnValue(EnvelopeBindings.serviceAddress(this,address));
 }
}
