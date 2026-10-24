package dev.abros.rivet.client;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.GameRenderer;

/** Direction-selected ring surface with native focus/narration targets for its portraits. */
final class UiRadialSector extends Button {
 private final JsonObject entry;private final boolean applied;
 UiRadialSector(int x,int y,int size,JsonObject entry,boolean applied,Runnable action){
  super(x,y,size,size,Component.literal(entry==null?Client.text("ui.default_skin_b097c1e1"):Json.str(entry,"name")),b->action.run(),DEFAULT_NARRATION);this.entry=entry;this.applied=applied;
 }
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){int size=Math.min(28,getWidth()-4);PlayerFaceRenderer.draw(g,UiSkinCard.appearance(entry),getX()+(getWidth()-size)/2,getY()+(getHeight()-size)/2,size);if(applied)UiKit.checkbox(g,getRight()-8,getBottom()-8,true,UiKit.accent());}
 static void draw(GuiGraphics g,int x,int y,int radius,int count,int selected){
  if(count==0)return;g.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShader(GameRenderer::getPositionColorShader);
  var vertices=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR);var matrix=g.pose().last().pose();
  for(int n=0;n<count;n++){double middle=2*Math.PI*n/count-Math.PI/2,half=Math.PI/count,gap=count==1?0:.018;double start=middle-half+gap,end=middle+half-gap;
   int fill=n==selected?UiTheme.mix(UiKit.surface(),UiKit.accent(),.25f):UiKit.surface(UiKit.Surface.CONTROL);
   arc(vertices,matrix,x,y,radius*.48,radius,start,end,fill);
   arc(vertices,matrix,x,y,radius-1.2,radius,start,end,n==selected?UiKit.accent():UiKit.border());
  }
  BufferUploader.drawWithShader(vertices.buildOrThrow());RenderSystem.disableBlend();
 }
 private static void arc(BufferBuilder b,org.joml.Matrix4f matrix,int x,int y,double inner,double outer,double start,double end,int color){int steps=Math.max(2,(int)Math.ceil((end-start)*outer/4));for(int i=0;i<steps;i++){double a=start+(end-start)*i/steps,c=start+(end-start)*(i+1)/steps;vertex(b,matrix,x,y,outer,a,color);vertex(b,matrix,x,y,inner,a,color);vertex(b,matrix,x,y,inner,c,color);vertex(b,matrix,x,y,outer,c,color);}}
 private static void vertex(BufferBuilder b,org.joml.Matrix4f matrix,int x,int y,double radius,double angle,int color){b.addVertex(matrix,x+(float)(Math.cos(angle)*radius),y+(float)(Math.sin(angle)*radius),0).setColor(color);}
}
