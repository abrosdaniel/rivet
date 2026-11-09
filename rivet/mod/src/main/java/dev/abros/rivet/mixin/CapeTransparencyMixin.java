package dev.abros.rivet.mixin;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Uploaded capes preserve alpha; vanilla's solid cape layer ignores it. */
@Mixin(CapeLayer.class)
abstract class CapeTransparencyMixin {
 @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/RenderType;entitySolid(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
 private RenderType rivet$capeAlpha(ResourceLocation texture){
  return texture.getNamespace().equals("rivet")&&texture.getPath().startsWith("capes/")?RenderType.entityTranslucent(texture):RenderType.entitySolid(texture);
 }
}
