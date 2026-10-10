package dev.abros.rivet.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;

final class MapTerritoriesScreen extends ScrollScreen {
 private final WorldMapScreen parent;private UiDialog dialog;private List<MapGroupClient.Group> snapshot=List.of();
 MapTerritoriesScreen(WorldMapScreen parent){super(Client.tr("map.territories"));this.parent=parent;}
 @Override protected void init(){dialog=UiDialog.fit(width,height,340,340);var b=dialog.body();snapshot=MapGroupClient.groups();var area=scrollForm(snapshot.size(),b,28);
  for(int i=firstRow;i<Math.min(snapshot.size(),firstRow+visibleRows);i++){var group=snapshot.get(i);String label=group.title()+" · "+(group.territory()==null?(group.manage()?Client.text("ui.create_territory_00bfc67b"):Client.text("ui.no_territory_set_1e491e3b")):dev.abros.rivet.network.DimensionLabels.name(group.territory().dimension()).getString());var button=addRenderableWidget(UiActions.button(Component.literal(UiKit.fit(font,label,area.content().width()-12)),UiActions.Tone.NORMAL,"",b1->{if(group.territory()==null)parent.drawTerritory(new MapTerritories.Draft(group,parent.selectedDimension()));else MapGroupClient.open(parent,group.id());}).bounds(b.x(),b.y()+(i-firstRow)*28,area.content().width(),20).build());button.active=group.manage()||group.territory()!=null;button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(label)));}
  UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(b.x(),dialog.footer().bottom()-20,b.width(),20),this::addRenderableWidget,this::onClose);
 }
 @Override public void tick(){if(!MapGroupClient.available()){onClose();return;}if(!snapshot.equals(MapGroupClient.groups()))rebuildWidgets();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());if(snapshot.isEmpty())Ui.text(g,font,UiKit.fit(font,MapGroupClient.status(),dialog.body().width()),dialog.body().x(),dialog.body().y()+8,UiKit.muted(),false);});}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
