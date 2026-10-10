package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.world.entity.LivingEntity;
import dev.abros.rivet.mixin.*;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
/** Select anatomical head parts from the renderer's model, including child ears, jaws and horns. */
final class MapMobHeads {
 record Head(List<ModelPart> parts,net.minecraft.resources.ResourceLocation texture){}
 @SuppressWarnings({"rawtypes","unchecked"})
 static Head of(LivingEntity entity){
  var mc=Minecraft.getInstance();var renderer=(EntityRenderer)mc.getEntityRenderDispatcher().getRenderer(entity);var texture=renderer.getTextureLocation(entity);var parts=new ArrayList<ModelPart>();
  if(renderer instanceof LivingEntityRenderer living){var model=living.getModel();
   if(model instanceof ShulkerModel shulker)parts.add(shulker.getHead());
   if(model instanceof MapLlamaModelAccessor llama)parts.add(llama.rivet$head());
   if(model instanceof MapAgeableModelAccessor ageable){ageable.rivet$headParts().forEach(p->parts.add(p.hasChild("head")?p.getChild("head"):p.hasChild("body")?p.getChild("body"):p));if(model instanceof HumanoidModel humanoid)parts.add(humanoid.hat);if(parts.isEmpty())for(var p:ageable.rivet$bodyParts()){var head=find(p,"head");if(head!=null){parts.add(head);break;}if(p.hasChild("body")){parts.add(p.getChild("body"));break;}}}
   if(parts.isEmpty()&&model instanceof HeadedModel headed)parts.add(headed.getHead());
   if(parts.isEmpty()&&model instanceof HierarchicalModel hierarchy){var root=hierarchy.root();for(String name:List.of("head","center_head","segment0")){var found=find(root,name);if(found!=null){parts.add(found);break;}}
    if(parts.isEmpty()){
     // Slimes, ghasts, squids and similar creatures have a facial body rather than a separate head.
     for(String name:List.of("body","cube","core")){var found=find(root,name);if(found!=null){parts.add(found);break;}}
     for(String name:List.of("left_eye","right_eye","mouth")){var found=find(root,name);if(found!=null)parts.add(found);}
     if(parts.isEmpty()&&model instanceof LavaSlimeModel)parts.add(root);
    }
   }
  }else if(entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon){var root=mc.getEntityModels().bakeLayer(ModelLayers.ENDER_DRAGON);parts.add(root.getChild("head"));}
  if(entity instanceof net.minecraft.world.entity.animal.camel.Camel){for(int i=0;i<parts.size();i++){var original=parts.get(i);var access=(MapModelPartAccessor)(Object)original;var children=new HashMap<>(access.rivet$children());children.remove("reins");children.remove("bridle");var face=new ModelPart(access.rivet$cubes(),children);face.setInitialPose(original.getInitialPose());face.resetPose();parts.set(i,face);}}
  return parts.isEmpty()?null:new Head(parts,texture);
 }
 private static ModelPart find(ModelPart root,String name){for(var part:root.getAllParts().toList())if(part.hasChild(name))return part.getChild(name);return null;}
 static float[] bounds(List<ModelPart> parts){float[] b={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};var pose=new PoseStack();for(var part:parts)bounds(part,pose,b);return b;}
 private static void bounds(ModelPart part,PoseStack pose,float[] b){pose.pushPose();part.translateAndRotate(pose);var access=(MapModelPartAccessor)(Object)part;for(var c:access.rivet$cubes())for(int corner=0;corner<8;corner++){var p=new org.joml.Vector3f((corner%2==0?c.minX:c.maxX)/16,(corner/2%2==0?c.minY:c.maxY)/16,(corner/4==0?c.minZ:c.maxZ)/16);pose.last().pose().transformPosition(p);b[0]=Math.min(b[0],p.x);b[1]=Math.min(b[1],p.y);b[2]=Math.min(b[2],p.z);b[3]=Math.max(b[3],p.x);b[4]=Math.max(b[4],p.y);b[5]=Math.max(b[5],p.z);}for(var child:access.rivet$children().values())bounds(child,pose,b);pose.popPose();}
 private MapMobHeads(){}
}
