package dev.abros.rivet.client;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.core.NativeLayout;
import net.minecraft.client.gui.GuiGraphics;

import net.minecraft.client.gui.screens.Screen;
final class ServerInfoScreen extends Screen {
 private final MenuSidebar nav=new MenuSidebar(this);private final Screen parent;private final ContentPane news=new ContentPane(),rules=new ContentPane();private String requested;private NativeLayout.Box newsArea,rulesArea;
 ServerInfoScreen(Screen parent){super(Client.tr("server.tab.info"));this.parent=parent;news.text(Client.tr("server.loading").getString());rules.text(Client.tr("server.loading").getString());}
 @Override protected void init(){
  nav.build("info",this::addRenderableWidget);
  var workspace=UiWorkspace.fit(width,height);var page=workspace.page();var footer=UiPageFooter.workspace(width,height);
  int bottom=Math.max(page.y(),workspace.footer().y()-32);
  var columns=NativeLayout.row(new NativeLayout.Box(page.x(),page.y()+22,page.width(),Math.max(0,bottom-page.y()-22)),12,NativeLayout.Track.flex(1),NativeLayout.Track.flex(1));
  newsArea=columns.get(0);rulesArea=columns.get(1);
  news.bounds(newsArea.x(),newsArea.y()+16,newsArea.width(),Math.max(0,newsArea.height()-16));
  rules.bounds(rulesArea.x(),rulesArea.y()+16,rulesArea.width(),Math.max(0,rulesArea.height()-16));
  addRenderableWidget(UiActions.button(Client.tr("server.links"),UiActions.Tone.NORMAL,"",v->FeatureListScreen.open(this,"links")).bounds(footer.start().x(),footer.start().y(),footer.start().width(),footer.start().height()).build());
  footer.end(UiActions.Command.BACK,this::addRenderableWidget,this::onClose);
  addRenderableWidget(UiActions.button(Client.tr("server.packStatus"),UiActions.Tone.NORMAL,"",v->checkPack()).bounds(page.x(),bottom+6,page.width(),20).build());
  loadContent();
 }
 private void loadContent(){
  String repository=Json.opt(ServerMenuClient.state,"repository","").strip();if(repository.equals(requested))return;requested=repository;
  if(repository.isEmpty()){news.text(Client.tr("server.noProjectNews").getString());rules.text(Client.tr("server.noProjectRules").getString());return;}
  news.text(Client.tr("server.loading").getString());rules.text(Client.tr("server.loading").getString());
  Client.IO.submit(()->{try{var release=Client.hub.repositories.fetchOrCached(repository);String n=Client.hub.content(release.manifest(),"news"),r=Client.hub.content(release.manifest(),"rules");minecraft.execute(()->{if(repository.equals(requested)){news.text(n);rules.text(r);}});}catch(Exception ex){minecraft.execute(()->{if(repository.equals(requested)){news.text(Errors.message(ex));rules.text(Errors.message(ex));}});}});
 }
 private void checkPack(){Client.IO.submit(()->{try{String version=Client.hub.active()==null?"—":Client.hub.active().version();String required=Json.opt(ServerMenuClient.state,"packVersion","—"),hash=Json.opt(ServerMenuClient.state,"requiredHash","");var issues=Client.hub.audit();String text=Client.tr("server.installed",version).getString()+"\n"+Client.tr("server.required",required).getString()+"\n\n"+Client.tr(hash.isEmpty()?"server.packUnknown":hash.equals(Client.hub.activeHash())&&issues.isEmpty()?"healthy":"server.packMismatch").getString()+"\n"+String.join("\n",issues);minecraft.execute(()->{if(minecraft.screen==this&&ServerMenuClient.available())minecraft.setScreen(new TextScreen(this,Client.tr("server.packStatus"),text));});}catch(Exception ex){minecraft.execute(()->{if(minecraft.screen==this)minecraft.setScreen(new TextScreen(this,Client.tr("server.packStatus"),Errors.message(ex)));});}});}
 @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);UiHeading.page(g,font,title,width);nav.drawFrame(g);var page=UiWorkspace.fit(width,height).page();Ui.text(g,font,font.plainSubstrByWidth(Json.opt(ServerMenuClient.state,"description",""),page.width()),page.x(),page.y(),UiPalette.color(0xEEEEEE));Ui.text(g,font,Client.tr("news"),newsArea.x(),newsArea.y(),UiPalette.color(0xE2BE75));Ui.text(g,font,Client.tr("rules"),rulesArea.x(),rulesArea.y(),UiPalette.color(0xE2BE75));news.render(g,font);rules.render(g,font);}
 @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(nav.scroll(x,y,dy)){rebuildWidgets();return true;}return news.scroll(x,y,dy)||rules.scroll(x,y,dy)||super.mouseScrolled(x,y,dx,dy);}
 @Override public boolean mouseClicked(double x,double y,int b){if(b==0){if(news.click(x,y)||rules.click(x,y))return true;for(var pane:new ContentPane[]{news,rules}){var style=pane.link(font,x,y);if(style!=null&&style.getClickEvent()!=null)return handleComponentClicked(style);}}return super.mouseClicked(x,y,b);}
 @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return b==0&&(news.drag(y)||rules.drag(y))||super.mouseDragged(x,y,b,dx,dy);}
 @Override public boolean mouseReleased(double x,double y,int b){news.release();rules.release();return super.mouseReleased(x,y,b);}
 @Override public void tick(){if(!ServerMenuClient.available())minecraft.setScreen(null);else loadContent();}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
