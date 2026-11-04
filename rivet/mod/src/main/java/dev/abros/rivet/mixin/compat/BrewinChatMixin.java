package dev.abros.rivet.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import org.spongepowered.asm.mixin.*;

@Pseudo
@Mixin(targets="umpaz.brewinandchewin.neoforge.client.TipsyEffects",remap=false)
public abstract class BrewinChatMixin {
 @WrapMethod(method="iCanHear")
 private static void rivet$nativeEnvelope(ClientChatReceivedEvent.Player event,Operation<Void> original){
  dev.abros.rivet.compat.BrewinChatAdapter.receive(event,adapted->original.call(adapted));
 }
}
