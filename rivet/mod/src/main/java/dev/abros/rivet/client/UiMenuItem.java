package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Compact menu row; text aligns with adjacent choices rather than moving with its length. */
final class UiMenuItem extends Button {
 private final boolean selected,danger;private final String label;
 UiMenuItem(String label,int x,int y,int width,boolean selected,boolean danger,Runnable choose){super(x,y,width,20,Component.literal((selected?"✓ ":"")+label),b->choose.run(),DEFAULT_NARRATION);this.label=label;this.selected=selected;this.danger=danger;}
 @Override protected void renderWidget(GuiGraphics g,int x,int y,float delta){int left=getX(),top=getY(),w=getWidth();float hover=UiTheme.hover(this);UiKit.control(g,left,top,w,20,UiTheme.mix(selected?UiKit.surface(UiKit.Surface.SELECTED):UiKit.surface(),danger?UiTheme.mix(UiKit.surface(),UiPalette.color(0xFFEF9090),.15f):UiKit.surface(UiKit.Surface.HOVER),hover));int color=danger?UiPalette.color(0xFFEF9090):selected?UiKit.accent():UiPalette.color(0xFFE0E9EE);if(selected)UiIcons.draw(g,UiIcons.CHECK,left+4,top+4,color);var font=Minecraft.getInstance().font;String shown=font.width(label)>w-28?font.plainSubstrByWidth(label,w-28-font.width("…"))+"…":label;Ui.text(g,font,shown,left+22,top+6,color,false);if(UiTheme.keyboardFocus(this))UiKit.focus(g,left,top,w,20);}
}
