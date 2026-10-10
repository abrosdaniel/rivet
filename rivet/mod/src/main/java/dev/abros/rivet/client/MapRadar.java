package dev.abros.rivet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Enemy;
import java.util.*;

/** Uses only entities already sent to this client; no world scans or extra entity requests. */
final class MapRadar {
 enum Category { PLAYER,HOSTILE,FRIENDLY,ITEM,OTHER }
 private static List<Entity> nearby=List.of();private static net.minecraft.client.multiplayer.ClientLevel owner;private static long next;
 static Category category(Entity e){return e instanceof Player?Category.PLAYER:e instanceof Enemy?Category.HOSTILE:e instanceof Mob?Category.FRIENDLY:e instanceof ItemEntity?Category.ITEM:Category.OTHER;}
 static boolean allowed(){var mc=Minecraft.getInstance();if(!WorldMapClient.allowed())return false;if(mc.hasSingleplayerServer())return dev.abros.rivet.server.ServerMap.radarAllowed();var policy=ServerMenuClient.state.getAsJsonObject("map");return policy!=null&&(!policy.has("radar")||policy.get("radar").getAsBoolean());}
 static boolean included(Entity e){var mc=Minecraft.getInstance();var s=MapRenderSettings.INSTANCE;if(mc.player==null||e==mc.player||e.isRemoved()||e.isInvisible()||e instanceof Player p&&p.isSpectator()||Math.abs(e.getY()-mc.player.getY())>s.radarHeight)return false;return switch(category(e)){case PLAYER->s.players&&!MapPositions.supported();case HOSTILE->s.hostile;case FRIENDLY->s.friendly;case ITEM,OTHER->false;};}
 private static List<Entity> entities(){var mc=Minecraft.getInstance();if(!allowed()||mc.level==null||mc.player==null){nearby=List.of();owner=null;return nearby;}long now=net.minecraft.Util.getMillis();if(owner!=mc.level||now>=next){owner=mc.level;next=now+100;var result=new ArrayList<Entity>();for(var e:mc.level.entitiesForRendering())if(included(e))result.add(e);result.sort(Comparator.comparingDouble(e->e.distanceToSqr(mc.player)));nearby=List.copyOf(result.subList(0,Math.min(256,result.size())));}return nearby;}
 static void world(GuiGraphics g,String dimension,dev.abros.rivet.core.map.MapViewport view,int width,int height){var mc=Minecraft.getInstance();if(!MapRenderSettings.INSTANCE.radarWorld||mc.level==null||!mc.level.dimension().location().toString().equals(dimension))return;float partial=mc.getTimer().getGameTimeDeltaPartialTick(true);for(var e:entities()){if(!included(e)||!MapLayers.visible(e instanceof Player?MapLayers.Layer.PLAYERS:MapLayers.Layer.MOBS,false))continue;var p=e.getPosition(partial);double x=view.screenX(p.x,width/2d),y=view.screenZ(p.z,height/2d);if(x>=8&&y>=8&&x<width-8&&y<height-8)draw(g,e,(int)x,(int)y);}}
 static void minimap(GuiGraphics g,dev.abros.rivet.core.map.MinimapProjection projection,int x,int y,int side,boolean round){if(!MapRenderSettings.INSTANCE.radar)return;float partial=Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);double radius=side/2d-13;for(var e:entities()){if(!included(e)||!MapLayers.visible(e instanceof Player?MapLayers.Layer.PLAYERS:MapLayers.Layer.MOBS,true))continue;var p=e.getPosition(partial);double dx=projection.screenX(p.x,p.z),dy=projection.screenY(p.x,p.z);if(round?dx*dx+dy*dy<=radius*radius:Math.max(Math.abs(dx),Math.abs(dy))<=radius)draw(g,e,x+side/2+(int)Math.round(dx),y+side/2+(int)Math.round(dy));}}
 private static void draw(GuiGraphics g,Entity e,int x,int y){var mc=Minecraft.getInstance();var settings=MapRenderSettings.INSTANCE;int color=switch(category(e)){case PLAYER->0xffffffff;case HOSTILE->0xffff5555;case FRIENDLY->0xffffff55;case ITEM->0xff55ffff;case OTHER->0xffaaaaaa;};
  int dy=(int)Math.round(e.getY()-mc.player.getY());
  if(settings.radarIcons){if(!portrait(g,e,x,y))g.fill(x-2,y-2,x+2,y+2,color);}else g.fill(x-2,y-2,x+2,y+2,color);
  if(Math.abs(dy)>2){int sy=dy>0?y-9:y+8;g.fill(x-1,sy,x+2,sy+1,color);}
  if(settings.radarNames){String name=UiKit.fit(mc.font,e.getDisplayName().getString(),64);int w=mc.font.width(name);g.fill(x-w/2-2,y+9,x+(w+1)/2+2,y+20,0xcc10151a);g.drawString(mc.font,name,x-w/2,y+10,color,false);}
 }
 /** Front faces and protruding details use the vanilla model's UV coordinates, never item substitutes. */
 @SuppressWarnings({"unchecked","rawtypes"})
 static boolean portrait(GuiGraphics g,Entity entity,int x,int y){
  com.mojang.blaze3d.systems.RenderSystem.enableBlend();com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
  if(entity instanceof net.minecraft.client.player.AbstractClientPlayer player){var texture=player.getSkin().texture();g.pose().pushPose();try{g.pose().translate(x-6,y-6,0);g.pose().scale(1.5f,1.5f,1);face(g,texture,0,0,8,8,8,8,64);face(g,texture,0,0,40,8,8,8,64);}finally{g.pose().popPose();}return true;}
  var type=entity.getType();int u,v,w,h,th=32,canvasW,canvasH;
  if(type==EntityType.ZOMBIE){u=v=w=h=8;th=64;}
  else if(type==EntityType.SKELETON||type==EntityType.WITHER_SKELETON||type==EntityType.STRAY||type==EntityType.CREEPER||type==EntityType.ENDERMAN||type==EntityType.BLAZE){u=v=w=h=8;}
  else if(type==EntityType.COW||type==EntityType.MOOSHROOM){u=v=6;w=h=8;}
  else if(type==EntityType.PIG){u=v=w=h=8;}
  else if(type==EntityType.SHEEP){u=v=8;w=h=6;}
  else if(type==EntityType.WOLF){u=v=4;w=h=6;}
  else if(type==EntityType.CHICKEN){u=v=3;w=4;h=6;}
  else if(type==EntityType.RABBIT){u=37;v=5;w=5;h=4;}
  else if(type==EntityType.VILLAGER||type==EntityType.WANDERING_TRADER){u=v=w=8;h=10;th=64;}
  else return entity instanceof LivingEntity living&&MapMobIcons.draw(g,living,x,y);
  canvasW=type==EntityType.COW||type==EntityType.MOOSHROOM?10:w;
  canvasH=type==EntityType.WOLF?8:type==EntityType.RABBIT?9:type==EntityType.COW||type==EntityType.MOOSHROOM?9:h;
  try{var renderer=(net.minecraft.client.renderer.entity.EntityRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);var texture=renderer.getTextureLocation(entity);float scale=12f/Math.max(canvasW,canvasH);g.pose().pushPose();try{
   g.pose().translate(x-canvasW*scale/2,y-canvasH*scale/2,0);g.pose().scale(scale,scale,1);
   int ox=(canvasW-w)/2,oy=canvasH-h;face(g,texture,ox,oy,u,v,w,h,th);
   if(type==EntityType.PIG)face(g,texture,2,4,17,17,4,3,th);
   if(type==EntityType.WOLF){face(g,texture,0,0,17,15,2,2,th);face(g,texture,4,0,17,15,2,2,th);face(g,texture,2,5,4,14,3,3,th);}
   if(type==EntityType.COW||type==EntityType.MOOSHROOM){face(g,texture,0,0,23,1,1,3,th);face(g,texture,9,0,23,1,1,3,th);}
   if(type==EntityType.CHICKEN){face(g,texture,0,2,16,2,4,2,th);face(g,texture,1,4,16,6,2,2,th);}
   if(type==EntityType.RABBIT){face(g,texture,0,0,53,1,2,5,th);face(g,texture,3,0,59,1,2,5,th);face(g,texture,2,7,33,10,1,1,th);}
   if(type==EntityType.VILLAGER||type==EntityType.WANDERING_TRADER)face(g,texture,3,6,26,2,2,4,th);
  }finally{g.pose().popPose();}return true;}catch(RuntimeException e){return false;}
 }
 private static void face(GuiGraphics g,net.minecraft.resources.ResourceLocation texture,int x,int y,int u,int v,int w,int h,int th){g.blit(texture,x,y,(float)u,(float)v,w,h,64,th);}
 private MapRadar(){}
}
