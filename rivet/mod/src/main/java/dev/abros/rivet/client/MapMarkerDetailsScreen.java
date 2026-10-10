package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
/** Small-view alternative for the same marker draft; Save remains in the card. */
final class MapMarkerDetailsScreen extends net.minecraft.client.gui.screens.Screen{
 private final WorldMapScreen parent;private final MapMarkerPanel panel;private UiDialog dialog;
 MapMarkerDetailsScreen(WorldMapScreen parent,MapMarkerPanel panel){super(Client.tr("map.details"));this.parent=parent;this.panel=panel;}
 @Override protected void init(){dialog=UiDialog.fit(width,height,300,170);var b=dialog.body();panel.extras(parent,b.x(),b.y(),b.width(),this::addRenderableWidget,this::rebuildWidgets);UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(dialog.footer().x(),dialog.footer().bottom()-20,dialog.footer().width(),20),this::addRenderableWidget,this::onClose);}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());});}
 @Override public void onClose(){parent.refreshPanels();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
