package dev.abros.rivet.client;

import com.google.gson.JsonObject;
import dev.abros.rivet.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;

/** Direction-selected ring surface with native focus/narration targets for its portraits. */
final class UiRadialSector extends Button {
 private final JsonObject entry;private final boolean applied;
 UiRadialSector(int x,int y,int size,JsonObject entry,boolean applied,Runnable action){
  super(x,y,size,size,Component.literal(entry==null?Client.text("ui.default_skin_b097c1e1"):Json.str(entry,"name")),b->action.run(),DEFAULT_NARRATION);this.entry=entry;this.applied=applied;
 }
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){int size=Math.min(28,getWidth()-4);PlayerFaceRenderer.draw(g,UiSkinCard.appearance(entry),getX()+(getWidth()-size)/2,getY()+(getHeight()-size)/2,size);if(applied)UiKit.checkbox(g,getRight()-8,getBottom()-8,true,UiKit.accent());}
 static void draw(GuiGraphics g,int x,int y,int radius,int count,int selected){
  if(count<=0)return;
  for(int n=0;n<count;n++){double middle=2*Math.PI*n/count-Math.PI/2,half=Math.PI/count,gap=count==1?0:.018;double start=middle-half+gap,end=middle+half-gap;
   int fill=n==selected?UiTheme.mix(UiKit.surface(),UiKit.accent(),.25f):UiKit.surface(UiKit.Surface.CONTROL);
   UiKit.sector(g,x,y,radius*.48,radius,start,end,fill);
   UiKit.sector(g,x,y,radius-1.2,radius,start,end,n==selected?UiKit.accent():UiKit.border());
  }
 }
}
