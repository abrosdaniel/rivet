package dev.abros.rivet.mixin.compat;
import dev.abros.rivet.compat.IndexorBindings;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="ledger.core.LedgerManager",remap=false)
public abstract class IndexorJournalMixin implements dev.abros.rivet.compat.IndexorBridge {
 @Inject(method="log",at=@At("HEAD"),cancellable=true)
 private void rivet$defer(@Coerce Object entry,CallbackInfo ci){if(IndexorBindings.defer(entry))ci.cancel();}
}
