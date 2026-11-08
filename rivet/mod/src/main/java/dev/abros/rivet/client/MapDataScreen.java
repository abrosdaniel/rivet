package dev.abros.rivet.client;

import dev.abros.rivet.core.NativeLayout;
import dev.abros.rivet.core.map.MapStorage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Local storage inspection; terrain deletion is explicit and never races active map writers. */
final class MapDataScreen extends ScrollScreen implements UiParentScreen {
 private final Screen parent;private UiDialog dialog;private List<MapStorage.Entry> entries=List.of();private boolean loading,loaded,canClean;private String status="";
 MapDataScreen(Screen parent){super(Client.tr("map.data.title"));this.parent=parent;}
 public Screen parentScreen(){return parent;}
 private MapStorage storage(){return new MapStorage(minecraft.gameDirectory.toPath().resolve("rivet"));}
 private void load(){
  if(loading)return;loading=true;status=Client.text("map.data.loading");var storage=storage();
  if(!WorldMapClient.io(()->{List<MapStorage.Entry> result=List.of();Exception error=null;try{result=storage.scan();}catch(Exception ex){error=ex;}var rows=result;var failure=error;net.minecraft.client.Minecraft.getInstance().execute(()->{loading=false;loaded=true;entries=rows;status=failure==null?rows.isEmpty()?Client.text("map.data.empty"):"":Client.text("map.data.error");rebuildWidgets();});})){loading=false;status=Client.text("map.data.busy");}
 }
 private String label(MapStorage.Entry entry){return (entry.name().isBlank()?Client.text("map.data.world")+" "+entry.world().toString().substring(0,8):entry.name())+" · "+(entry.playerName().isBlank()?Client.text("map.data.player")+" "+entry.player().toString().substring(0,8):entry.playerName());}
 private static String size(long bytes){return String.format(Locale.ROOT,"%.1f MiB",bytes/1048576d);}
 private void clear(MapStorage.Entry entry){
  minecraft.setScreen(new UiConfirmDialog(yes->{minecraft.setScreen(this);if(!yes)return;
   if(!WorldMapClient.storageAvailable()){status=Client.text("map.data.disconnect");rebuildWidgets();return;}
   loading=true;status=Client.text("map.data.loading");var storage=storage();
   if(!WorldMapClient.maintainStorage(()->{storage.clearTerrain(entry.world(),entry.player());return null;},error->{loading=false;if(error==null){loaded=false;load();}else{status=Client.text("map.data.error");rebuildWidgets();}})){loading=false;status=Client.text("map.data.busy");}
   rebuildWidgets();
  },Client.tr("map.data.clear"),Client.tr("map.data.confirm").copy().append("\n"+label(entry))).dangerous().compact());
 }
 @Override protected void init(){
  canClean=WorldMapClient.storageAvailable();dialog=UiDialog.fit(width,height,460,350);var body=dialog.body();var area=scrollArea(entries.size(),new NativeLayout.Box(body.x(),body.y()+24,body.width(),Math.max(0,body.height()-24)),56).content();
  for(int n=firstRow;n<Math.min(entries.size(),firstRow+visibleRows);n++){
   var entry=entries.get(n);int y=area.y()+(n-firstRow)*56;
   var button=addRenderableWidget(UiActions.button(Client.tr("map.data.clear"),UiActions.Tone.DANGER,"",ignored->clear(entry)).bounds(area.x(),y+28,area.width(),20).build());
   button.active=!loading&&entry.terrainBytes()>0&&canClean;button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Client.tr("map.data.disconnect")));
  }
  var footer=dialog.footer();int half=(footer.width()-8)/2;
  UiActions.command(UiActions.Command.REFRESH,new NativeLayout.Box(footer.x(),footer.bottom()-20,half,20),this::addRenderableWidget,this::load).active=!loading;
  UiActions.close(new NativeLayout.Box(footer.x()+half+8,footer.bottom()-20,half,20),this::addRenderableWidget,this::onClose);
  if(!loaded&&!loading)load();
 }
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{
  super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());var body=dialog.body();
  Ui.text(g,font,UiKit.fit(font,status.isEmpty()?Client.text("map.data.hint"):status,body.width()),body.x(),body.y(),UiKit.muted(),false);
  for(int n=firstRow;n<Math.min(entries.size(),firstRow+visibleRows);n++){var entry=entries.get(n);int top=body.y()+24+(n-firstRow)*56;
   Ui.text(g,font,UiKit.fit(font,label(entry),body.width()-8),body.x(),top,UiKit.text(),false);
   Ui.text(g,font,UiKit.fit(font,Client.text("map.data.total")+" "+size(entry.bytes())+" · "+Client.text("map.data.terrain")+" "+size(entry.terrainBytes()),body.width()-8),body.x(),top+12,UiKit.muted(),false);
  }
 });}
 @Override public void tick(){boolean available=WorldMapClient.storageAvailable();if(canClean!=available){canClean=available;rebuildWidgets();}}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
