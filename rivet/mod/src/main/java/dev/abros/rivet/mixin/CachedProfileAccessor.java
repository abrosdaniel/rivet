package dev.abros.rivet.mixin;
import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.minecraft.server.players.GameProfileCache$GameProfileInfo")
public interface CachedProfileAccessor { @Accessor("profile") GameProfile rivet$profile(); }
