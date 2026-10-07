package dev.abros.rivet.client;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import dev.abros.rivet.core.NativeLayout;
/** Shared centered brand in the application header. */
final class UiBrand {
 static final int HEIGHT=14,GAP=5;
 static final float TEXT_SCALE=1.0f;
 // The bitmap letters occupy seven pixels; lineHeight also includes line spacing.
 static final int TEXT_INK_HEIGHT=7;
 static int width(Font font){return HEIGHT+GAP+(int)Math.ceil(font.width("Rivet")*TEXT_SCALE);}
 static int left(Font font,NativeLayout.Box header){return header.x()+(header.width()-width(font))/2;}
 static void draw(GuiGraphics graphics,Font font,NativeLayout.Box header){
  int x=left(font,header);
  int y=header.y()+(header.height()-HEIGHT)/2;
  Branding.icon(graphics,x,y,HEIGHT);
  graphics.pose().pushPose();
  graphics.pose().translate(x+HEIGHT+GAP,y+Math.round((HEIGHT-TEXT_INK_HEIGHT*TEXT_SCALE)/2f),0);
  graphics.pose().scale(TEXT_SCALE,TEXT_SCALE,1);
  Ui.text(graphics,font,"Rivet",0,0,UiKit.text(),false);
  graphics.pose().popPose();
 }
 private UiBrand(){}
}
