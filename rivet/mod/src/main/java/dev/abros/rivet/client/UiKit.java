package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
/** Rivet design system. Geometry in GUI pixels; colours are semantic and theme-aware. */
final class UiKit {
 static final int ACCENT=0xFF83C6C4;
 static final int CONTROL_HEIGHT=20, ROW_STRIDE=24, GAP=4, INSET=8, CHECK_SIZE=10;
 static final int SPACE_SMALL=4, SPACE_MEDIUM=8, SPACE_LARGE=12, SPACE_SECTION=16;
 enum Surface { CANVAS, PANEL, CONTROL, INPUT, SELECTED, HOVER }
 private UiKit(){}
 static int accent(){return UiPalette.color(0xFFE2BE75);}
 static int onAccent(){int color=accent();double luminance=0;for(int shift:new int[]{16,8,0}){double v=((color>>>shift)&255)/255d;v=v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);luminance+=v*(shift==16?.2126:shift==8?.7152:.0722);}return luminance>.179?0xFF111111:0xFFFFFFFF;}
 static int text(){return AccessibilityScreen.foreground(UiPalette.color(0xFFE0E9EE));}
 static int muted(){return UiPalette.color(0xFFA4B5C0);}
 static int border(){return UiTheme.mix(surface(),muted(),AccessibilityScreen.highContrast()?0.5f:0.18f);}
 static int surface(){return UiPalette.foundation(1);}
 static int surface(Surface role){return switch(role){
  case CANVAS->UiPalette.foundation(0);
  case PANEL->surface();
  case CONTROL->UiPalette.foundation(2);
  case INPUT->UiPalette.foundation(3);
  case SELECTED->UiTheme.mix(surface(),accent(),0.15f);
  case HOVER->UiTheme.mix(surface(),text(),0.09f);
 };}
 /** Flat surfaces have no ornamental corners or simulated bevels. */
 static void surface(GuiGraphics g,int x,int y,int w,int h,int color){if(w>0&&h>0)g.fill(x,y,x+w,y+h,color);}
 static void material(GuiGraphics g,int x,int y,int w,int h){surface(g,x,y,w,h,AccessibilityScreen.background(surface()));}
 static void plate(GuiGraphics g,int x,int y,int w,int h,int color){surface(g,x,y,w,h,color);}
 static void divider(GuiGraphics g,int x,int y,int width){if(width>0)g.fill(x,y,x+width,y+1,border());}
 static void focus(GuiGraphics g,int x,int y,int width,int height){g.renderOutline(x,y,width,height,accent());}
 static void checkbox(GuiGraphics g,int x,int y,boolean checked,int color){
  g.fill(x,y,x+CHECK_SIZE,y+CHECK_SIZE,surface(Surface.INPUT));g.renderOutline(x,y,CHECK_SIZE,CHECK_SIZE,checked?color:border());
  if(checked)UiIcons.draw(g,UiIcons.CHECK,x-1,y-1,color);
 }
 static String fit(net.minecraft.client.gui.Font font,String text,int width){if(width<=0)return "";if(text.isEmpty()||font.width(text)<=width)return text;if(width<font.width("…"))return "";return font.plainSubstrByWidth(text,Math.max(0,width-font.width("…")))+"…";}
}
