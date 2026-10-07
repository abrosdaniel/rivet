package dev.abros.rivet.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
/** Loading, no results, empty and failure share geometry, but keep distinct meaning. */
final class UiState {
 enum Kind { LOADING, EMPTY, FILTERED, ERROR }
 static void draw(GuiGraphics g,Font font,Kind kind,String title,String hint,int x,int y,int width,int bottom){
  if(width<24||bottom-y<24)return;
  width=Math.min(width,400);
  int color=kind==Kind.ERROR?UiPalette.color(0xFFEF7777):kind==Kind.LOADING?UiKit.muted():UiKit.accent();
  var lines=font.split(net.minecraft.network.chat.Component.literal(hint),Math.max(1,width-20));int panelHeight=Math.min(bottom-y,Math.max(40,28+12*lines.size()));
  UiKit.surface(g,x,y,width,panelHeight,UiTheme.mix(UiKit.surface(),color,0.035f));

  if(kind==Kind.ERROR)Ui.text(g,font,UiKit.fit(font,title,width-24),x+12,y+8,color,false);else UiTypography.draw(g,font,title,x+12,y+8,width-24,UiTypography.Role.TITLE);
  int lineY=y+22;for(var line:lines){if(lineY+9>y+panelHeight-4)break;Ui.text(g,font,line,x+12,lineY,UiKit.muted(),false);lineY+=12;}
 }
 private UiState(){}
}
