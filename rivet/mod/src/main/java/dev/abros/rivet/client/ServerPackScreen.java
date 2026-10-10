package dev.abros.rivet.client;
import dev.abros.rivet.core.pack.PackManifest;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** Optional components belong to the server publication, not a separate catalog. */
final class ServerPackScreen extends ScrollScreen {
 private final Screen parent;private final Set<String> selected;private final Consumer<Set<String>> apply;private UiDialog dialog;private int rowStride;
 private record Row(String id,String label,String description,boolean required) {}
 private final List<Row> rows;
 ServerPackScreen(Screen parent,PackManifest manifest,Set<String> selected,Consumer<Set<String>> apply){super(Client.tr("ui.server_pack_88518674"));this.parent=parent;this.selected=new HashSet<>(selected);this.apply=apply;
  var items=new ArrayList<Row>();
  for(var c:manifest.components())items.add(new Row(c.id(),c.name(),c.description(),false));
  for(var file:manifest.files())if(file.component().isEmpty()){
   String name=file.path().substring(file.path().lastIndexOf('/')+1);
   items.add(new Row("",name,Client.text("ui.required_file_9792ac97")+file.path(),true));
  }
  rows=List.copyOf(items);
 }
 protected void init(){
  dialog=UiDialog.fit(width,height,460,320);
  var body=dialog.body();int descriptionWidth=Math.max(1,body.width()-38);rowStride=24+rows.stream().mapToInt(r->Math.min(3,font.split(Component.literal(r.description()),descriptionWidth).size())*font.lineHeight).max().orElse(0);var area=scrollArea(rows.size(),body,rowStride);
  for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++){
   var row=rows.get(i);int y=body.y()+(i-firstRow)*rowStride;
   var choice=new UiChoiceRow(area.content().x(),y,area.content().width(),row.label(),row.required()||selected.contains(row.id()),-1,UiKit.ACCENT,()->{
    if(row.required())return;
    if(!selected.remove(row.id()))selected.add(row.id());rebuildWidgets();
   });
   choice.active=!row.required();
   choice.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(row.description().isBlank()?row.label():row.description())));
   addRenderableWidget(choice);
  }
  var f=dialog.footer();UiActions.row(new dev.abros.rivet.core.NativeLayout.Box(f.x(),f.bottom()-20,f.width(),20),this::addRenderableWidget,UiActions.primary(Client.text("ui.review_changes_f5459a86"),()->apply.accept(Set.copyOf(selected)),true),UiActions.action(Client.text("cancel"),this::onClose,true));
 }
 public void renderBackground(GuiGraphics g,int x,int y,float d){var f=dialog.frame();UiDialog.surface(g,f.x(),f.y(),f.width(),f.height());}
 public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);var area=scrollLayout().content();for(int i=firstRow;i<Math.min(rows.size(),firstRow+visibleRows);i++){var lines=font.split(Component.literal(rows.get(i).description()),Math.max(1,area.width()-26));for(int n=0;n<Math.min(3,lines.size());n++)g.drawString(font,lines.get(n),area.x()+26,area.y()+(i-firstRow)*rowStride+22+n*font.lineHeight,UiKit.muted(),false);}var h=dialog.header();UiHeading.dialog(g,font,title,h.x(),dialog.frame().y(),h.width());if(rows.isEmpty()){var b=dialog.body();g.drawWordWrap(font,Client.tr("ui.the_pack_contains_no_files_2d1c15d8"),b.x(),b.y(),b.width(),UiPalette.color(0xEEEEEE));}});}
 public void onClose(){minecraft.setScreen(parent);}public boolean isPauseScreen(){return false;}
}
