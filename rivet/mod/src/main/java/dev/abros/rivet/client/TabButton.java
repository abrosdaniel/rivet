package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** A selected tab is a location indicator, distinct from an unavailable action. */
class TabButton extends Button {
 private final boolean selected;
 TabButton(int x,int y,int w,String label,boolean selected,Runnable action){super(x,y,w,20,Component.literal(label),b->action.run(),DEFAULT_NARRATION);this.selected=selected;active=!selected;}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){int x=getX(),y=getY(),w=getWidth();float t=UiTheme.hover(this);UiKit.control(g,x,y,w,20,UiKit.surface(UiKit.Surface.INPUT));if(selected||t>0)UiKit.rounded(g,x+2,y+2,w-4,16,3,selected?UiKit.surface(UiKit.Surface.CONTROL):UiTheme.mix(UiKit.surface(UiKit.Surface.INPUT),UiKit.surface(UiKit.Surface.HOVER),t));if(selected)UiKit.ring(g,x+2,y+2,w-4,16,3,UiKit.border());var font=Minecraft.getInstance().font;String label=UiKit.fit(font,getMessage().getString(),w-12);Ui.text(g,font,label,x+(w-font.width(label))/2,y+6,selected?UiKit.text():UiKit.muted(),false);if(UiTheme.keyboardFocus(this))UiKit.focus(g,x,y,w,20);}
}
