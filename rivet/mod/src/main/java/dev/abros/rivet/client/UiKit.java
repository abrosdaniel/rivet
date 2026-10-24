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
  case HOVER->UiTheme.mix(UiPalette.foundation(2),text(),0.09f);
 };}
 /** Rectangles remain available for clipping, tracks and quantitative fills. */
 static void surface(GuiGraphics g,int x,int y,int w,int h,int color){if(w>0&&h>0)g.fill(x,y,x+w,y+h,color);}
 /** Integer scanlines keep native text and small GUI scales crisp; no textures or allocations. */
 static void rounded(GuiGraphics g,int x,int y,int w,int h,int radius,int color){
  if(w<=0||h<=0)return;
  int r=Math.max(0,Math.min(radius,Math.min(w,h)/2));
  surface(g,x,y+r,w,h-2*r,color);
  for(int row=0;row<r;row++){
   double dy=r-row-.5;int inset=(int)Math.ceil(r-Math.sqrt(r*r-dy*dy)-.5);
   surface(g,x+inset,y+row,w-2*inset,1,color);
   surface(g,x+inset,y+h-row-1,w-2*inset,1,color);
  }
 }
 static void ring(GuiGraphics g,int x,int y,int w,int h,int radius,int color){
  if(w<=0||h<=0)return;
  int r=Math.max(0,Math.min(radius,Math.min(w,h)/2));
  if(r==0){g.renderOutline(x,y,w,h,color);return;}
  for(int row=0;row<r;row++){
   double dy=r-row-.5;int outer=(int)Math.ceil(r-Math.sqrt(r*r-dy*dy)-.5);
   double innerRadius=r-1;int inner=row==0?w/2:(int)Math.ceil(r-Math.sqrt(Math.max(0,innerRadius*innerRadius-dy*dy))-.5);
   int length=row==0?w-2*outer:Math.max(1,inner-outer);
   surface(g,x+outer,y+row,length,1,color);surface(g,x+outer,y+h-row-1,length,1,color);
   if(row>0){surface(g,x+w-outer-length,y+row,length,1,color);surface(g,x+w-outer-length,y+h-row-1,length,1,color);}
  }
  surface(g,x,y+r,1,h-2*r,color);surface(g,x+w-1,y+r,1,h-2*r,color);
 }
 static void material(GuiGraphics g,int x,int y,int w,int h){
  rounded(g,x,y+3,w,h,8,0x24000000);
  rounded(g,x,y,w,h,8,AccessibilityScreen.background(surface()));
 }
 /** Floating controls over a busy canvas: quiet edge, soft elevation, opaque readable content. */
 static void floating(GuiGraphics g,int x,int y,int w,int h){
  rounded(g,x-1,y+4,w+2,h,9,0x18000000);
  material(g,x,y,w,h);
  if(AccessibilityScreen.highContrast())ring(g,x,y,w,h,8,border());
 }
 static void plate(GuiGraphics g,int x,int y,int w,int h,int color){rounded(g,x,y,w,h,6,color);}
 static void control(GuiGraphics g,int x,int y,int w,int h,int color){rounded(g,x,y,w,h,4,color);}
 static void divider(GuiGraphics g,int x,int y,int width){if(width>0)g.fill(x,y,x+width,y+1,border());}
 static void focus(GuiGraphics g,int x,int y,int width,int height){ring(g,x,y,width,height,4,accent());}
 static void checkbox(GuiGraphics g,int x,int y,boolean checked,int color){
  rounded(g,x,y,CHECK_SIZE,CHECK_SIZE,2,surface(Surface.INPUT));ring(g,x,y,CHECK_SIZE,CHECK_SIZE,2,checked?color:border());
  if(checked)UiIcons.draw(g,UiIcons.CHECK,x-1,y-1,color);
 }
 static String fit(net.minecraft.client.gui.Font font,String text,int width){if(width<=0)return "";if(text.isEmpty()||font.width(text)<=width)return text;if(width<font.width("…"))return "";return font.plainSubstrByWidth(text,Math.max(0,width-font.width("…")))+"…";}
 /** Shorten rich text without flattening its colours, styles or actions. */
 static net.minecraft.network.chat.Component fit(net.minecraft.client.gui.Font font,net.minecraft.network.chat.Component text,int width){
  if(width<=0)return net.minecraft.network.chat.Component.empty();
  if(font.width(text)<=width)return text;
  int ellipsis=font.width("…");if(width<ellipsis)return net.minecraft.network.chat.Component.empty();
  var out=net.minecraft.network.chat.Component.empty();
  font.substrByWidth(text,width-ellipsis).visit((style,part)->{out.append(net.minecraft.network.chat.Component.literal(part).withStyle(style));return java.util.Optional.empty();},net.minecraft.network.chat.Style.EMPTY);
  return out.append("…");
 }
}
