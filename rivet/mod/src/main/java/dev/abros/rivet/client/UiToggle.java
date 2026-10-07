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
 @Override protected void renderWidget(GuiGraphics g,int x,int y,float delta){int left=getX(),top=getY(),w=getWidth();g.fill(left,top,left+w,top+24,active&&isHoveredOrFocused()?UiKit.surface(UiKit.Surface.HOVER):UiKit.surface(UiKit.Surface.CONTROL));int track=left+w-34;g.fill(track,top+6,track+26,top+18,enabled?UiTheme.mix(UiKit.surface(),UiKit.accent(),0.45f):UiKit.border());int knob=track+(enabled?15:1);g.fill(knob,top+7,knob+10,top+17,active?(enabled?UiKit.accent():UiPalette.color(0xFFABB5BE)):UiPalette.color(0xFF78828B));Ui.text(g,Minecraft.getInstance().font,UiKit.fit(Minecraft.getInstance().font,getMessage().getString(),Math.max(0,w-50)),left+8,top+8,active?UiKit.text():UiKit.muted(),false);if(isFocused())g.renderOutline(left,top,w,24,UiKit.accent());}
}
