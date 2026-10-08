package dev.abros.rivet.client;
import dev.abros.rivet.core.pack.PackManifest;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** Optional components belong to the server publication, not a separate catalog. */
final class ServerPackScreen extends ScrollScreen {
 private final Screen parent;private final Set<String> selected;private final Consumer<Set<String>> apply;private UiDialog dialog;
 private record Row(String id,String label,String description,boolean required) {}
 private final List<Row> rows;
 ServerPackScreen(Screen parent,PackManifest manifest,Set<String> selected,Consumer<Set<String>> apply){super(Component.literal("Сборка сервера"));this.parent=parent;this.selected=new HashSet<>(selected);this.apply=apply;
  var items=new ArrayList<Row>();
  for(var c:manifest.components())items.add(new Row(c.id(),c.name(),c.description(),false));
  for(var file:manifest.files())if(file.component().isEmpty()){
   String name=file.path().substring(file.path().lastIndexOf('/')+1);
   items.add(new Row("",name,"Обязательный файл: "+file.path(),true));
  }
  rows=List.copyOf(items);
 }
 protected void init(){
  dialog=UiDialog.fit(width,height,460,320);
  var body=dialog.body();var area=scrollArea(rows.size(),body,24);
  for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++){
   var row=rows.get(i);int y=body.y()+(i-firstRow)*24;
   var choice=new UiChoiceRow(area.content().x(),y,area.content().width(),row.label(),row.required()||selected.contains(row.id()),-1,UiKit.ACCENT,()->{
    if(row.required())return;
    if(!selected.remove(row.id()))selected.add(row.id());rebuildWidgets();
   });
   choice.active=!row.required();
   choice.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(row.description().isBlank()?row.label():row.description())));
   addRenderableWidget(choice);
  }
  var f=dialog.footer();UiActions.row(new dev.abros.rivet.core.NativeLayout.Box(f.x(),f.bottom()-20,f.width(),20),this::addRenderableWidget,UiActions.primary("Проверить изменения",()->apply.accept(Set.copyOf(selected)),true),UiActions.action("Отмена",this::onClose,true));
 }
 public void renderBackground(GuiGraphics g,int x,int y,float d){var f=dialog.frame();UiDialog.surface(g,f.x(),f.y(),f.width(),f.height());}
 public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);var h=dialog.header();UiHeading.dialog(g,font,title,h.x(),dialog.frame().y(),h.width());if(rows.isEmpty()){var b=dialog.body();g.drawWordWrap(font,Component.literal("В сборке нет файлов."),b.x(),b.y(),b.width(),UiPalette.color(0xEEEEEE));}});}
 public void onClose(){minecraft.setScreen(parent);}public boolean isPauseScreen(){return false;}
}
