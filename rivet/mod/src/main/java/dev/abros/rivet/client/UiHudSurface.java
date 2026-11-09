package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
/** Shared rounded HUD surfaces; no scanline seams at fractional widget scales. */
final class UiHudSurface {
 static final int RADIUS=6;
 static void panel(GuiGraphics g,int w,int h,double opacity){panel(g,0,0,w,h,opacity);}
 static void panel(GuiGraphics g,int x,int y,int w,int h,double opacity){float a=(float)Math.max(0,Math.min(1,opacity));if(a==0)return;shadow(g,x,y,w,h,a);fill(g,x,y,w,h,RADIUS,(UiKit.surface()&0xFFFFFF)|(Math.round(255*a)<<24));if(AccessibilityScreen.highContrast())outline(g,x,y,w,h,RADIUS,(UiKit.border()&0xFFFFFF)|(Math.round(255*a)<<24));}
 static void shadow(GuiGraphics g,int x,int y,int w,int h,double opacity){float a=(float)Math.max(0,Math.min(1,opacity));fill(g,x,y+3,w,h,RADIUS,(Math.round(16*a)<<24));fill(g,x,y+1,w,h,RADIUS,(Math.round(20*a)<<24));}
 static void fill(GuiGraphics g,int x,int y,int w,int h,int radius,int color){UiKit.rounded(g,x,y,w,h,radius,color);}
 static void outline(GuiGraphics g,int x,int y,int w,int h,int radius,int color){UiKit.ring(g,x,y,w,h,radius,color);}
 private UiHudSurface(){}
}
