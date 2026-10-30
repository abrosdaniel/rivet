package dev.abros.rivet.mixin;
import com.mojang.authlib.GameProfile;
import dev.abros.rivet.client.SkinClient;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Explicit textures belong to decorative heads, not the mutable account skin. */
@Mixin(SkullBlockRenderer.class)
abstract class SkullTextureMixin {
 @Redirect(method="getRenderType",at=@At(value="INVOKE",target="Lnet/minecraft/client/resources/SkinManager;getInsecureSkin(Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/client/resources/PlayerSkin;"))
 private static PlayerSkin rivet$texture(SkinManager manager,GameProfile profile){return profile.getProperties().containsKey("textures")?SkinClient.vanillaSkin(profile):manager.getInsecureSkin(profile);}
}
