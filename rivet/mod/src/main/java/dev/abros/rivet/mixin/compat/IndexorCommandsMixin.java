package dev.abros.rivet.mixin.compat;
import dev.abros.rivet.compat.IndexorBindings;
import net.minecraft.commands.CommandSourceStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Pseudo @Mixin(targets="ledger.commands.InspectCommand",remap=false)
public abstract class IndexorCommandsMixin implements dev.abros.rivet.compat.IndexorBridge {
 @Redirect(method="lambda$register$5",at=@At(value="INVOKE",target="Lnet/minecraft/commands/CommandSourceStack;hasPermission(I)Z"))
 private static boolean rivet$permission(CommandSourceStack source,int level){return IndexorBindings.allowed(source);}
 @Redirect(method="*",at=@At(value="INVOKE",target="Lledger/core/LogEntry;getPlayerName()Ljava/lang/String;"))
 private static String rivet$currentName(@Coerce Object entry){return IndexorBindings.name(entry);}
}
