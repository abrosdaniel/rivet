package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
final class PasswordVisibilityButton extends Button {
 private final PasswordBox field;
 PasswordVisibilityButton(int x,int y,PasswordBox field){super(x,y,22,20,Client.tr("ui.show_password_07fefc08"),b->{field.toggleVisibility();b.setMessage(Component.literal(field.revealed()?Client.text("ui.hide_password_8992c9df"):Client.text("ui.show_password_07fefc08")));},DEFAULT_NARRATION);this.field=field;}
 @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){UiKit.control(g,getX(),getY(),22,20,UiTheme.mix(UiKit.surface(UiKit.Surface.CONTROL),UiKit.surface(UiKit.Surface.HOVER),active?UiTheme.hover(this):0));int c=active?UiKit.text():UiKit.muted();UiIcons.draw(g,field.revealed()?UiIcons.EYE:UiIcons.EYE_OFF,getX()+5,getY()+4,c);if(UiTheme.keyboardFocus(this))UiKit.focus(g,getX(),getY(),22,20);}
}
