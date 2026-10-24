package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
/** One checkable row for task stages, ballots and other explicit selections. */
final class UiChoiceRow extends Button {
 private final boolean checked;private final double progress;private final int accent;
 UiChoiceRow(int x,int y,int width,String label,boolean checked,double progress,int accent,Runnable change){
  super(x,y,width,UiKit.CONTROL_HEIGHT,Component.literal(label),b->change.run(),DEFAULT_NARRATION);
  this.checked=checked;this.progress=progress<0?-1:Math.min(1,progress);this.accent=accent;
  if(Minecraft.getInstance().font.width(label)>width-32)setTooltip(Tooltip.create(Component.literal(label)));
 }
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){
  int accent=active?UiPalette.color(this.accent):UiKit.muted();int x=getX(),y=getY(),w=getWidth();float hover=UiTheme.hover(this);
  UiKit.control(g,x,y,w,getHeight(),UiTheme.mix(UiKit.surface(UiKit.Surface.CONTROL),accent,checked?0.22f:0.12f*hover));
  if(progress>=0){int filled=(int)(w*progress);g.enableScissor(x,y,x+filled,y+getHeight());UiKit.control(g,x,y,w,getHeight(),UiTheme.mix(UiKit.surface(),accent,0.35f));g.disableScissor();}
  UiKit.checkbox(g,x+UiKit.INSET,y+5,checked,accent);
  var font=Minecraft.getInstance().font;Ui.text(g,font,UiKit.fit(font,getMessage().getString(),w-32),x+26,y+6,active?UiKit.text():UiKit.muted(),false);
  if(UiTheme.keyboardFocus(this))UiKit.focus(g,x,y,w,getHeight());
 }
}
