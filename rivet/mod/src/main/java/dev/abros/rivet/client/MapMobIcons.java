package dev.abros.rivet.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import java.util.*;

/** Resource-pack-aware head portraits, bounded in memory and work. */
final class MapMobIcons {
 private record Icon(ResourceLocation texture,int x,int y,int width,int height,long updated){}
 private static final LinkedHashMap<UUID,Icon> CACHE=new LinkedHashMap<>(64,.75f,true);
 private static final LinkedHashMap<UUID,Long> RETRY=new LinkedHashMap<>();
 private static int budget=2;private static long serial;
 static void beginFrame(){budget=2;}
 static void clear(){var textures=Minecraft.getInstance().getTextureManager();for(var icon:CACHE.values())textures.release(icon.texture());CACHE.clear();RETRY.clear();}
 static boolean draw(GuiGraphics g,LivingEntity entity,int x,int y){
  long now=net.minecraft.Util.getMillis();var icon=CACHE.get(entity.getUUID());
  if((icon==null||now-icon.updated()>5000)&&budget>0&&now>=RETRY.getOrDefault(entity.getUUID(),0L)){budget--;var next=bake(g,entity,now);if(next!=null){if(icon!=null)Minecraft.getInstance().getTextureManager().release(icon.texture());icon=next;CACHE.put(entity.getUUID(),icon);RETRY.remove(entity.getUUID());while(CACHE.size()>256){var key=CACHE.keySet().iterator().next();Minecraft.getInstance().getTextureManager().release(CACHE.remove(key).texture());}}else{RETRY.put(entity.getUUID(),now+5000);while(RETRY.size()>256)RETRY.remove(RETRY.keySet().iterator().next());}}
  if(icon==null)return false;
  float scale=12f/Math.max(icon.width(),icon.height());g.pose().pushPose();try{g.pose().translate(x-icon.width()*scale/2,y-icon.height()*scale/2,0);g.pose().scale(scale,scale,1);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();g.blit(icon.texture(),0,0,(float)icon.x(),(float)icon.y(),icon.width(),icon.height(),128,128);}finally{g.pose().popPose();}return true;
 }
 private static Icon bake(GuiGraphics caller,LivingEntity entity,long now){
  var mc=Minecraft.getInstance();caller.flush();int framebuffer=org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);int[] viewport=new int[4];org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT,viewport);boolean scissor=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
  var target=new TextureTarget(128,128,true,Minecraft.ON_OSX);NativeImage pixels=null;
  RenderSystem.backupProjectionMatrix();var model=RenderSystem.getModelViewStack();model.pushMatrix();model.identity();RenderSystem.applyModelViewMatrix();
  try{
   RenderSystem.disableScissor();RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,128,128,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
   var headParts=MapMobHeads.of(entity);if(headParts==null)return null;
   var poses=new LinkedHashMap<net.minecraft.client.model.geom.ModelPart,net.minecraft.client.model.geom.PartPose>();for(var part:headParts.parts())part.getAllParts().forEach(p->poses.putIfAbsent(p,p.storePose()));
   try{poses.keySet().forEach(net.minecraft.client.model.geom.ModelPart::resetPose);float[] bounds=MapMobHeads.bounds(headParts.parts());if(!Float.isFinite(bounds[0]))return null;
   float size=110f/Math.max(.1f,Math.max(bounds[3]-bounds[0],bounds[4]-bounds[1]));
   for(int attempt=0;attempt<4;attempt++){
    target.setClearColor(0,0,0,0);target.clear(Minecraft.ON_OSX);target.bindWrite(true);var gui=new GuiGraphics(mc,mc.renderBuffers().bufferSource());gui.pose().translate(64,64,-11000);gui.pose().scale(size,size,-size);if(sideView(entity))gui.pose().mulPose(new Quaternionf().rotationY(.6f));gui.pose().translate(-(bounds[0]+bounds[3])/2,-(bounds[1]+bounds[4])/2,-(bounds[2]+bounds[5])/2);
    com.mojang.blaze3d.platform.Lighting.setupForFlatItems();var buffer=gui.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(headParts.texture()));for(var part:headParts.parts())part.render(gui.pose(),buffer,15728880,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);gui.flush();
    pixels=new NativeImage(128,128,false);RenderSystem.bindTexture(target.getColorTextureId());pixels.downloadTexture(0,false);pixels.flipY();int left=128,top=128,right=-1,bottom=-1;
    for(int py=0;py<128;py++)for(int px=0;px<128;px++)if((pixels.getPixelRGBA(px,py)>>>24)>8){left=Math.min(left,px);top=Math.min(top,py);right=Math.max(right,px);bottom=Math.max(bottom,py);}
    if(right<0){pixels.close();pixels=null;return null;}
    if((left<2||top<2||right>125||bottom>125)&&attempt<3){pixels.close();pixels=null;size*=.5f;continue;}
    // Llama/camel model parts combine the face and a long neck in the same cube set.
    if(entity instanceof net.minecraft.world.entity.animal.horse.Llama||entity instanceof net.minecraft.world.entity.animal.camel.Camel){bottom=top+(int)Math.ceil((bottom-top+1)*(entity instanceof net.minecraft.world.entity.animal.camel.Camel?.27f:.46f))-1;left=128;right=-1;for(int py=top;py<=bottom;py++)for(int px=0;px<128;px++)if((pixels.getPixelRGBA(px,py)>>>24)>8){left=Math.min(left,px);right=Math.max(right,px);}if(right<left)return null;}
    var id=ResourceLocation.fromNamespaceAndPath("rivet","map/radar/model_"+(serial++));mc.getTextureManager().register(id,new DynamicTexture(pixels));pixels=null;return new Icon(id,left,top,right-left+1,bottom-top+1,now);
   }
   return null;
   }finally{poses.forEach(net.minecraft.client.model.geom.ModelPart::loadPose);com.mojang.blaze3d.platform.Lighting.setupFor3DItems();}
  }catch(RuntimeException failure){com.mojang.logging.LogUtils.getLogger().debug("Unable to draw map portrait for {}",entity.getType());return null;}
  finally{if(pixels!=null)pixels.close();target.destroyBuffers();org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER,framebuffer);RenderSystem.viewport(viewport[0],viewport[1],viewport[2],viewport[3]);RenderSystem.restoreProjectionMatrix();model.popMatrix();RenderSystem.applyModelViewMatrix();if(scissor)org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();}
 }
 private static boolean sideView(LivingEntity entity){return entity instanceof net.minecraft.world.entity.animal.AbstractFish||entity instanceof net.minecraft.world.entity.animal.horse.AbstractHorse||entity instanceof net.minecraft.world.entity.animal.Dolphin||entity instanceof net.minecraft.world.entity.animal.Parrot||entity instanceof net.minecraft.world.entity.animal.Turtle||entity instanceof net.minecraft.world.entity.animal.frog.Tadpole||entity instanceof net.minecraft.world.entity.animal.armadillo.Armadillo;}
 private MapMobIcons(){}
}
