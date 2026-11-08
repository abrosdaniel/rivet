package dev.abros.rivet.client;

import com.google.gson.JsonArray;
import dev.abros.rivet.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** One local, permission-filtered catalogue. No personal coordinates are sent to search. */
final class MapPlacesScreen extends ScrollScreen implements UiParentScreen {
 private static final List<String> SOURCES=List.of("all","personal","public","groups","adapters");
 private final Screen parent;private UiDialog dialog;private String query="",source="all",notice="";private int page;
 private JsonArray rows=new JsonArray();private boolean next,dirty;private long refreshAt;
 MapPlacesScreen(Screen parent){super(Client.tr("map.places.title"));this.parent=parent;refresh();}
 public Screen parentScreen(){return parent;}
 private void refresh(){rows=MenuPlaces.browse(query,source,page*50);next=rows.size()>50;if(next)rows.remove(50);refreshAt=System.currentTimeMillis()+1000;}
 private void changed(){notice="";page=0;resetScroll();refresh();rebuildSearch();}
 private void rebuildSearch(){boolean focused=getFocused() instanceof net.minecraft.client.gui.components.EditBox;int cursor=focused?((net.minecraft.client.gui.components.EditBox)getFocused()).getCursorPosition():0;rebuildWidgets();if(focused)for(var child:children())if(child instanceof net.minecraft.client.gui.components.EditBox field){setFocused(field);field.setCursorPosition(cursor);break;}}
 @Override protected void init(){
  dialog=UiDialog.fit(width,height,460,350);var b=dialog.body();int filter=Math.min(128,b.width()/3);
  var search=addRenderableWidget(UiFields.text(font,b.x(),b.y(),b.width()-filter-8,20,Client.tr("map.search")));
  search.setValue(query);search.setResponder(value->{query=value;dirty=true;});
  addRenderableWidget(UiActions.button(Client.tr("map.places."+source),UiActions.Tone.NORMAL,"",button->minecraft.setScreen(new ChoicePopup(this,title.getString(),SOURCES.stream().map(s->Client.text("map.places."+s)).toList(),n->{source=SOURCES.get(n);changed();},button).current(SOURCES.indexOf(source)))).bounds(b.right()-filter,b.y(),filter,20).build());
  var area=scrollArea(rows.size(),new NativeLayout.Box(b.x(),b.y()+28,b.width(),Math.max(0,b.height()-48)),36).content();
  for(int n=firstRow;n<Math.min(rows.size(),firstRow+visibleRows);n++){
   var row=rows.get(n).getAsJsonObject();String id=Json.str(row,"id");int y=area.y()+(n-firstRow)*36;
   var button=addRenderableWidget(UiActions.button(Component.literal(Json.str(row,"title")),UiActions.Tone.NORMAL,"",ignored->{if(!MenuPlaces.open(this,id)){notice=Client.text("search.place.unavailable");refresh();rebuildWidgets();}}).bounds(area.x(),y,area.width(),20).build());
   button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(Json.str(row,"label")+" · ").append(dev.abros.rivet.network.DimensionLabels.name(Json.str(row,"dimension")))));
  }
  var footer=dialog.footer();int part=(footer.width()-16)/3;
  addRenderableWidget(UiActions.button(Client.tr("server.previous"),UiActions.Tone.NORMAL,"",button->{page--;resetScroll();refresh();rebuildWidgets();}).bounds(footer.x(),footer.bottom()-20,part,20).build()).active=page>0;
  addRenderableWidget(UiActions.button(Client.tr("server.next"),UiActions.Tone.NORMAL,"",button->{page++;resetScroll();refresh();rebuildWidgets();}).bounds(footer.x()+part+8,footer.bottom()-20,part,20).build()).active=next;
  UiActions.close(new NativeLayout.Box(footer.x()+2*(part+8),footer.bottom()-20,part,20),this::addRenderableWidget,this::onClose);
 }
 @Override public void tick(){
  if(dirty){dirty=false;changed();}
  else if(System.currentTimeMillis()>=refreshAt){var before=rows;boolean hadNext=next;refresh();if(hadNext!=next||!before.equals(rows))rebuildSearch();}
 }
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{
  super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());var b=dialog.body();
  for(int n=firstRow;n<Math.min(rows.size(),firstRow+visibleRows);n++){var row=rows.get(n).getAsJsonObject();Ui.text(g,font,UiKit.fit(font,Json.str(row,"label"),b.width()-8),b.x()+4,b.y()+50+(n-firstRow)*36,UiKit.muted(),false);}
  String text=!notice.isEmpty()?notice:rows.isEmpty()?Client.text("map.places.empty"):Client.text("map.places.loaded");Ui.text(g,font,UiKit.fit(font,text,b.width()),b.x(),b.bottom()-12,UiKit.muted(),false);
 });}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
