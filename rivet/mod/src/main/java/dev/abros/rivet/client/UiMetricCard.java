package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.network.chat.Component;
/** Read-only dashboard measure, visually distinct from navigation cards. */
final class UiMetricCard extends net.minecraft.client.gui.components.Button {
 private final String label,value;
 UiMetricCard(int x,int y,int width,String label,String value){this(x,y,width,label,value,null);}
 UiMetricCard(int x,int y,int width,String label,String value,Runnable action){this(x,y,width,label,value,action,56);}
 static UiMetricCard compact(int x,int y,int width,String label,String value){return new UiMetricCard(x,y,width,label,value,null,38);}
 static UiMetricCard compact(int x,int y,int width,String label,String value,Runnable action){return new UiMetricCard(x,y,width,label,value,action,38);}
 private UiMetricCard(int x,int y,int width,String label,String value,Runnable action,int height){super(x,y,width,height,Component.literal(label+": "+value),b->{if(action!=null)action.run();},DEFAULT_NARRATION);this.label=label;this.value=value;active=action!=null;setTooltip(net.minecraft.client.gui.components.Tooltip.create(getMessage()));}
 @Override public void updateWidgetNarration(NarrationElementOutput output){output.add(NarratedElementType.TITLE,getMessage());}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){int x=getX(),y=getY(),w=getWidth();var font=Minecraft.getInstance().font;UiKit.surface(g,x,y,w,getHeight(),active?UiTheme.mix(UiKit.surface(),UiKit.surface(UiKit.Surface.HOVER),UiTheme.hover(this)):UiKit.surface());Ui.text(g,font,UiKit.fit(font,label,w-16),x+8,y+(getHeight()==38?6:9),UiKit.muted(),false);Ui.text(g,font,UiKit.fit(font,value,w-16),x+8,y+(getHeight()==38?22:30),UiKit.text(),false);if(isFocused()&&active)UiKit.focus(g,x,y,w,getHeight());}
}
