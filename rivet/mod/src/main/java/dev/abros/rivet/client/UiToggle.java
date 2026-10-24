package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Whole control is clickable; the track and caption both indicate the saved state. */
final class UiToggle extends Button {
 private final boolean enabled;
 UiToggle(String value,int x,int y,int width,boolean enabled,Runnable change){super(x,y,width,24,Component.literal(value),b->change.run(),DEFAULT_NARRATION);this.enabled=enabled;}
 boolean checked(){return enabled;}
 @Override protected void renderWidget(GuiGraphics g,int x,int y,float delta){int left=getX(),top=getY(),w=getWidth();UiKit.control(g,left,top,w,24,UiTheme.mix(UiKit.surface(UiKit.Surface.CONTROL),UiKit.surface(UiKit.Surface.HOVER),UiTheme.hover(this)));int track=left+w-34;UiKit.rounded(g,track,top+6,26,12,6,enabled?UiTheme.mix(UiKit.surface(),UiKit.accent(),0.45f):UiKit.border());int knob=track+(enabled?15:1);UiKit.rounded(g,knob,top+7,10,10,5,active?(enabled?UiKit.accent():UiPalette.color(0xFFABB5BE)):UiPalette.color(0xFF78828B));Ui.text(g,Minecraft.getInstance().font,UiKit.fit(Minecraft.getInstance().font,getMessage().getString(),Math.max(0,w-50)),left+8,top+8,active?UiKit.text():UiKit.muted(),false);if(UiTheme.keyboardFocus(this))UiKit.focus(g,left,top,w,24);}
}
