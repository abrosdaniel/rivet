package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
/** Shared grip for list ordering; the trailing scrollbar remains a separate target. */
final class UiDragHandle {
 static final int WIDTH=24;
 static void draw(GuiGraphics g,int x,int y,boolean dragging){
  if(dragging)UiKit.surface(g,x,y,WIDTH,24,UiTheme.mix(UiKit.surface(),UiKit.accent(),.15f));
  int ink=dragging?UiKit.accent():UiKit.muted();
  for(int row=0;row<3;row++)for(int col=0;col<2;col++)g.fill(x+8+col*4,y+6+row*4,x+10+col*4,y+8+row*4,ink);
 }
 private UiDragHandle(){}
}
