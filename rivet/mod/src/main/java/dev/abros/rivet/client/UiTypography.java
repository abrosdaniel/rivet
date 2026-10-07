package dev.abros.rivet.client;
import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
/** Crisp native typography: weight, secondary ink and spacing establish hierarchy. */
final class UiTypography {
 enum Role { PAGE, SECTION, TITLE, BODY, CAPTION }
 static void draw(GuiGraphics g,Font font,String text,int x,int y,int width,Role role){
  if(width<=0)return;
  var label=Component.literal(text);if(role==Role.PAGE||role==Role.SECTION||role==Role.TITLE)label.withStyle(ChatFormatting.BOLD);
  var fitted=font.substrByWidth(label,width);
  Ui.text(g,font,net.minecraft.locale.Language.getInstance().getVisualOrder(fitted),x,y,role==Role.CAPTION?UiKit.muted():UiKit.text(),false);
 }
 private UiTypography(){}
}
