package dev.abros.rivet.mixin;
import com.mojang.authlib.GameProfile;
import dev.abros.rivet.server.AuthServer;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Unresolved player heads keep the server's stable identity, including offline accounts. */
@Mixin(SkullBlockEntity.class)
abstract class SkullIdentityMixin {
 @Inject(method="fetchGameProfile(Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;",at=@At("HEAD"),cancellable=true)
 private static void rivet$name(String name,CallbackInfoReturnable<CompletableFuture<Optional<GameProfile>>> callback){
  if(AuthServer.enabled()&&dev.abros.rivet.core.auth.AuthStore.validName(name))AuthServer.knownIdentity(name).ifPresent(p->callback.setReturnValue(CompletableFuture.completedFuture(Optional.of(new GameProfile(p.uuid(),p.name())))));
 }
 @Inject(method="fetchGameProfile(Ljava/util/UUID;)Ljava/util/concurrent/CompletableFuture;",at=@At("HEAD"),cancellable=true)
 private static void rivet$id(UUID id,CallbackInfoReturnable<CompletableFuture<Optional<GameProfile>>> callback){
  if(AuthServer.enabled())AuthServer.knownIdentity(id).ifPresent(p->callback.setReturnValue(CompletableFuture.completedFuture(Optional.of(new GameProfile(p.uuid(),p.name())))));
 }
}
