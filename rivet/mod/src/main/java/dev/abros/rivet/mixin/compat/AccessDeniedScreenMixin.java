package dev.abros.rivet.mixin.compat;
import dev.abros.rivet.client.CompatibilityClient;
import dev.abros.rivet.compat.AccessDeniedBindings;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

@Pseudo @Mixin(targets="net.fw14.createAddons.accessDenied.screen.AccessControlScreen",remap=false)
public abstract class AccessDeniedScreenMixin {
 // Reflection keeps the optional PlayerEditBox type out of Rivet's class linkage.
 @Inject(method="submitToAdd",at=@At("HEAD"),cancellable=true,require=0)
 private void rivet$serverIdentity(CallbackInfo callback){
  if(!CompatibilityClient.active())return;callback.cancel();
  try{var target=AccessDeniedBindings.type("screen.AccessControlScreen");var input=target.getDeclaredField("playerUsernameBox");input.setAccessible(true);var box=(EditBox)input.get(this);var field=target.getDeclaredField("networkId");field.setAccessible(true);CompatibilityClient.add((UUID)field.get(this),box.getValue());box.setValue("");}
  catch(ReflectiveOperationException error){throw new IllegalStateException("Rivet Access Denied adapter contract changed",error);}
 }
}
