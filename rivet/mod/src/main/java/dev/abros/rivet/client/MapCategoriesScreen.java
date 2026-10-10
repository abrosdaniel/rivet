package dev.abros.rivet.client;
import dev.abros.rivet.core.map.MapCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
/** Category identity survives edits; deleting a category keeps its markers. */
final class MapCategoriesScreen extends ScrollScreen{
 private final Screen parent;private UiDialog dialog;private UUID id=UUID.randomUUID();private String name="",error="";private boolean map=true,mini=true,existing;
 MapCategoriesScreen(Screen parent){super(Client.tr("map.categories"));this.parent=parent;}
 private void select(MapCategory c){id=c.id();name=c.name();map=c.mapVisible();mini=c.minimapVisible();existing=true;error="";rebuildWidgets();}
 private void fresh(){id=UUID.randomUUID();name="";map=mini=true;existing=false;error="";rebuildWidgets();}
 @Override protected void init(){dialog=UiDialog.fit(width,height,300,266);var b=dialog.body();int x=b.x();var area=scrollForm(5,new dev.abros.rivet.core.NativeLayout.Box(x,b.y()+28,b.width(),Math.max(0,b.height()-28)),28);int w=area.content().width();
  addRenderableWidget(UiActions.button(Component.literal((existing?name:Client.tr("map.newCategory").getString())+" ▾"),UiActions.Tone.NORMAL,"",button->{var rows=WorldMapClient.categories();var labels=new ArrayList<String>();labels.add(Client.tr("map.newCategory").getString());rows.forEach(c->labels.add(c.name()));minecraft.setScreen(new ChoicePopup(this,Client.tr("map.categories").getString(),labels,i->{if(i==0)fresh();else select(rows.get(i-1));minecraft.setScreen(this);},button));}).bounds(x,b.y(),w-28,20).build());
  addRenderableWidget(UiActions.tool(Client.tr("map.newCategory"),MapGlyphs.icon("plus"),x+w-20,b.y(),this::fresh));
  for(int row=firstRow;row<Math.min(5,firstRow+visibleRows);row++){int y=b.y()+28+(row-firstRow)*28;switch(row){
   case 0->addRenderableWidget(UiFields.text(font,x,y,w,20,Client.tr("map.categoryName").getString(),Client.tr("map.categoryName").getString(),40,name,v->name=v));
   case 1->addRenderableWidget(new UiToggle(Client.tr("map.visibleMap").getString(),x,y,w,map,()->{map=!map;rebuildWidgets();}));
   case 2->addRenderableWidget(new UiToggle(Client.tr("map.visibleMinimap").getString(),x,y,w,mini,()->{mini=!mini;rebuildWidgets();}));
   case 3->addRenderableWidget(UiActions.button(Client.tr("map.save"),UiActions.Tone.PRIMARY,MapGlyphs.icon("save"),button->{try{WorldMapClient.category(new MapCategory(id,name,map,mini));existing=true;error="";rebuildWidgets();}catch(Exception ex){error=Client.tr("map.categoryInvalid").getString();}}).bounds(x,y,w,20).build());
   case 4->{var delete=addRenderableWidget(UiActions.button(Client.tr("map.deleteCategory"),UiActions.Tone.DANGER,MapGlyphs.icon("delete"),button->minecraft.setScreen(new UiConfirmDialog(yes->{if(yes){try{WorldMapClient.deleteCategory(id);fresh();}catch(Exception ex){error=Client.tr("map.saveError").getString();}}minecraft.setScreen(this);},Client.tr("map.deleteCategory"),Client.tr("map.deleteCategoryInfo")).dangerous().compact())).bounds(x,y,w,20).build());delete.active=existing;}
  }}
  UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(x,dialog.footer().bottom()-20,w,20),this::addRenderableWidget,this::onClose);
 }
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());if(!error.isEmpty())Ui.text(g,font,UiKit.fit(font,error,dialog.footer().width()),dialog.footer().x(),dialog.footer().y(),UiKit.text(),false);});}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
