package dev.abros.rivet.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
/** Theme-aware scroll decorations for Rivet fields only; editing and input remain vanilla. */
final class UiMultiLineEditBox extends MultiLineEditBox {
 private final Font font;private final Component placeholder;private int limit=-1;private long focusAt;private boolean dragging;private double dragOffset;
 UiMultiLineEditBox(Font font,int x,int y,int w,int h,Component placeholder,Component title){super(font,x,y,Math.max(1,w-8),h,placeholder,title);setWidth(w);this.font=font;this.placeholder=placeholder;}
 // The supplied width owns both text and scrollbar; native text keeps its own inner gutter.
 private int thumbHeight(){return Math.min(height,Math.max(12,height*height/Math.max(1,getInnerHeight()+4)));}
 @Override protected boolean withinContentAreaPoint(double x,double y){return x>=getX()&&x<getX()+width-8&&y>=getY()&&y<getY()+height;}
 @Override public boolean mouseClicked(double x,double y,int button){
  if(!visible||!active||x<getX()||x>=getX()+width||y<getY()||y>=getY()+height)return false;
  if(button==0&&scrollbarVisible()&&x>=getX()+width-8){int thumb=thumbHeight();double top=getY()+scrollAmount()*(height-thumb)/Math.max(1,getMaxScrollAmount());dragOffset=y>=top&&y<top+thumb?y-top:thumb/2d;dragging=true;setFocused(true);seekScroll(y);return true;}
  return super.mouseClicked(x,y,button);
 }
 private void seekScroll(double y){setScrollAmount((y-getY()-dragOffset)*getMaxScrollAmount()/Math.max(1,height-thumbHeight()));}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging&&button==0){seekScroll(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
 @Override public boolean mouseReleased(double x,double y,int button){if(button==0)dragging=false;return super.mouseReleased(x,y,button);}
 record EditingState(int cursor,int anchor,double scroll){}
 EditingState editingState(){var field=((dev.abros.rivet.mixin.MultiLineFieldAccessor)(Object)this).rivet$textField();var selected=field.getSelected();return new EditingState(field.cursor(),field.cursor()==selected.beginIndex()?selected.endIndex():selected.beginIndex(),scrollAmount());}
 void restoreEditing(EditingState state){var field=((dev.abros.rivet.mixin.MultiLineFieldAccessor)(Object)this).rivet$textField();field.setSelecting(false);field.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE,Math.min(state.anchor(),getValue().length()));field.setSelecting(true);field.seekCursor(net.minecraft.client.gui.components.Whence.ABSOLUTE,Math.min(state.cursor(),getValue().length()));field.setSelecting(false);setScrollAmount(state.scroll());}
 @Override public void setCharacterLimit(int value){super.setCharacterLimit(value);limit=value;}
 @Override protected void renderBackground(GuiGraphics g){UiKit.surface(g,getX(),getY(),width,height,UiPalette.inputSurface());g.renderOutline(getX(),getY(),width,height,UiFields.outline(this));}
 @Override public void setFocused(boolean focused){super.setFocused(focused);if(focused)focusAt=net.minecraft.Util.getMillis();}
 @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta){
  var field=((dev.abros.rivet.mixin.MultiLineFieldAccessor)(Object)this).rivet$textField();String value=field.value();int left=getX()+innerPadding(),y=getY()+innerPadding();
  if(value.isEmpty()&&!isFocused()){Ui.wrap(g,font,placeholder,left,y,Math.max(1,width-totalInnerPadding()-8),UiKit.muted());return;}
  boolean caret=isFocused()&&(net.minecraft.Util.getMillis()-focusAt)/300%2==0;int cursor=field.cursor();
  for(var line:field.iterateLines()){
   if(withinContentAreaTopBottom(y,y+9)){String text=value.substring(line.beginIndex(),line.endIndex());Ui.text(g,font,text,left,y,active?UiKit.text():UiKit.muted(),false);
    if(caret&&cursor>=line.beginIndex()&&cursor<=line.endIndex()){int xx=left+font.width(value.substring(line.beginIndex(),cursor));g.fill(xx,y-1,xx+1,y+10,UiKit.accent());}
    if(field.hasSelection()){var selection=field.getSelected();int start=Math.max(line.beginIndex(),selection.beginIndex()),end=Math.min(line.endIndex(),selection.endIndex());if(end>start)g.fill(net.minecraft.client.renderer.RenderType.guiTextHighlight(),left+font.width(value.substring(line.beginIndex(),start)),y,left+font.width(value.substring(line.beginIndex(),end)),y+9,0xFF0000FF);}
   }y+=9;
  }
 }
 @Override protected void renderDecorations(GuiGraphics g){
  if(scrollbarVisible()){int h=thumbHeight(),y=getY()+(int)(scrollAmount()*(height-h)/Math.max(1,getMaxScrollAmount()));UiScrollbar.draw(g,new dev.abros.rivet.core.NativeLayout.Box(getX()+width-7,getY()+1,6,height-2),new dev.abros.rivet.core.NativeLayout.Box(getX()+width-7,y+1,6,Math.max(1,h-2)));}
  if(limit>=0){String text=getValue().length()+"/"+limit;Ui.text(g,font,text,getX()+width-font.width(text),getY()+height+4,UiKit.muted(),false);}
 }
}
