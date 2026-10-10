package dev.abros.rivet.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.GameRenderer;
import dev.abros.rivet.core.NativeLayout;
import dev.abros.rivet.core.map.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** A bounded HUD view of the same explored tiles and personal markers as the world map. */
final class Minimap {
 static final KeyMapping EXPAND=new KeyMapping("key.rivet.minimapExpand",org.lwjgl.glfw.GLFW.GLFW_KEY_Z,"key.categories.rivet");
 static NativeLayout.Box bounds=new NativeLayout.Box(0,0,0,0);
 private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("rivet","map/minimap");
 private static DynamicTexture texture;private static int imageSide;private static long nextUpdate,drawn,terrainSignature=Long.MIN_VALUE;
 private static boolean composing;private static long generation;
 private record Area(int x,int z,int side,int step,String dimension,int band){}
 // Geometry belongs to the uploaded pixels, never to an in-flight composition.
 private static Area displayed;
 private static MinimapProjection projection;private static final MinimapMotion motion=new MinimapMotion();
 private static int textureBand=MapLayer.SURFACE;private static int textureSide,textureX,textureZ,pixelStep;private static String textureDimension="";private static final Map<UUID,double[]> hits=new LinkedHashMap<>();
 static void invalidate(){nextUpdate=0;}
 static void reset(){generation++;composing=false;if(texture!=null)Minecraft.getInstance().getTextureManager().release(TEXTURE);texture=null;displayed=null;imageSide=0;projection=null;motion.reset();textureSide=0;textureDimension="";nextUpdate=drawn=0;terrainSignature=Long.MIN_VALUE;hits.clear();bounds=new NativeLayout.Box(0,0,0,0);EXPAND.setDown(false);}
 static boolean visible(){var mc=Minecraft.getInstance();return MapSettings.INSTANCE.enabled&&WorldMapClient.ready()&&mc.player!=null&&!mc.options.hideGui&&(!mc.getDebugOverlay().showDebugScreen()||HudSettings.INSTANCE.debug)&&(mc.screen==null||mc.screen instanceof ChatScreen||mc.screen instanceof HudInteractionScreen);}
 static void draw(GuiGraphics g,boolean preview){long timing=dev.abros.rivet.core.PerformanceMetrics.start();try{drawContents(g,preview);}finally{dev.abros.rivet.core.PerformanceMetrics.end("client.minimap.draw",timing);}}
 private static void drawContents(GuiGraphics g,boolean preview){
  hits.clear();if(!preview&&!visible()){bounds=new NativeLayout.Box(0,0,0,0);return;}
  var mc=Minecraft.getInstance();var s=MapSettings.INSTANCE;var captions=captions();int wanted=(int)Math.round(s.size*(!preview&&EXPAND.isDown()?1.6:1));
  int side=Math.max(32,Math.min(wanted,Math.min(g.guiWidth()-16,g.guiHeight()-40-captions.size()*11)));var hud=MapSettings.INSTANCE.hud;
  bounds=HudPlacement.fit(g.guiWidth(),g.guiHeight(),side,side+(captions.isEmpty()?0:captions.size()*11+6),hud.mapAnchorX,hud.mapAnchorY,hud.mapOffsetX,hud.mapOffsetY);side=bounds.width();
  float partial=mc.getTimer().getGameTimeDeltaPartialTick(true);var position=mc.player==null?net.minecraft.world.phys.Vec3.ZERO:mc.player.getPosition(partial);
  double cx=position.x,cz=position.z;float yaw=mc.player==null?0:mc.player.getViewYRot(partial);long now=net.minecraft.Util.getMillis();
  double speed=mc.player==null||preview?0:Math.hypot(mc.player.getX()-mc.player.xo,mc.player.getZ()-mc.player.zo);
  double zoom=motion.update(s.zoom*(mc.level!=null&&MapCaves.layer(mc.level.dimension().location().toString()).cave()?MapCaves.options().zoom():1),speed,now,AccessibilityScreen.animations());projection=new MinimapProjection(cx,cz,zoom,yaw,s.rotate);
  imageSide=side;String dimension=mc.level==null?"minecraft:overworld":mc.level.dimension().location().toString();
  int span=(int)Math.ceil(side/zoom*1.42)+64;int step=1<<MapDetail.level(zoom,mc.getWindow().getGuiScale());while(step*2048<span)step*=2;int pixels=64;while(pixels<2048&&pixels*step<span)pixels*=2;
  boolean resized=textureSide!=pixels||pixelStep!=step;
  textureSide=pixels;pixelStep=step;
  double margin=side/zoom*.71;
  int band=MapCaves.layer(dimension).band();boolean moved=band!=textureBand||resized||!dimension.equals(textureDimension)||cx-margin<textureX||cz-margin<textureZ||cx+margin>=textureX+textureSide*pixelStep||cz+margin>=textureZ+textureSide*pixelStep;
  if(moved){textureX=(int)Math.floor(cx/pixelStep)*pixelStep-textureSide*pixelStep/2;textureZ=(int)Math.floor(cz/pixelStep)*pixelStep-textureSide*pixelStep/2;textureDimension=dimension;textureBand=band;terrainSignature=Long.MIN_VALUE;}
  if(moved||now>=nextUpdate){update();nextUpdate=now+100;}
  int x=bounds.x(),y=bounds.y();surface(g,x,y,side,s.round,s.opacity);
  if(WorldMapClient.ready()&&mc.level!=null){int radius=side/2-2;
   MapTerritories.minimap(g,projection,dimension,x,y,side,s.round);
   long markerTiming=dev.abros.rivet.core.PerformanceMetrics.start();
   try{nearestMarkers(cx,cz,dimension,projection.zoom()).forEach(m->{double px=projection.screenX(m.x()+.5,m.z()+.5),py=projection.screenY(m.x()+.5,m.z()+.5);{var pinned=MinimapProjection.pin(px,py,radius,s.round);px=pinned[0];py=pinned[1];double mx=x+imageSide/2d+px,my=y+imageSide/2d+py;g.pose().pushPose();g.pose().translate(mx,my,0);g.pose().scale(MapRenderSettings.INSTANCE.markerScale,MapRenderSettings.INSTANCE.markerScale,1);g.fill(-7,-7,7,7,0xc010151a);g.renderOutline(-7,-7,14,14,m.color());if(m.icon().equals("none"))MapGlyphs.initial(g,m.name(),0,0,12,m.color());else MapGlyphs.marker(g,m.icon(),-6,-6,12);g.pose().popPose();hits.put(m.id(),new double[]{mx,my});}});}finally{dev.abros.rivet.core.PerformanceMetrics.end("client.minimap.markers",markerTiming);}
   var target=DirectionCue.target();if(MapLayers.visible(MapLayers.Layer.ROUTE,true)&&MapSettings.INSTANCE.hud.directionMap&&target!=null&&target.dimension().equals(dimension)){double px=projection.screenX(target.x()+.5,target.z()+.5),py=projection.screenY(target.x()+.5,target.z()+.5);var pinned=MinimapProjection.pin(px,py,radius,s.round);px=pinned[0];py=pinned[1];UiIcons.draw(g,MapGlyphs.icon("navigate"),x+side/2+(int)Math.round(px)-6,y+side/2+(int)Math.round(py)-6,UiKit.accent());}
  }
  MapRadar.minimap(g,projection,x,y,side,s.round);
  MapSharedOverlay.minimap(g,projection,x,y,side,s.round);
  MapPlayerArrow.minimap(g,x+side/2,y+side/2,s.rotate?-180:yaw,side);
  float compassScale=Math.clamp(side/160f,.5f,1.25f);
  for(int i=0;i<4;i++){
   String letter=Client.tr("map.compass."+List.of("north","east","south","west").get(i)).getString();
   double wx=cx+(i==1?1:i==3?-1:0),wz=cz+(i==2?1:i==0?-1:0);
   double inset=2+Math.max(mc.font.width(letter),mc.font.lineHeight)*compassScale/2;
   var compass=MinimapProjection.compass(projection.screenX(wx,wz),projection.screenY(wx,wz),side/2d-inset,s.round);
   g.pose().pushPose();g.pose().translate(x+side/2d+compass[0],y+side/2d+compass[1],0);g.pose().scale(compassScale,compassScale,1);
   g.drawString(mc.font,letter,-mc.font.width(letter)/2,-4,0xfff4f4f4,true);g.pose().popPose();
  }
  if(preview&&!WorldMapClient.ready())g.drawCenteredString(mc.font,Client.text("map.minimap"),x+side/2,y+side/2+16,UiKit.text());
  if(!captions.isEmpty()){g.pose().pushPose();g.pose().translate(x,y+side+2,0);UiHudSurface.panel(g,side,captions.size()*11+4,s.opacity);for(int n=0;n<captions.size();n++){String text=captions.get(n);float factor=Math.min(1,(side-8f)/Math.max(1,mc.font.width(text)));g.pose().pushPose();g.pose().translate(side/2f,3+n*11,0);g.pose().scale(factor,factor,1);g.drawString(mc.font,text,-mc.font.width(text)/2,0,UiKit.text(),false);g.pose().popPose();}g.pose().popPose();}
  drawn=now;
 }
 static List<MapMarker> nearestMarkers(double cx,double cz,String dimension,double zoom){long began=dev.abros.rivet.core.PerformanceMetrics.start();try{var s=MapSettings.INSTANCE;if(WorldMapClient.markers().isEmpty()||zoom<MapRenderSettings.INSTANCE.markerMinZoom)return List.of();var visibility=WorldMapClient.markerVisibility(true);return NearestSelection.select(WorldMapClient.markers(),64,m->m.dimension().equals(dimension)&&visibility.test(m)&&(!m.death()||s.deathMinimap),m->Math.hypot(m.x()-cx,m.z()-cz));}finally{dev.abros.rivet.core.PerformanceMetrics.end("client.minimap.select",began);}}
 static List<String> captions(){var out=new ArrayList<String>();var s=MapSettings.INSTANCE;var mc=Minecraft.getInstance();if(s.coordinates){out.add(dev.abros.rivet.network.DimensionLabels.name(mc.level==null?"minecraft:overworld":mc.level.dimension().location().toString()).getString()+" · "+(mc.player==null?"0, 64, 0":mc.player.getBlockX()+", "+mc.player.getBlockY()+", "+mc.player.getBlockZ()));}if(MapCaves.options().showTop()&&mc.level!=null&&MapCaves.layer(mc.level.dimension().location().toString()).cave())out.add(MapCaves.label(mc.level.dimension().location().toString()));if(s.biome)out.add(mc.level==null||mc.player==null?Client.text("ui.biome_885e508c"):mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(k->Component.translatable("biome."+k.location().getNamespace()+"."+k.location().getPath().replace('/','.')).getString()).orElse(Client.text("ui.biome_885e508c")));if(s.time){long minutes=mc.level==null?720:Math.floorMod(mc.level.getDayTime()+6000,24000)*1440/24000;out.add(String.format(Locale.ROOT,"%02d:%02d",minutes/60,minutes%60));}if(s.weather)out.add(mc.level==null||!mc.level.isRaining()?Client.text("ui.clear_ad1732f5"):mc.level.isThundering()?Client.text("ui.thunderstorm_3bec9ce3"):Client.text("ui.rain_aad03f21"));return out;}
 /** Frame geometry moves continuously; this world-aligned colour cache refreshes independently. */
 private static void surface(GuiGraphics g,int x,int y,int side,boolean round,float opacity){
  g.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,opacity);
  RenderSystem.setShader(GameRenderer::getPositionColorShader);var matrix=g.pose().last().pose();
  var border=Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN,DefaultVertexFormat.POSITION_COLOR);
  border.addVertex(matrix,x+side/2f,y+side/2f,0).setColor(UiKit.border());
  int segments=round?96:4;double radius=side/2d;
  for(int n=0;n<=segments;n++){var edge=edge(n,segments,radius,round);border.addVertex(matrix,(float)(x+radius+edge[0]),(float)(y+radius+edge[1]),0).setColor(UiKit.border());}
  BufferUploader.drawWithShader(border.buildOrThrow());
  if(texture==null||displayed==null||(!displayed.dimension().equals(textureDimension)||displayed.band()!=textureBand)){RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();return;}
  RenderSystem.setShaderTexture(0,TEXTURE);RenderSystem.setShader(GameRenderer::getPositionTexShader);
  var terrain=Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN,DefaultVertexFormat.POSITION_TEX);
  vertex(terrain,matrix,x+radius,y+radius,0,0);
  for(int n=0;n<=segments;n++){var edge=edge(n,segments,radius-1.5,round);vertex(terrain,matrix,x+radius+edge[0],y+radius+edge[1],edge[0],edge[1]);}
  BufferUploader.drawWithShader(terrain.buildOrThrow());RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();
 }
 private static double[] edge(int n,int segments,double r,boolean round){if(round){double angle=-2*Math.PI*n/segments;return new double[]{Math.cos(angle)*r,Math.sin(angle)*r};}return switch(n%4){case 0->new double[]{-r,-r};case 1->new double[]{-r,r};case 2->new double[]{r,r};default->new double[]{r,-r};};}
 private static void vertex(BufferBuilder builder,org.joml.Matrix4f matrix,double x,double y,double dx,double dy){builder.addVertex(matrix,(float)x,(float)y,0).setUv((float)((projection.worldX(dx,dy)-displayed.x())/(displayed.side()*displayed.step())),(float)((projection.worldZ(dx,dy)-displayed.z())/(displayed.side()*displayed.step())));}
 private static void update(){
  if(composing)return;
  var images=new HashMap<WorldMapClient.TileKey,MapTerrainCache.Image>();
  long signature=Objects.hash(UiKit.surface(),textureX,textureZ,textureSide,pixelStep,textureDimension,textureBand);
  if(WorldMapClient.ready()){
   var view=new MapViewport();view.center(textureX+textureSide*pixelStep/2d,textureZ+textureSide*pixelStep/2d);
   for(var key:WorldMapClient.visibleTiles(new MapLayer(textureDimension,textureBand),view,textureSide*pixelStep,textureSide*pixelStep)){
    var image=MapTerrainCache.image(key);if(image!=null){images.put(key,image);signature+=31L*key.hashCode()+image.revision();}
   }
  }
  if(signature==terrainSignature&&new Area(textureX,textureZ,textureSide,pixelStep,textureDimension,textureBand).equals(displayed))return;
  int detail=Math.min(4,Integer.numberOfTrailingZeros(pixelStep)),side=16>>detail;
  int startX=textureX,startZ=textureZ,size=textureSide,step=pixelStep,fallback=UiKit.surface();String dimension=textureDimension;int band=textureBand;
  long targetSignature=signature,owner=generation,styleEpoch=MapTerrainCache.styleEpoch();composing=true;
  if(!MapTerrainCache.work(()->{
   var image=new NativeImage(size,size,false);
   for(int py=0;py<size;py++)for(int px=0;px<size;px++){
    int wx=startX+px*step,wz=startZ+py*step;
    var terrain=images.get(new WorldMapClient.TileKey(dimension,Math.floorDiv(wx,16),Math.floorDiv(wz,16),band));
    int color=terrain==null?0:terrain.levels()[detail][(Math.floorMod(wz,16)>>detail)*side+(Math.floorMod(wx,16)>>detail)];
    if(color==0)color=fallback;image.setPixelRGBA(px,py,color&0xff00ff00|(color&255)<<16|(color>>>16&255));
   }
   Minecraft.getInstance().execute(()->{
    if(owner!=generation){image.close();return;}composing=false;
    if(styleEpoch!=MapTerrainCache.styleEpoch()||textureBand!=band||!textureDimension.equals(dimension)||fallback!=UiKit.surface()){image.close();nextUpdate=0;return;}
    // Publish complete pixels and their world placement together on the render thread.
    // A completed older area is still useful while the player keeps moving.
    if(texture==null||displayed.side()!=size){
     var replacement=new DynamicTexture(image);configure(replacement);
     if(texture!=null)Minecraft.getInstance().getTextureManager().release(TEXTURE);
     Minecraft.getInstance().getTextureManager().register(TEXTURE,replacement);texture=replacement;
    }else{texture.setPixels(image);texture.upload();configure(texture);}
    displayed=new Area(startX,startZ,size,step,dimension,band);terrainSignature=targetSignature;nextUpdate=0;
   });
  }))composing=false;

 }
 private static void configure(DynamicTexture value){value.setFilter(false,false);value.bind();/* Preserve sharp enlarged blocks, filter only subpixel terrain during rotation. */com.mojang.blaze3d.platform.GlStateManager._texParameter(3553,10241,9729);com.mojang.blaze3d.platform.GlStateManager._texParameter(3553,10242,33071);com.mojang.blaze3d.platform.GlStateManager._texParameter(3553,10243,33071);}
 static boolean contains(double x,double y){return bounds.width()>0&&net.minecraft.Util.getMillis()-drawn<250&&MinimapProjection.contains(x-bounds.x()-imageSide/2d,y-bounds.y()-imageSide/2d,imageSide/2d,MapSettings.INSTANCE.round);}
 static boolean click(double x,double y,Screen parent){if(visible()&&net.minecraft.Util.getMillis()-drawn<250&&hits.values().stream().noneMatch(v->Math.abs(v[0]-x)<=8*MapRenderSettings.INSTANCE.markerScale&&Math.abs(v[1]-y)<=8*MapRenderSettings.INSTANCE.markerScale)&&MapSharedOverlay.clickMinimap(parent,x,y))return true;if(!visible()||(!contains(x,y)&&hits.values().stream().noneMatch(v->Math.abs(v[0]-x)<=8*MapRenderSettings.INSTANCE.markerScale&&Math.abs(v[1]-y)<=8*MapRenderSettings.INSTANCE.markerScale)))return false;var mc=Minecraft.getInstance();var map=new WorldMapScreen(parent);mc.setScreen(map);hits.entrySet().stream().filter(e->Math.abs(e.getValue()[0]-x)<=8*MapRenderSettings.INSTANCE.markerScale&&Math.abs(e.getValue()[1]-y)<=8*MapRenderSettings.INSTANCE.markerScale).min(Comparator.comparingDouble(e->Math.hypot(e.getValue()[0]-x,e.getValue()[1]-y))).flatMap(e->WorldMapClient.markers().stream().filter(m->m.id().equals(e.getKey())).findFirst()).ifPresent(m->map.openMarker(m,false));return true;}
 static boolean scroll(double x,double y,double delta){if(!visible()||!contains(x,y))return false;var s=MapSettings.INSTANCE;s.zoom=HudSettings.clamp((float)(s.zoom*Math.pow(1.25,Math.clamp(delta,-4,4))),.25f,4);s.save();invalidate();return true;}
 private Minimap(){}
}
