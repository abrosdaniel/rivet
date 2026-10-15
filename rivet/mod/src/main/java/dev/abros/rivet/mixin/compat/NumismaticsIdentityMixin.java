package dev.abros.rivet.mixin.compat;

import dev.abros.rivet.api.IntegrationApi;
import dev.abros.rivet.compat.NumismaticsBindings;
import dev.abros.rivet.core.compat.AccountIdentity;
import dev.abros.rivet.server.ServerProviders;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

@Pseudo @Mixin(targets="dev.ithundxr.createnumismatics.util.UsernameUtils",remap=false)
public abstract class NumismaticsIdentityMixin {
 @Inject(method="getName(Ljava/util/UUID;Ljava/lang/String;)Ljava/lang/String;",at=@At("HEAD"),cancellable=true,require=0)
 private void rivet$serverName(UUID id,String fallback,CallbackInfoReturnable<String> callback){
  var server=ServerLifecycleHooks.getCurrentServer();
  if(server==null||!server.isSameThread()||!NumismaticsBindings.CAPABILITY.available())return;
  var identities=ServerProviders.find(IntegrationApi.IDENTITY,"rivet");
  if(identities.isPresent())callback.setReturnValue(NumismaticsBindings.CAPABILITY.call(()->AccountIdentity.find(identities.get(),id).map(IntegrationApi.Player::name).orElse(fallback),()->fallback));
 }
}
