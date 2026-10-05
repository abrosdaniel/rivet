package dev.abros.rivet.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
/** Loading, no results, empty and failure share geometry, but keep distinct meaning. */
final class UiState {
 enum Kind { LOADING, EMPTY, FILTERED, ERROR }
 static void draw(GuiGraphics g,Font font,Kind kind,String title,String hint,int x,int y,int width,int bottom){
  if(width<24||bottom-y<24)return;
  int color=kind==Kind.ERROR?UiPalette.color(0xFFEF7777):kind==Kind.LOADING?UiKit.muted():UiKit.accent();
  var lines=font.split(net.minecraft.network.chat.Component.literal(hint),Math.max(1,width-20));int panelHeight=Math.min(bottom-y,Math.max(40,28+12*lines.size()));
  UiKit.surface(g,x,y,width,panelHeight,UiTheme.mix(UiKit.surface(),color,0.035f));
  g.fill(x,y+4,x+2,Math.min(bottom,y+44),color);
  Ui.text(g,font,UiKit.fit(font,title,width-20),x+10,y+6,kind==Kind.ERROR?color:UiKit.text(),false);
  int lineY=y+22;for(var line:lines){if(lineY+9>y+panelHeight-4)break;Ui.text(g,font,line,x+10,lineY,UiKit.muted(),false);lineY+=12;}
 }
 private UiState(){}
}
