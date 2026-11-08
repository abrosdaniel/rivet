package dev.abros.rivet.mixin.compat;
import dev.abros.rivet.compat.IndexorBindings;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
@Pseudo @Mixin(targets="ledger.events.LedgerEventHandler",remap=false)
public abstract class IndexorEventsMixin implements dev.abros.rivet.compat.IndexorBridge {
 @WrapMethod(method="onBlockBreak") private static void rivet$break(BlockEvent.BreakEvent e,Operation<Void> original){var old=IndexorBindings.begin(e);try{original.call(e);}finally{IndexorBindings.end(old);}}
 @WrapMethod(method="onBlockPlace") private static void rivet$place(BlockEvent.EntityPlaceEvent e,Operation<Void> original){var old=IndexorBindings.begin(e);try{original.call(e);}finally{IndexorBindings.end(old);}}
 @WrapMethod(method="onMultiBlockPlace") private static void rivet$multi(BlockEvent.EntityMultiPlaceEvent e,Operation<Void> original){var old=IndexorBindings.begin(e);try{original.call(e);}finally{IndexorBindings.end(old);}}
}
