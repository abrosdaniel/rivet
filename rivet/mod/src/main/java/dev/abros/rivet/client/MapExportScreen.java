package dev.abros.rivet.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import dev.abros.rivet.core.NativeLayout;

/** Export is explicit and keeps the map view intact when returning. */
final class MapExportScreen extends Screen {
 private final WorldMapScreen parent;private final MapExport.Area area;private UiDialog dialog;private MapExport.Job job;private String error="";
 MapExportScreen(WorldMapScreen parent){super(Client.tr("map.export"));this.parent=parent;var v=parent.view;int x=(int)Math.floor(v.worldX(0,parent.width/2d)),z=(int)Math.floor(v.worldZ(0,parent.height/2d));area=new MapExport.Area(x,z,Math.max(1,(int)Math.ceil(parent.width/v.zoom())),Math.max(1,(int)Math.ceil(parent.height/v.zoom())));}
 Screen parentScreen(){return parent;}
 @Override protected void init(){dialog=UiDialog.fit(width,height,340,190);var b=dialog.body();addRenderableWidget(UiActions.button(Client.tr("ui.export_visible_area_968b0608"),UiActions.Tone.PRIMARY,MapGlyphs.icon("save"),ignored->{try{job=MapExport.export(MapCaves.layer(parent.selectedDimension()),area);error="";rebuildWidgets();}catch(RuntimeException e){error=e.getMessage();}}).bounds(b.x(),b.bottom()-28,b.width(),24).build()).active=job==null||job.done;UiActions.close(new NativeLayout.Box(dialog.footer().right()-96,dialog.footer().bottom()-20,96,20),this::addRenderableWidget,this::onClose);}
 @Override public void tick(){if(job!=null&&job.done&&children().stream().anyMatch(c->c instanceof net.minecraft.client.gui.components.Button b&&!b.active))rebuildWidgets();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.surface(g,dialog.frame().x(),dialog.frame().y(),dialog.frame().width(),dialog.frame().height());}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,title,dialog.header().x(),dialog.frame().y(),dialog.header().width());var b=dialog.body();Ui.text(g,font,"PNG · "+area.pixelsX()+" × "+area.pixelsZ(),b.x(),b.y(),UiKit.text(),false);String status=!error.isEmpty()?error:job==null?Client.text("ui.no_interface_unexplored_areas_are_transparent_d0d40304"):!job.done?Client.text("ui.export_2e3ac583")+job.progress+"%":job.file!=null?Client.text("ui.saved_in_rivet_exports_8ed8a4ff"):job.error;Ui.text(g,font,UiKit.fit(font,status,b.width()),b.x(),b.y()+18,UiKit.muted(),false);});}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
