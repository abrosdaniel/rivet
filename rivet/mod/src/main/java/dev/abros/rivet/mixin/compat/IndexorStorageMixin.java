package dev.abros.rivet.mixin.compat;
import dev.abros.rivet.compat.IndexorBindings;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
@Pseudo @Mixin(targets={"ledger.storage.SQLiteStorage","ledger.storage.JsonStorage"},remap=false)
public abstract class IndexorStorageMixin implements dev.abros.rivet.compat.IndexorBridge {
 @Shadow(remap=false) public abstract List<?> queryPlayer(String uuid,int limit);
 @Inject(method="queryPlayerByName",at=@At("HEAD"),cancellable=true)
 private void rivet$identity(String name,int limit,CallbackInfoReturnable<List<?>> ci){var id=IndexorBindings.uuid(name);if(id!=null)ci.setReturnValue(queryPlayer(id,limit));}
}
