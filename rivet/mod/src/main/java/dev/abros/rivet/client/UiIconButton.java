package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Shared tool action: an accessible label and a glyph over either native or Rivet controls. */
final class UiIconButton extends Button {
 private final String icon;
 UiIconButton(Component label,String icon,int x,int y,OnPress press){super(x,y,20,20,label,press,DEFAULT_NARRATION);this.icon=icon;}
 @Override protected void renderWidget(GuiGraphics g,int x,int y,float partial){
  if(UiTheme.stylesButtons(Minecraft.getInstance().screen,this)){super.renderWidget(g,x,y,partial);return;}
  var label=getMessage();setMessage(Component.empty());try{super.renderWidget(g,x,y,partial);}finally{setMessage(label);}
  UiIcons.draw(g,icon,getX()+(getWidth()-12)/2,getY()+(getHeight()-12)/2,active?0xFFFFFFFF:0xFFA0A0A0);
 }
}
