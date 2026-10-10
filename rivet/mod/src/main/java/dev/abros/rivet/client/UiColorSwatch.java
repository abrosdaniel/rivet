package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
/** A colour is a visible swatch, with a redundant selection tick and accessible name. */
final class UiColorSwatch extends Button {
 private final int colour;private final boolean selected;
 UiColorSwatch(int x,int y,int colour,Component label,boolean selected,Runnable choose){super(x,y,18,18,label,b->choose.run(),DEFAULT_NARRATION);this.colour=colour;this.selected=selected;setTooltip(Tooltip.create(label));}
 @Override protected void renderWidget(GuiGraphics g,int x,int y,float delta){g.fill(getX(),getY(),getX()+18,getY()+18,UiKit.border());g.fill(getX()+2,getY()+2,getX()+16,getY()+16,colour);if(selected){int light=((colour>>16&255)*299+(colour>>8&255)*587+(colour&255)*114)/1000;UiIcons.draw(g,UiIcons.CHECK,getX()+3,getY()+3,light>150?0xff10151a:0xffffffff);}if(selected||isHoveredOrFocused())g.renderOutline(getX(),getY(),18,18,UiKit.accent());}
}
