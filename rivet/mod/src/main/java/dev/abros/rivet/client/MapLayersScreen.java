package dev.abros.rivet.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/** Shared visibility controls in one modal; source permissions remain authoritative. */
final class MapLayersScreen extends ScrollScreen {
 private final Screen parent;private UiDialog dialog;private java.util.List<MapLayers.Layer> available=java.util.List.of();
 MapLayersScreen(Screen parent){super(Client.tr("map.layers"));this.parent=parent;}
 @Override protected void init(){
  available=java.util.Arrays.stream(MapLayers.Layer.values()).filter(MapLayers::available).toList();dialog=UiDialog.fit(width,height,340,360);var b=dialog.body();
  var layers=java.util.Arrays.stream(MapLayers.Layer.values()).filter(MapLayers::available).toArray(MapLayers.Layer[]::new);var area=scrollForm(layers.length,b,28);
  for(int i=firstRow;i<Math.min(layers.length,firstRow+visibleRows);i++){var layer=layers[i];addRenderableWidget(new UiToggle(Client.tr(layer.key()).getString(),b.x(),b.y()+(i-firstRow)*28,area.content().width(),MapLayers.visible(layer,false),()->{MapLayers.toggle(layer,false);rebuildWidgets();}));}
  UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(b.x(),dialog.footer().bottom()-20,b.width(),20),this::addRenderableWidget,this::onClose);
 }
 @Override public void tick(){if(!available.equals(java.util.Arrays.stream(MapLayers.Layer.values()).filter(MapLayers::available).toList()))rebuildWidgets();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());});}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
