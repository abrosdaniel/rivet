package dev.abros.rivet.mixin;
import com.mojang.authlib.GameProfile;
import dev.abros.rivet.client.SkinClient;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Negotiated server lookup with bounded timeout and vanilla fallback. */
@Mixin(SkullBlockEntity.class)
abstract class SkullIdentityClientMixin {
 @Inject(method="fetchGameProfile(Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;",at=@At("RETURN"),cancellable=true)
 private static void rivet$name(String name,CallbackInfoReturnable<CompletableFuture<Optional<GameProfile>>> callback){callback.setReturnValue(SkinClient.resolveProfile(name,null,callback.getReturnValue()));}
 @Inject(method="fetchGameProfile(Ljava/util/UUID;)Ljava/util/concurrent/CompletableFuture;",at=@At("RETURN"),cancellable=true)
 private static void rivet$id(UUID id,CallbackInfoReturnable<CompletableFuture<Optional<GameProfile>>> callback){callback.setReturnValue(SkinClient.resolveProfile("",id,callback.getReturnValue()));}
}
