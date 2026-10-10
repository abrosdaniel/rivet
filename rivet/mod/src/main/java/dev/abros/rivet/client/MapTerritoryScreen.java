package dev.abros.rivet.client;

import dev.abros.rivet.core.map.MapTerritory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Edits a private draft; nothing is persisted until Save. */
final class MapTerritoryScreen extends ScrollScreen implements CommunityScreen.Receiver {
 private final WorldMapScreen parent;private final MapTerritories.Draft draft;private UiDialog dialog;private String error="";private final dev.abros.rivet.core.RequestSession session=new dev.abros.rivet.core.RequestSession();private boolean busy,retryable;
 private final java.util.Map<Integer,String[]> coordinates=new java.util.HashMap<>();
 MapTerritoryScreen(WorldMapScreen parent,MapTerritories.Draft draft){super(Client.tr("map.territory"));this.parent=parent;this.draft=draft;}
 private boolean applyVertices(){try{for(var e:coordinates.entrySet()){var v=e.getValue();draft.points.set(e.getKey(),new MapTerritory.Point(Integer.parseInt(v[0]),Integer.parseInt(v[1])));}return true;}catch(IllegalArgumentException ex){error=Client.tr("map.territoryInvalid").getString();return false;}}
 @Override protected void init(){dialog=UiDialog.fit(width,height,360,390);var b=dialog.body();int columns=Math.max(1,(b.width()-12)/20),paletteRows=(MapMarkerPanel.COLOURS.length+columns-1)/columns;int count=4+paletteRows+draft.points.size();var area=scrollForm(count,b,28);int w=area.content().width();
  for(int row=firstRow;row<Math.min(count,firstRow+visibleRows);row++){int y=b.y()+(row-firstRow)*28,x=b.x();if(row>=1&&row<=paletteRows){for(int i=(row-1)*columns;i<Math.min(row*columns,MapMarkerPanel.COLOURS.length);i++){int color=MapMarkerPanel.COLOURS[i];addRenderableWidget(new UiColorSwatch(x+(i%columns)*20,y,color,Client.tr("map.color."+MapMarkerPanel.COLOUR_NAMES[i]),draft.color==color,()->{draft.color=color;rebuildWidgets();}));}continue;}int logical=row==0?0:row-paletteRows+1;switch(logical){
   case 0->addRenderableWidget(UiActions.button(Component.literal(UiKit.fit(font,draft.name,w-12)),UiActions.Tone.NORMAL,"",button->{}).bounds(x,y,w,20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(draft.name))).build()).active=false;
   case 2->addRenderableWidget(UiActions.button(Client.tr("map.redrawTerritory"),UiActions.Tone.NORMAL,MapGlyphs.icon("territory"),button->{if(applyVertices()){draft.points.clear();coordinates.clear();parent.drawTerritory(draft);}}).bounds(x,y,w,20).build());
   case 3->{var delete=addRenderableWidget(UiActions.button(Client.tr("map.deleteTerritory"),UiActions.Tone.DANGER,MapGlyphs.icon("delete"),button->minecraft.setScreen(new UiConfirmDialog(yes->{if(yes){try{minecraft.setScreen(this);submit(true);}catch(Exception ex){error=ex.getMessage();minecraft.setScreen(this);}}else minecraft.setScreen(this);},Client.tr("map.deleteTerritory"),Component.literal(draft.name)).dangerous().compact())).bounds(x,y,w,20).build());delete.active=!busy&&MapGroupClient.territories().stream().anyMatch(t->t.id().equals(draft.id));}
   case 4->addRenderableWidget(UiActions.button(Client.tr("map.vertices"),UiActions.Tone.NORMAL,"",button->{}).bounds(x,y,w,20).build()).active=false;
   default->{int index=logical-5;var p=draft.points.get(index);var text=coordinates.computeIfAbsent(index,k->new String[]{Integer.toString(p.x()),Integer.toString(p.z())});int half=(w-32)/2;
    addRenderableWidget(UiFields.text(font,x,y,half,20,"X · "+(index+1),"X",9,text[0],v->text[0]=v));
    addRenderableWidget(UiFields.text(font,x+half+4,y,half,20,"Z · "+(index+1),"Z",9,text[1],v->text[1]=v));
    addRenderableWidget(UiActions.tool(Client.tr("map.removeVertex"),MapGlyphs.icon("clear"),x+w-20,y,()->{if(applyVertices()){draft.points.remove(index);coordinates.clear();rebuildWidgets();}}));
   }
  }}
  int y=dialog.footer().bottom()-20,half=(b.width()-8)/2;
  addRenderableWidget(UiActions.button(Client.tr("map.save"),UiActions.Tone.PRIMARY,MapGlyphs.icon("save"),button->{if(applyVertices())try{submit(false);}catch(IllegalArgumentException ex){error=Client.tr("map.territoryInvalid").getString();}catch(IllegalStateException ex){error=ex.getMessage();}}).bounds(b.x(),y,half,20).build());
  if(busy||retryable)for(var child:children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget)widget.active=retryable&&widget.getMessage().equals(Client.tr("map.save"));
  addRenderableWidget(UiActions.button(Client.tr("cancel"),UiActions.Tone.NORMAL,"",button->onClose()).bounds(b.x()+half+8,y,half,20).build());
 }
 private void submit(boolean remove){if(busy||!MapGroupClient.available()||draft.group.isEmpty())return;if(retryable){busy=true;ServerMenuClient.request(session.retry(System.currentTimeMillis()));rebuildWidgets();return;}var j=new com.google.gson.JsonObject();j.addProperty("action","community");j.addProperty("section","groups");j.addProperty("op","plusTerritorySave");j.addProperty("id",draft.group);j.addProperty("revision",draft.revision);j.addProperty("clearTerritory",remove);j.add("territory",remove?com.google.gson.JsonNull.INSTANCE:dev.abros.rivet.core.map.MapTerritoryJson.write(draft.value()));busy=true;error=Client.text("ui.saving_632fd0d9");ServerMenuClient.request(session.begin(j,true,System.currentTimeMillis()));rebuildWidgets();}
 public void receiveCommunity(com.google.gson.JsonObject j){if(!session.receive(j))return;busy=false;retryable=false;if(j.has("error")){error=dev.abros.rivet.core.Json.opt(j,"text",Client.text("ui.could_not_save_the_territory_8a3e19f0"));rebuildWidgets();}else{MapGroupClient.reset();parent.cancelTerritory();minecraft.setScreen(parent);}}
 @Override public void tick(){if(!MapGroupClient.available()){session.cancel();onClose();return;}if(session.timeout(System.currentTimeMillis())){busy=false;retryable=true;error=Client.text("ui.no_response_try_saving_again_a12a6126");rebuildWidgets();}}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());Ui.text(g,font,UiKit.fit(font,error.isEmpty()?Client.text("ui.group_territory_ff2573fd"):error,dialog.footer().width()),dialog.footer().x(),dialog.footer().y(),UiKit.muted(),false);});}
 @Override public void onClose(){session.cancel();parent.cancelTerritory();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
