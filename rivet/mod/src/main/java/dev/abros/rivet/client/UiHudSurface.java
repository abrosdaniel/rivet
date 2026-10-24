package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
/** Rounded native HUD geometry; each translucent pixel is painted only once. */
final class UiHudSurface {
 static final int RADIUS=6;
 static void panel(GuiGraphics g,int w,int h,double opacity){panel(g,0,0,w,h,opacity);}
 static void panel(GuiGraphics g,int x,int y,int w,int h,double opacity){float a=(float)Math.max(0,Math.min(1,opacity));if(a==0)return;shadow(g,x,y,w,h,a);fill(g,x,y,w,h,RADIUS,(UiKit.surface()&0xFFFFFF)|(Math.round(255*a)<<24));if(AccessibilityScreen.highContrast())outline(g,x,y,w,h,RADIUS,(UiKit.border()&0xFFFFFF)|(Math.round(255*a)<<24));}
 static void shadow(GuiGraphics g,int x,int y,int w,int h,double opacity){float a=(float)Math.max(0,Math.min(1,opacity));fill(g,x,y+3,w,h,RADIUS,(Math.round(16*a)<<24));fill(g,x,y+1,w,h,RADIUS,(Math.round(20*a)<<24));}
 static void fill(GuiGraphics g,int x,int y,int w,int h,int radius,int color){int r=Math.min(radius,Math.min(w,h)/2);for(int row=0;row<h;row++){int inset=inset(row,h,r);g.fill(x+inset,y+row,x+w-inset,y+row+1,color);}}
 static void outline(GuiGraphics g,int x,int y,int w,int h,int radius,int color){int r=Math.min(radius,Math.min(w,h)/2);for(int row=0;row<h;row++){int outer=inset(row,h,r);int inner=row==0||row==h-1?w/2:1+inset(row-1,h-2,Math.max(0,r-1));if(row==0||row==h-1)g.fill(x+outer,y+row,x+w-outer,y+row+1,color);else{g.fill(x+outer,y+row,x+inner,y+row+1,color);g.fill(x+w-inner,y+row,x+w-outer,y+row+1,color);}}}
 private static int inset(int row,int h,int r){if(r==0)return 0;double d=row<r?r-row-.5:row>=h-r?row-(h-r)+.5:0;return d==0?0:Math.max(0,(int)Math.ceil(r-Math.sqrt(r*r-d*d)-.5));}
 private UiHudSurface(){}
}
