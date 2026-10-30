package dev.abros.rivet.mixin.compat;
import dev.abros.rivet.compat.*;
import java.util.Optional;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo @Mixin(targets="io.github.mortuusars.envelope.world.KnownPlayers",remap=false)
public abstract class EnvelopeKnownPlayersMixin {
 @Inject(method="update",at=@At("HEAD"),cancellable=true,require=1)
 private void rivet$update(net.minecraft.world.entity.player.Player player,java.util.function.Consumer<?> updater,org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback){
  if(EnvelopeBindings.route()&&!EnvelopeBindings.mayUpdate(this,player))callback.cancel();
 }
 @Inject(method="getDefaultAddressOf(Lio/github/mortuusars/envelope/world/mail/address/type/PlayerAddress;)Ljava/util/Optional;",at=@At("HEAD"),cancellable=true,require=1)
 private void rivet$address(@Coerce Object address,CallbackInfoReturnable<Optional<?>> callback){
  if(EnvelopeBindings.route())callback.setReturnValue(EnvelopeBindings.defaultAddress(this,address));
 }
}
