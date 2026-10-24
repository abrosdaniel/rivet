package dev.abros.rivet.mixin;
import com.mojang.authlib.GameProfile;
import dev.abros.rivet.server.AuthServer;
import net.minecraft.server.players.GameProfileCache;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Administrative name lookups use the same identity as incoming players. */
@Mixin(GameProfileCache.class)
abstract class ProfileLookupMixin {
 @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private java.util.Map<String,?> profilesByName;
 @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final private java.util.Map<java.util.UUID,?> profilesByUUID;
 @Inject(method="add",at=@At("HEAD"))
 private void rivet$removeOldName(GameProfile profile,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){
  if(!AuthServer.enabled())return;
  Object previous=profilesByUUID.get(profile.getId());
  if(previous!=null)profilesByName.values().removeIf(value->value==previous);
 }
 @Inject(method="get(Ljava/util/UUID;)Ljava/util/Optional;",at=@At("HEAD"),cancellable=true)
 private void rivet$lookupId(java.util.UUID id,CallbackInfoReturnable<Optional<GameProfile>> cir){
  if(AuthServer.enabled())AuthServer.knownIdentity(id).ifPresent(p->cir.setReturnValue(Optional.of(new GameProfile(p.uuid(),p.name()))));
 }
 @Inject(method="get(Ljava/lang/String;)Ljava/util/Optional;",at=@At("HEAD"),cancellable=true)
 private void rivet$lookup(String name,CallbackInfoReturnable<Optional<GameProfile>> cir){
  var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
  if(AuthServer.enabled()&&server!=null&&!server.usesAuthentication()&&dev.abros.rivet.core.auth.AuthStore.validName(name))cir.setReturnValue(Optional.of(AuthServer.profile(net.minecraft.core.UUIDUtil.createOfflineProfile(name))));
 }
 @Inject(method="get(Ljava/lang/String;)Ljava/util/Optional;",at=@At("RETURN"),cancellable=true)
 private void rivet$verifiedLookup(String name,CallbackInfoReturnable<Optional<GameProfile>> cir){
  if(AuthServer.enabled())cir.setReturnValue(cir.getReturnValue().map(AuthServer::lookupProfile));
 }
}
