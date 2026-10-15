package dev.abros.rivet.mixin.compat;

import dev.abros.rivet.client.NumismaticsClientAdapter;
import dev.abros.rivet.compat.NumismaticsBindings;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

@Pseudo @Mixin(targets="dev.ithundxr.createnumismatics.util.UsernameUtils",remap=false)
public abstract class NumismaticsClientIdentityMixin {
 @Inject(method="getName(Ljava/util/UUID;Ljava/lang/String;)Ljava/lang/String;",at=@At("HEAD"),cancellable=true,require=0)
 private void rivet$clientName(UUID id,String fallback,CallbackInfoReturnable<String> callback){
  if(NumismaticsClientAdapter.active())callback.setReturnValue(NumismaticsBindings.CAPABILITY.call(()->NumismaticsClientAdapter.name(id,fallback),()->fallback));
 }
}
