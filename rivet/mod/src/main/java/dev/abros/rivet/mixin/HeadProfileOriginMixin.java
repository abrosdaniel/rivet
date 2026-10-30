package dev.abros.rivet.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

/** Distinguishes automatically fetched account textures from explicitly supplied decorations. */
@Mixin(ResolvableProfile.class)
abstract class HeadProfileOriginMixin {
 @Inject(method="resolve",at=@At("RETURN"),cancellable=true)
 private void rivet$origin(CallbackInfoReturnable<CompletableFuture<ResolvableProfile>> callback){
  var input=(ResolvableProfile)(Object)this;
  if(input.isResolved())return;
  callback.setReturnValue(callback.getReturnValue().thenApply(result->{
   var profile=new GameProfile(result.gameProfile().getId(),result.gameProfile().getName());
   profile.getProperties().putAll(result.properties());
   profile.getProperties().put("rivet:head_account",new Property("rivet:head_account","1"));
   return new ResolvableProfile(profile);
  }));
 }
}
