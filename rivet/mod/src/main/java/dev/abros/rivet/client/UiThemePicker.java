package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Palette samples use real tokens and leave the selected palette untouched while rendering. */
final class UiThemePicker extends ScrollScreen {
 private final Screen parent;private UiDialog dialog;private int cols;
 UiThemePicker(Screen parent){super(Client.tr("ui.interface_themes_5bca3557"));this.parent=parent;}
 @Override protected void init(){dialog=UiDialog.fit(width,height,500,340);var b=dialog.body();cols=Math.max(1,Math.min(3,b.width()/145));var geometry=scrollArea((UiPalette.names().size()+cols-1)/cols,b,74);var tracks=new dev.abros.rivet.core.NativeLayout.Track[cols];java.util.Arrays.fill(tracks,dev.abros.rivet.core.NativeLayout.Track.flex(1));var columns=dev.abros.rivet.core.NativeLayout.row(geometry.content(),6,tracks);for(int row=firstRow;row<Math.min((UiPalette.names().size()+cols-1)/cols,firstRow+visibleRows);row++)for(int col=0;col<cols;col++){int theme=row*cols+col;if(theme>=UiPalette.names().size())break;addRenderableWidget(new ThemeCard(columns.get(col).x(),b.y()+(row-firstRow)*74,columns.get(col).width(),theme));}UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(dialog.footer().right()-96,dialog.footer().bottom()-20,96,20),this::addRenderableWidget,this::onClose);}
 private final class ThemeCard extends Button {
  private final int theme;
  ThemeCard(int x,int y,int w,int theme){super(x,y,w,68,Component.literal(UiPalette.names().get(theme)),button->{try{UiPalette.select(theme);minecraft.setScreen(parent);}catch(Exception ex){Client.failure(ex);}},DEFAULT_NARRATION);this.theme=theme;setTooltip(net.minecraft.client.gui.components.Tooltip.create(getMessage()));}
  @Override protected void renderWidget(GuiGraphics g,int mx,int my,float d){int selected=UiPalette.selected();try{UiPalette.preview(theme);int x=getX(),y=getY(),w=getWidth();UiKit.surface(g,x,y,w,68,UiKit.surface());g.renderOutline(x,y,w,68,selected==theme||isHoveredOrFocused()?UiKit.accent():UiPalette.outline());Ui.text(g,font,UiKit.fit(font,getMessage().getString(),w-14),x+7,y+7,UiKit.text(),false);g.fill(x+7,y+24,x+w-7,y+25,UiKit.accent());Ui.text(g,font,Client.text("ui.text_description_ca6edf20"),x+7,y+32,UiKit.muted(),false);UiKit.surface(g,x+7,y+48,w-14,14,UiPalette.insetSurface());Ui.text(g,font,selected==theme?Client.text("ui.selected_8fc928c4"):Client.text("ui.select_fe4b0c80"),x+12,y+51,UiKit.accent(),false);}finally{UiPalette.preview(selected);}}
 }
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());});}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
