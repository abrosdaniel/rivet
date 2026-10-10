package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.time.*;


/** Paged server lists; response routing stays bound to the requesting screen. */
final class FeatureListScreen extends ScrollScreen {
    private final dev.abros.rivet.core.PagedMenuController controller;
    private JsonObject selectedPlayer;
    void leavePage(){controller.leave();ServerMenuClient.cancelReads(this);}
    boolean navigationPage(){return kind.equals("players")&&selection==null;}
    void invalidate(){controller.invalidate();}
    private boolean splitPlayers(){return kind.equals("players")&&selection==null&&height>=320&&UiWorkspace.fit(width,height).page().width()>=552;}
    private int playerRowsTop(){return UiSearchToolbar.contentWidth(listWidth())<340?94:68;}
    private int playerPanelLeft(){return nav.left()+listWidth()+12;}
    private int listWidth(){var area=UiWorkspace.fit(width,height).page();return splitPlayers()?UiListDetail.fit(area,230,true).list().width():area.width();}
    private String focusReport="";private final MenuSidebar nav=new MenuSidebar(this);private java.util.function.Consumer<JsonObject> selection;private String query="";private long searchAt;private boolean allPlayers;private final Screen parent;final String kind;private JsonArray entries=new JsonArray(),actions=new JsonArray();private String status="";
    FeatureListScreen(Screen parent,String kind){super(kind.equals("players")?Component.literal(CommunityScreen.name(kind)):Client.tr("server."+kind));this.parent=parent;this.kind=kind;this.controller=new dev.abros.rivet.core.PagedMenuController(kind,ServerMenuClient::request,System::currentTimeMillis);}
    static void open(Screen parent,String kind){if(kind.equals("reports")&&ServerMenuClient.supports("community-extensions")){net.minecraft.client.Minecraft.getInstance().setScreen(new ReportQueueScreen(parent,"new"));return;}var screen=new FeatureListScreen(parent,kind);net.minecraft.client.Minecraft.getInstance().setScreen(screen);screen.load(0);}
    static void searchPlayer(Screen parent,String name){var screen=new FeatureListScreen(parent,"players");screen.query=name;screen.allPlayers=true;net.minecraft.client.Minecraft.getInstance().setScreen(screen);screen.load(0);}
    static void openReport(Screen parent,String id){var screen=new FeatureListScreen(parent,"myReports");screen.focusReport=id;net.minecraft.client.Minecraft.getInstance().setScreen(screen);var request=new JsonObject();request.addProperty("action","myReport");request.addProperty("id",id);screen.controller.request(request,0);}
    static void pick(Screen parent,java.util.function.Consumer<JsonObject> selection){var screen=new FeatureListScreen(parent,"players");screen.selection=selection;screen.allPlayers=true;net.minecraft.client.Minecraft.getInstance().setScreen(screen);screen.load(0);}
    private void load(int page){var packet=new JsonObject();packet.addProperty("action",kind.equals("links")?"menuData":kind);packet.addProperty("query",query);packet.addProperty("all",allPlayers);if(controller.request(packet,page))status=Client.tr("server.loading").getString();}
    void receive(JsonObject reply){
        var result=controller.receive(reply);if(!result.accepted())return;
        if(result.failed()){status=controller.error();rebuildWidgets();return;}
        String selectedId=selectedPlayer==null?"":Json.str(selectedPlayer,"uuid");entries=controller.entries();
        if(selectedId.isEmpty()&&kind.equals("players")&&selection==null&&!entries.isEmpty())selectedPlayer=entries.get(0).getAsJsonObject();
        if(!selectedId.isEmpty()){selectedPlayer=null;for(var entry:entries)if(Json.opt(entry.getAsJsonObject(),"uuid","").equals(selectedId))selectedPlayer=entry.getAsJsonObject();}
        if(!focusReport.isEmpty()&&entries.size()==1){focusReport="";minecraft.setScreen(new ReportDetailScreen(parent,entries.get(0).getAsJsonObject(),false));return;}
        var metadata=controller.reply();if(metadata.has("actions"))actions=metadata.getAsJsonArray("actions");
        status=entries.isEmpty()?Client.tr("content.empty").getString():"";if(result.changed())rebuildWidgets();
    }
    void failure(String text){controller.failure(text);status=text;rebuildWidgets();}
    private void reset(){if(controller.busy())return;searchAt=0;controller.reset();entries=controller.entries();resetScroll();load(0);rebuildWidgets();}
    private void refresh(){if(controller.busy())return;controller.refresh();load(0);}
    private void next(){if(controller.nextAllowed())load(controller.loadedPage()+1);}
    @Override protected void init(){int w=kind.equals("players")?listWidth():Math.min(620,UiPage.body(width,height).width()),left=kind.equals("players")?nav.left():(width-w)/2;if(kind.equals("players"))nav.build("players",this::addRenderableWidget);boolean reportList=kind.equals("reports")||kind.equals("myReports");int stride=reportList?(AccessibilityScreen.compact()?60:76):kind.equals("players")?36:28;int top=kind.equals("players")?playerRowsTop():40;scrollArea(entries.size(),new dev.abros.rivet.core.NativeLayout.Box(left,top,Math.max(0,w),Math.max(0,(height-70)-(top))),stride);
        if(kind.equals("players")){w=UiSearchToolbar.contentWidth(w);boolean stacked=w<340;int filterY=stacked?68:42;var search=UiSearchToolbar.query(font,new dev.abros.rivet.core.NativeLayout.Box(left,42,stacked?w:w-112,20),Client.text("ui.player_name_e27fc713"),query,this::addRenderableWidget,v->{query=v;searchAt=System.currentTimeMillis()+500;},this::reset,!controller.busy());search.setMaxLength(32);addRenderableWidget(UiActions.button(Component.literal((allPlayers?Client.text("ui.all_fd08da7a"):Client.text("ui.online_e858f4e3"))+" ▾"),UiActions.Tone.NORMAL,"",b->{if(!controller.busy())minecraft.setScreen(new ChoicePopup(this,Client.text("map.layer.players"),java.util.List.of(Client.text("ui.online_e858f4e3"),Client.text("ui.all_players_7b6d95ec")),n->{allPlayers=n==1;reset();},b).current(allPlayers?1:0));}).bounds(stacked?left:left+w-108,filterY,stacked?w-26:82,20).build());addRenderableWidget(UiActions.button(Component.literal("×"),UiActions.Tone.NORMAL,"",b->{query="";allPlayers=false;reset();rebuildWidgets();}).bounds(left+w-20,filterY,20,20).tooltip(Tooltip.create(Client.tr("ui.reset_search_and_show_online_players_9529d440"))).build()).active=!query.isBlank()||allPlayers;}
        if(kind.equals("players"))w=listWidth();
        for(int i=firstRow;i<Math.min(entries.size(),firstRow+visibleRows);i++){var item=entries.get(i).getAsJsonObject();String label=label(item);int y=top+(i-firstRow)*stride;
            if(kind.equals("players"))addRenderableWidget(new PlayerRow(left,y,w,item,()->detail(item),selectedPlayer!=null&&Json.str(selectedPlayer,"uuid").equals(Json.str(item,"uuid"))));
            else if(reportList){var card=item.deepCopy();card.addProperty("section","moderation");card.addProperty("title",Client.text("ui.report_26c7dcd8")+Json.opt(item,"player",Client.text("map.tool.player")));card.addProperty("attention",Client.tr("server.status."+Json.opt(item,"status","open")).getString()+(Json.opt(item,"priority","normal").equals("high")?Client.text("ui.high_priority_8d9b05d1"):""));card.addProperty("preview",Json.opt(item,"message",""));card.addProperty("footer",Json.opt(item,"assignedName","").isBlank()?Client.text("ui.no_assignee_b69f1a51"):Client.text("ui.assigned_to_31372c54")+Json.opt(item,"assignedName",""));addRenderableWidget(new CommunityCard(left,y,w,stride-6,card,()->detail(item)));}
            else addRenderableWidget(UiActions.button(Component.literal(font.plainSubstrByWidth(label,w-12)),UiActions.Tone.NORMAL,"",b->detail(item)).bounds(left,y,w,24).build());}

        if(splitPlayers()&&selectedPlayer!=null){
            int x=playerPanelLeft(),wPanel=UiWorkspace.fit(width,height).page().right()-x;
            int actionY=74+UiPlayerIdentity.height(font,selectedPlayer,wPanel-24)+PlayerStatisticsText.summary(selectedPlayer).size()*14;
            int half=(wPanel-30)/2;boolean online=selectedPlayer.has("online")&&selectedPlayer.get("online").getAsBoolean();
            boolean self=Json.str(selectedPlayer,"uuid").equals(Json.opt(ServerMenuClient.state,"uuid",""));
            if(!self){if(online&&actions.contains(new JsonPrimitive("tell")))addRenderableWidget(UiActions.button(Client.tr("ui.write_bda589de"),UiActions.Tone.NORMAL,"",b->PlayerActionsScreen.openChat(this,Json.str(selectedPlayer,"name"))).bounds(x+12,actionY,half,20).build());
            if(ServerMenuClient.module("reports"))addRenderableWidget(UiActions.button(Client.tr("ui.report_5405a3bc"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(ReportScreen.player(this,Json.str(selectedPlayer,"name")))).bounds(x+18+half,actionY,half,20).build());
            if(ServerMenuClient.module("groups"))addRenderableWidget(UiActions.button(Client.tr("ui.invite_to_group_4f69a115"),UiActions.Tone.NORMAL,"",b->CommunityScreen.invite(this,selectedPlayer)).bounds(x+12,actionY+26,wPanel-24,20).build());
            }
            addRenderableWidget(UiActions.button(Client.tr("ui.player_profile_d138bbf0"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new PlayerActionsScreen(this,selectedPlayer,actions))).bounds(x+12,actionY+(self?0:52),wPanel-24,20).build());

        }
        var footer=kind.equals("players")?UiPageFooter.workspace(width,height):UiPageFooter.fit(new dev.abros.rivet.core.NativeLayout.Box(left,height-32,w,20));
        footer.start(UiActions.Command.REFRESH,this::addRenderableWidget,this::refresh).active=!controller.busy();
        footer.end(UiNavigation.exitCommand(this,parent),this::addRenderableWidget,this::onClose);

    }
    private String label(JsonObject j){return switch(kind){case "players" -> Json.str(j,"name")+(j.has("online")&&j.get("online").getAsBoolean()?" · ●":"");case "links" -> Json.str(j,"name");case "history" -> Json.str(j,"actor")+" · "+Json.str(j,"action");default -> Client.tr("server.status."+Json.opt(j,"status","open")).getString()+" · "+Json.str(j,"player")+" · "+Json.str(j,"message");};}
    private void detail(JsonObject j){
        if(kind.equals("players")){if(selection!=null){minecraft.setScreen(parent);selection.accept(j);}else if(splitPlayers()){selectedPlayer=j;rebuildWidgets();}else minecraft.setScreen(new PlayerActionsScreen(this,j,actions));return;}
        if(kind.equals("links")){handleComponentClicked(Component.literal(Json.str(j,"name")).withStyle(s->s.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.OPEN_URL,Json.str(j,"url")))).getStyle());return;}
        if(kind.equals("history")){minecraft.setScreen(new TextScreen(this,title,Instant.ofEpochMilli(j.get("at").getAsLong()).toString()+"\n"+label(j)+"\n"+Json.str(j,"detail")));return;}
        minecraft.setScreen(new ReportDetailScreen(this,j,kind.equals("reports")));
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if((key==257||key==335)&&kind.equals("players")&&getFocused() instanceof EditBox){reset();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override protected void onScrollEnd(){next();}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(kind.equals("players")&&nav.scroll(x,y,dy)){rebuildWidgets();return true;}boolean used=super.mouseScrolled(x,y,dx,dy);if(used&&dy<0&&firstRow+visibleRows>=entries.size())next();return used;}
    @Override public void tick(){if(!ServerMenuClient.available()){minecraft.setScreen(null);return;}if(controller.timeout()){status=controller.error();rebuildWidgets();}else if(!controller.busy()&&searchAt>0&&System.currentTimeMillis()>=searchAt&&System.currentTimeMillis()-controller.sent()>=600)reset();else if(controller.refreshDue())load(controller.page()+1);else if(controller.dirtyDue())refresh();else if((kind.equals("reports")&&!ServerMenuClient.may("rivet.reports")||kind.equals("history")&&!ServerMenuClient.admin()))UiNavigation.back(this,parent);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){super.renderBackground(g,mx,my,d);if(!kind.equals("players"))UiPage.draw(g,width,height);if(splitPlayers()){
      int x=playerPanelLeft(),wPanel=UiWorkspace.fit(width,height).page().right()-x;UiKit.material(g,x,42,wPanel,UiWorkspace.fit(width,height).page().height());
      if(selectedPlayer==null){Ui.status(g,font,Client.text("ui.select_a_player_on_the_left_2642ea86"),x+12,54,wPanel-24,height-78);return;}
      UiPlayerIdentity.draw(g,font,selectedPlayer,x+12,54,wPanel-24);
      int statY=62+UiPlayerIdentity.height(font,selectedPlayer,wPanel-24);for(String line:PlayerStatisticsText.summary(selectedPlayer)){Ui.text(g,font,UiKit.fit(font,line,wPanel-24),x+12,statY,UiKit.muted(),false);statY+=14;}
    }}
    @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);UiHeading.page(g,font,title,width);nav.drawFrame(g);if(entries.isEmpty())UiState.draw(g,font,controller.failed()?UiState.Kind.ERROR:controller.busy()?UiState.Kind.LOADING:query.isBlank()?UiState.Kind.EMPTY:UiState.Kind.FILTERED,controller.failed()?Client.text("ui.list_unavailable_dac91ee5"):controller.busy()?Client.text("ui.loading_list_604a4412"):kind.equals("players")?query.isBlank()?Client.text("ui.no_players_662ea944"):Client.text("ui.no_players_found_149dfe62"):Client.text("ui.no_entries_yet_5d9c52da"),controller.failed()?status:controller.busy()?Client.text("ui.fetching_current_data_from_the_server_1e212f93"):kind.equals("players")?Client.text("ui.change_your_search_or_select_all_a75ea7ad"):Client.text("ui.entries_will_appear_here_after_refreshing_4d7e1609"),kind.equals("players")?nav.left()+8:(width-Math.min(620,UiPage.body(width,height).width()))/2+8,kind.equals("players")?playerRowsTop()+8:48,(kind.equals("players")?listWidth():Math.min(620,UiPage.body(width,height).width()))-16,height-70);if(!status.isEmpty()&&!entries.isEmpty())Ui.centered(g,font,status,width/2,height-66,AccessibilityScreen.foreground(UiPalette.color(0xEEEEEE)));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){controller.leave();ServerMenuClient.cancelReads(this);UiNavigation.back(this,parent);}
}
