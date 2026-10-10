package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
final class PasswordVisibilityButton extends Button {
 private final PasswordBox field;
 PasswordVisibilityButton(int x,int y,PasswordBox field){super(x,y,22,20,Client.tr("ui.show_password_07fefc08"),b->{field.toggleVisibility();b.setMessage(Component.literal(field.revealed()?Client.text("ui.hide_password_8992c9df"):Client.text("ui.show_password_07fefc08")));},DEFAULT_NARRATION);this.field=field;}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){UiKit.plate(g,getX(),getY(),22,20,isHoveredOrFocused()?UiPalette.color(0xFF405365):UiPalette.color(0xFF263440));int c=active?UiPalette.color(0xFFE0E8F0):UiPalette.color(0xFF677580);g.fill(getX()+5,getY()+7,getX()+17,getY()+8,c);g.fill(getX()+3,getY()+8,getX()+5,getY()+12,c);g.fill(getX()+17,getY()+8,getX()+19,getY()+12,c);g.fill(getX()+5,getY()+12,getX()+17,getY()+13,c);g.fill(getX()+9,getY()+8,getX()+13,getY()+12,c);if(!field.revealed())for(int i=0;i<14;i++)g.fill(getX()+4+i,getY()+15-i/2,getX()+5+i,getY()+16-i/2,c);if(isFocused())g.renderOutline(getX(),getY(),22,20,c);}
}
