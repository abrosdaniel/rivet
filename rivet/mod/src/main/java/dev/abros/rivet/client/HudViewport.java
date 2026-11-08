package dev.abros.rivet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Render-thread-only preview scope; never changes the game window or saved options. */
final class HudViewport {
 private record View(int width,int height,int scale,int x,int y,float fit){}
 private static View preview;
 static int width(GuiGraphics g){return preview==null?g.guiWidth():preview.width();}
 static int height(GuiGraphics g){return preview==null?g.guiHeight():preview.height();}
 static double scale(){return preview==null?Minecraft.getInstance().getWindow().getGuiScale():preview.scale();}
 static void draw(int width,int height,int scale,int x,int y,float fit,Runnable render){
  if(width<320||height<240||scale<1)throw new IllegalArgumentException("Invalid HUD viewport");
  var previous=preview;preview=new View(width,height,scale,x,y,fit);
  try{render.run();}finally{preview=previous;}
 }
 static void scissor(GuiGraphics g,int x1,int y1,int x2,int y2){
  var view=preview;if(view==null){g.enableScissor(x1,y1,x2,y2);return;}
  g.enableScissor(view.x()+(int)Math.floor(x1*view.fit()),view.y()+(int)Math.floor(y1*view.fit()),view.x()+(int)Math.ceil(x2*view.fit()),view.y()+(int)Math.ceil(y2*view.fit()));
 }
 static boolean previewScreen(net.minecraft.client.gui.screens.Screen screen){
  for(int n=0;screen!=null&&n<16;n++){
   if(screen instanceof HudPreviewScreen||screen instanceof HudInteractionScreen edit&&edit.layoutEditing())return true;
   if(screen instanceof UiParentScreen child)screen=child.parentScreen();else return false;
  }return false;
 }
 private HudViewport(){}
}
