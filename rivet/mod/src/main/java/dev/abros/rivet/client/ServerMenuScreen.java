package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.core.RequestSession;
import dev.abros.rivet.core.UiPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.util.*;

final class ServerMenuScreen extends ScrollScreen implements CommunityScreen.Receiver {
    private final MenuSidebar nav=new MenuSidebar(this);private final Screen parent;private String tab="help",adminGroup="overview";private final ContentPane content=new ContentPane();
    
    ServerMenuScreen(Screen parent,String tab){super(Client.tr("server.menu"));this.parent=parent;this.tab=tab;}
    private String stateKey="";
    private final RequestSession overviewSession=new RequestSession();private JsonObject overviewData=new JsonObject();private boolean overviewLoaded,overviewBusy,refreshOnReturn;private String overviewNotice="";private Button overviewRefresh;
    private record Summary(String title,String value,List<String> details,int accent,Runnable action){}
    private final List<Summary> summaries=new ArrayList<>();
    private record DashboardSection(String title,List<Summary> items){}
    private final List<DashboardSection> dashboardSections=new ArrayList<>();
    private void section(String title){if(summaries.isEmpty())return;for(int i=0;i<summaries.size();i+=columns())dashboardSections.add(new DashboardSection(i==0?title:"",List.copyOf(summaries.subList(i,Math.min(summaries.size(),i+columns())))));summaries.clear();}

    private String metric(String key){return dev.abros.rivet.core.DisplayCounts.text(overviewData,key,overviewBusy?"…":"—");}
    private void group(String group){adminGroup=group;resetScroll();rebuildWidgets();}
    private void loadOverview(){if(overviewBusy||!ServerMenuClient.admin()||!ServerMenuClient.supports("player-tools"))return;overviewLoaded=true;overviewBusy=true;if(overviewRefresh!=null)overviewRefresh.active=false;overviewNotice=Client.text("ui.updating_summary_e06b0a85");var q=new JsonObject();q.addProperty("action","adminDashboard");ServerMenuClient.request(overviewSession.begin(q,false,System.currentTimeMillis()));}
    public void receiveCommunity(JsonObject response){if(!overviewSession.receive(response))return;overviewBusy=false;overviewNotice=response.has("error")?Json.opt(response,"text",Client.text("ui.summary_unavailable_f9423eba")):"";if(overviewRefresh!=null)overviewRefresh.active=true;if(!response.has("error")&&!UiPayload.same(overviewData,response)){overviewData=response.deepCopy();rebuildWidgets();}}

    private boolean stateFlag(String key){return ServerMenuClient.state.has(key)&&ServerMenuClient.state.get(key).getAsBoolean();}
    private boolean scheduled(){return ServerMenuClient.state.has("restartAt")&&ServerMenuClient.state.get("restartAt").getAsLong()>0;}
    private String modeKey(){return stateFlag("maintenance")+":"+scheduled()+":"+stateFlag("pinned")+":"+dev.abros.rivet.core.DisplayCounts.text(ServerMenuClient.state,"online","");}
    private int columns(){return nav.right()-nav.left()-14>=440?2:1;}
    private void card(String title,String value,String first,String second,int accent,Runnable action){summaries.add(new Summary(title,value,List.of(first,second),accent,action));}
    private void export(String section){var q=new JsonObject();q.addProperty("action","exportCommunity");q.addProperty("section",section);ServerMenuClient.request(q);}
    private String sectionTitle(){return switch(adminGroup){case "server"->Client.text("ui.server_management_08e01138");case "messages"->Client.text("server.notices");case "data"->Client.text("ui.export_data_1f42902c");default->Client.text("server.tab.admin");};}
    private void button(String key,int x,int y,int w,Runnable action){addRenderableWidget(UiActions.button(Client.tr(key),UiActions.Tone.NORMAL,"",b->action.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){
        nav.build(tab,this::addRenderableWidget);
        if(tab.equals("admin")&&!ServerMenuClient.staff()){CommunityScreen.open(parent,"home");return;}
        content.bounds(nav.left(),70,UiWorkspace.fit(width,height).page().width(),Math.max(30,height-145));
        if(tab.equals("help")){
            stateKey=stateFlag("hasLinks")+":"+Json.opt(ServerMenuClient.state,"help","");
            var actions=new ArrayList<UiActions.Action>();
            actions.add(UiActions.action(Client.text("ui.voice_diagnostics_0d7590bb"),()->minecraft.setScreen(new VoiceDiagnosticsScreen(this)),true));
            if(stateFlag("hasLinks"))actions.add(UiActions.action(Client.tr("server.links").getString(),()->FeatureListScreen.open(this,"links"),true));
            UiActions.row(new dev.abros.rivet.core.NativeLayout.Box(nav.left(),42,UiWorkspace.fit(width,height).page().width(),20),this::addRenderableWidget,actions.toArray(UiActions.Action[]::new));
            if(ServerMenuClient.module("reports"))UiActions.row(new dev.abros.rivet.core.NativeLayout.Box(nav.left(),height-58,nav.right()-nav.left()-20,20),this::addRenderableWidget,UiActions.action(Client.tr("server.report").getString(),()->minecraft.setScreen(new ReportScreen(this)),true),UiActions.action(Client.tr("server.myReports").getString(),()->FeatureListScreen.open(this,"myReports"),true));
        }

        if(tab.equals("admin")){
            int left=nav.left(),available=UiWorkspace.fit(width,height).page().width();
            boolean server=ServerMenuClient.may("rivet.announce")||ServerMenuClient.may("rivet.maintenance")||ServerMenuClient.may("rivet.restart");
            var groups=new ArrayList<String>(List.of("overview"));var tabLabels=new ArrayList<String>(List.of(Client.text("ui.overview_39c63872")));
            if(ServerMenuClient.may("rivet.reports")){groups.add("reports");tabLabels.add(Client.text("ui.support_617371c8"));}
            if(server||ServerMenuClient.admin()&&ServerMenuClient.supports("player-tools")){groups.add("server");tabLabels.add(Client.text("ui.server_917e0514"));}
            if(ServerMenuClient.may("rivet.diagnostics")){groups.add("diagnostics");tabLabels.add(Client.text("server.diagnostics"));}
            if(ServerMenuClient.admin()){groups.add("tools");tabLabels.add(Client.text("ui.tools_c0eca9e2"));}
            boolean allowedChild=adminGroup.equals("messages")&&ServerMenuClient.may("rivet.announce")||adminGroup.equals("data")&&ServerMenuClient.admin()&&ServerMenuClient.supports("admin-tools");
            if(!groups.contains(adminGroup)&&!allowedChild)adminGroup="overview";
            UiTabs.build(this,font,new dev.abros.rivet.core.NativeLayout.Box(left,42,available,20),tabLabels,Math.max(0,groups.indexOf(adminGroup)),this::addRenderableWidget,n->group(groups.get(n)),true);
            summaries.clear();stateKey=modeKey();
            dashboardSections.clear();
            if(Set.of("overview","reports","diagnostics","tools").contains(adminGroup)){
                if(Set.of("overview","reports").contains(adminGroup)){
                if(ServerMenuClient.may("rivet.reports"))dashboardSections.add(new DashboardSection(Client.text("ui.reports_summary_89cfcf75"),List.of()));
                if(ServerMenuClient.may("rivet.reports")){
                    if(overviewData.has("attentionReports"))for(var entry:overviewData.getAsJsonArray("attentionReports")){var report=entry.getAsJsonObject();card(Json.opt(report,"player",Client.text("map.tool.player")),Json.opt(report,"attentionReason",Client.text("ui.awaiting_response_dacc6a50")),Json.opt(report,"message",""),Json.opt(report,"assignedName","").isBlank()?Client.text("ui.no_assignee_b69f1a51"):Client.text("ui.assigned_to_31372c54")+Json.opt(report,"assignedName",""),0xFFEF7777,()->minecraft.setScreen(new ReportDetailScreen(this,report,true)));}
                    if(summaries.isEmpty()&&!metric("openReports").equals("0"))card(Client.text("ui.player_reports_2b875ea4"),overviewBusy?Client.text("server.loading"):metric("openReports").equals("0")?Client.text("ui.all_reports_have_been_processed_b2b9863e"):Client.text("ui.open_queue_3514fdc1"),Client.text("ui.assign_staff_and_reply_3b5dcd83"),Client.text("ui.open_and_closed_reports_b20552f3"),metric("openReports").equals("0")?0xFF79CBA6:0xFFEF7777,()->FeatureListScreen.open(this,"reports"));
                    section(Client.text("ui.player_reports_2b875ea4"));
                }
                }
                boolean rivetErrors=overviewData.has("recentErrors")&&!overviewData.getAsJsonArray("recentErrors").isEmpty();
                if(Set.of("overview","diagnostics").contains(adminGroup)&&rivetErrors)card(Client.text("ui.rivet_errors_72a769f5"),overviewData.getAsJsonArray("recentErrors").size()+Client.text("ui.recent_errors_8b705430"),Client.text("ui.open_diagnostics_64fa5b35"),Client.text("ui.details_and_log_29abf380"),0xFFEF7777,()->minecraft.setScreen(new AdminDashboardScreen(this,true)));
                if(adminGroup.equals("overview")&&server&&(stateFlag("maintenance")||scheduled())){card(Client.text("ui.server_status_6081a1b2"),stateFlag("maintenance")?Client.text("ui.maintenance_enabled_76d9d6ea"):Client.text("ui.shutdown_scheduled_a649930e"),Client.text("ui.check_mode_and_shutdown_time_f69fb895"),Client.text("ui.open_server_management_35d6f85a"),0xFFF0A77C,()->group("server"));}
                if(!summaries.isEmpty())section(Client.text("ui.needs_attention_1a25c7b2"));
                if(adminGroup.equals("diagnostics")){
                if(!rivetErrors&&ServerMenuClient.admin()&&ServerMenuClient.supports("player-tools"))card(Client.text("ui.rivet_status_201732d9"),overviewData.has("recentErrors")?overviewData.getAsJsonArray("recentErrors").isEmpty()?Client.text("ui.no_errors_recorded_2459ecdb"):overviewData.getAsJsonArray("recentErrors").size()+Client.text("ui.recent_errors_8b705430"):Client.text("ui.no_error_data_f7386050"),Client.text("ui.write_queue_88447342")+metric("storageQueue"),Client.text("ui.read_queue_bb4abd61")+metric("readQueue"),0xFFB49AE8,()->minecraft.setScreen(new AdminDashboardScreen(this,true)));
                else if(!rivetErrors&&ServerMenuClient.may("rivet.diagnostics"))card(Client.text("ui.rivet_status_201732d9"),Client.text("ui.connection_check_359e3a31"),Client.text("ui.mod_and_integration_status_2334cde5"),Client.text("ui.get_current_report_bfcd9f39"),0xFFB49AE8,()->ServerMenuClient.request("diagnostics"));
                if(ServerMenuClient.may("rivet.diagnostics")&&ServerMenuClient.supports("spark-diagnostics"))card(Client.text("ui.performance_2b543b00"),"spark",Client.text("ui.tps_mspt_cpu_and_memory_b29d9965"),Client.text("ui.profiling_and_report_history_8e946b7d"),0xFF79CBA6,()->minecraft.setScreen(new SparkDiagnosticsScreen(this)));
                if(!summaries.isEmpty())section(Client.text("ui.server_and_diagnostics_08e852ff"));
                }
                if(adminGroup.equals("tools")){
                if(ServerMenuClient.admin()&&ServerMenuClient.supports("community-extensions"))card(Client.text("ui.role_permissions_950366f4"),Client.text("ui.preview_5d092ca8"),Client.text("ui.available_sections_and_actions_3b04cc4d"),Client.text("ui.keep_your_current_role_ef14dd6f"),0xFFB49AE8,()->minecraft.setScreen(new RolePreviewScreen(this)));
                if(ServerMenuClient.admin())card(Client.text("server.history"),Client.text("ui.administration_history_fe068195"),Client.text("ui.who_changed_what_1b3846c2"),Client.text("ui.open_entries_and_details_11abebc8"),0xFF82B6F2,()->FeatureListScreen.open(this,"history"));
                if(ServerMenuClient.admin()&&ServerMenuClient.supports("admin-tools"))card(Client.text("ui.community_data_5767255c"),Client.text("map.tool.export"),Client.text("ui.select_a_section_b85ade23"),Client.text("ui.export_server_data_613ac09c"),0xFFE2BE75,()->group("data"));
                if(!summaries.isEmpty())section(Client.text("ui.administration_tools_08646444"));
                }
            }else if(adminGroup.equals("server")){
                if(ServerMenuClient.may("rivet.maintenance"))card(Client.text("ui.player_admission_9f34090a"),stateFlag("maintenance")?Client.text("ui.maintenance_enabled_76d9d6ea"):Client.text("ui.open_3568fc61"),stateFlag("maintenance")?Json.opt(ServerMenuClient.state,"maintenanceReason",""):Client.text("ui.players_can_connect_55367309"),stateFlag("maintenance")?Client.text("ui.click_to_finish_98a53d46"):Client.text("ui.enable_maintenance_with_a_reason_1e4955c5"),0xFF79CBA6,()->action(stateFlag("maintenance")?"maintenanceOff":"maintenance"));
                if(ServerMenuClient.may("rivet.restart"))card(Client.text("ui.server_shutdown_7c5d4a79"),scheduled()?Client.text("ui.scheduled_411fa3b0"):Client.text("ui.not_scheduled_8c3235ad"),Client.text("ui.the_control_panel_handles_restarting_9eea9645"),scheduled()?Client.text("ui.cancel_scheduled_shutdown_3917f53c"):Client.text("ui.specify_shutdown_time_and_reason_42f5e7f2"),0xFFEF7777,()->action(scheduled()?"restartOff":"restart"));
                if(ServerMenuClient.may("rivet.announce"))card(Client.text("server.notices"),stateFlag("pinned")?Client.text("ui.pinned_announcement_present_55663a5e"):Client.text("ui.nothing_pinned_d0f60577"),Client.text("ui.one_time_message_to_all_players_2cbae856"),Client.text("ui.message_on_rivet_home_78266f23"),0xFFE2BE75,()->group("messages"));
                if(ServerMenuClient.admin()&&ServerMenuClient.supports("player-tools"))card(Client.text("ui.server_settings_7b60e7b4"),Client.text("ui.view_and_edit_cd3f5657"),Client.text("ui.review_values_before_applying_f5f6dd3e"),Client.text("ui.changes_requiring_confirmation_4cfcc600"),0xFF82B6F2,()->minecraft.setScreen(new ServerSettingsReviewScreen(this)));
            }else if(adminGroup.equals("messages")&&ServerMenuClient.may("rivet.announce")){
                if(ServerMenuClient.supports("scheduled-announcements"))card(Client.text("ui.scheduled_announcements_d35ca997"),Client.text("ui.scheduled_publication_ee9c275c"),Client.text("ui.preview_text_and_time_before_saving_27e44a30"),Client.text("ui.deliver_to_online_players_without_duplicates_031f1da4"),0xFF82B6F2,()->minecraft.setScreen(new ScheduledAnnouncementsScreen(this)));
                card(Client.text("ui.one_time_announcement_36a5c3e1"),Client.text("ui.send_to_players_21498519"),Client.text("ui.the_text_will_appear_for_all_3651b774"),Client.text("ui.message_and_send_confirmation_6a410587"),0xFFE2BE75,()->action("announce"));
                if(ServerMenuClient.supports("admin-tools")){
                    card(Client.text("ui.home_message_649f3874"),stateFlag("pinned")?Client.text("ui.edit_pinned_announcement_b147559a"):Client.text("ui.pin_announcement_80eb82c5"),stateFlag("pinned")?Json.opt(ServerMenuClient.state,"pinnedText",""):Client.text("ui.shown_on_rivet_home_b734e543"),Client.text("ui.enter_the_text_and_display_duration_fc0cdb42"),0xFF79CBA6,()->action("pinAnnouncement"));
                    if(stateFlag("pinned"))card(Client.text("ui.unpin_announcement_55fbffbb"),Client.text("ui.remove_from_home_95df54e6"),Client.text("ui.the_current_message_will_no_longer_53b5813c"),Client.text("ui.this_action_requires_confirmation_351eb8e8"),0xFFEF7777,()->minecraft.setScreen(new UiConfirmDialog(yes->{minecraft.setScreen(this);if(yes){var q=new JsonObject();q.addProperty("action","pinAnnouncement");q.addProperty("text","");q.addProperty("minutes",1);ServerMenuClient.request(q);}},Client.tr("ui.unpin_announcement_3ca71e70"),Client.tr("ui.the_message_will_disappear_from_rivet_36fcc526"))));
                }
            }else if(adminGroup.equals("data")&&ServerMenuClient.admin()&&ServerMenuClient.supports("admin-tools")){
                String[] sections={"","board","groups","events","polls","ideas"},labels={Client.text("ui.all_sections_35239848"),Client.text("ui.notice_board_dd5c5a3b"),Client.text("ui.groups_903e08f2"),Client.text("map.layer.events"),Client.text("ui.polls_8bd653c3"),Client.text("ui.suggestions_cc07b307")};
                for(int i=0;i<sections.length;i++){String section=sections[i];card(labels[i],Client.text("ui.export_data_fb991cb8"),Client.text("ui.export_selected_section_c64d2301"),Client.text("ui.the_file_is_created_on_the_33f8f6b9"),0xFFE2BE75,()->export(section));}
            }
            if(Set.of("overview","reports").contains(adminGroup)&&available<420){
                var responsive=new ArrayList<DashboardSection>();
                for(var section:dashboardSections)if(!section.items.isEmpty())responsive.add(section);else{
                    String[] keys={"openReports","unassignedReports","highReports"},labels={Client.text("ui.open_reports_1d5f9766"),Client.text("ui.unassigned_72af7971"),Client.text("ui.high_priority_9ad02c50")};
                    for(int i=0;i<keys.length;i++){String key=keys[i];responsive.add(new DashboardSection(i==0?section.title:"",List.of(new Summary(labels[i],metric(key),List.of(Client.text("ui.open_report_queue_83b21af8")),UiKit.ACCENT,()->minecraft.setScreen(new ReportQueueScreen(this,key.equals("unassignedReports")?"unassigned":key.equals("highReports")?"high":"all"))))));}
                }
                dashboardSections.clear();dashboardSections.addAll(responsive);
            }
            int columns=columns(),tileWidth=(available-8*(columns-1))/columns;
            if(Set.of("overview","reports","diagnostics","tools").contains(adminGroup)){
                scrollArea(dashboardSections.size(),new dev.abros.rivet.core.NativeLayout.Box(left,74,Math.max(0,available),Math.max(0,(height-64)-(74))),86);
                for(int row=firstRow;row<Math.min(dashboardSections.size(),firstRow+visibleRows);row++){
                    int y=74+(row-firstRow)*86;var section=dashboardSections.get(row);
                    if(section.items.isEmpty()){String[] keys={"openReports","unassignedReports","highReports"},labels={Client.text("ui.open_reports_1d5f9766"),Client.text("ui.unassigned_72af7971"),Client.text("ui.high_priority_9ad02c50")};int statWidth=(available-8)/3;for(int i=0;i<3;i++){String key=keys[i];if(ServerMenuClient.may("rivet.reports"))addRenderableWidget(UiMetricCard.compact(left+i*(statWidth+4),y+18,statWidth,labels[i],metric(key),()->minecraft.setScreen(ServerMenuClient.supports("community-extensions")?new ReportQueueScreen(this,key.equals("unassignedReports")?"unassigned":key.equals("highReports")?"high":"all"):new FeatureListScreen(this,"reports"))));}if(ServerMenuClient.may("rivet.reports"))addRenderableWidget(UiActions.button(Client.tr("ui.all_reports_f7b1086e"),UiActions.Tone.NORMAL,"",b->FeatureListScreen.open(this,"reports")).bounds(left,y+60,110,20).build());continue;}
                    int count=section.items.size(),cardWidth=(available-8*(Math.min(count,columns)-1))/Math.max(1,Math.min(count,columns));
                    for(int n=0;n<Math.min(count,columns);n++){var item=section.items.get(n);addRenderableWidget(UiSummaryCard.compact(left+n*(cardWidth+8),y+18,cardWidth,item.title,item.value,item.details,item.accent,item.action));}
                }
                if(ServerMenuClient.admin()&&ServerMenuClient.supports("player-tools")){overviewRefresh=UiPageFooter.workspace(width,height).start(UiActions.Command.REFRESH,this::addRenderableWidget,this::loadOverview);overviewRefresh.active=!overviewBusy;}
            }else{
                scrollArea((summaries.size()+columns-1)/columns,new dev.abros.rivet.core.NativeLayout.Box(left,76,Math.max(0,available),Math.max(0,(height-64)-(76))),96);
                for(int n=firstRow*columns;n<Math.min(summaries.size(),(firstRow+visibleRows)*columns);n++){var item=summaries.get(n);addRenderableWidget(new UiSummaryCard(left+n%columns*(tileWidth+8),76+(n/columns-firstRow)*96,tileWidth,item.title,item.value,item.details,item.accent,item.action));}
            }

        }
        UiPageFooter.workspace(width,height).end(tab.equals("admin")&&!adminGroup.equals("overview")?UiActions.Command.BACK:UiNavigation.exitCommand(this,parent),this::addRenderableWidget,this::onClose);
        if(tab.equals("admin")&&adminGroup.equals("overview")&&(!overviewLoaded||refreshOnReturn)){refreshOnReturn=false;loadOverview();}
    }
    private void action(String name){
        if(name.equals("maintenanceOff")||name.equals("restartOff")){
            var j=new JsonObject();j.addProperty("text","");
            if(name.equals("maintenanceOff")){j.addProperty("action","maintenance");j.addProperty("enabled",false);j.addProperty("minutes",0);}
            else {j.addProperty("action","restart");j.addProperty("seconds",0);}
            minecraft.setScreen(new UiConfirmDialog(yes->{minecraft.setScreen(this);if(yes)ServerMenuClient.request(j);},Client.tr("server.confirm"),Client.tr("server."+name)));
        }else minecraft.setScreen(new ActionForm(this,name));
    }
    @Override public void removed(){refreshOnReturn=true;}
    void refreshPermissions(){if(!ServerMenuClient.admin()){overviewSession.cancel();overviewBusy=false;overviewData=new JsonObject();}rebuildWidgets();}
    @Override public void tick(){if(tab.equals("admin")&&!ServerMenuClient.staff()){CommunityScreen.open(parent,"home");}else if(tab.equals("admin")&&!stateKey.equals(modeKey())||tab.equals("help")&&!stateKey.equals(stateFlag("hasLinks")+":"+Json.opt(ServerMenuClient.state,"help","")))rebuildWidgets();if(overviewSession.timeout(System.currentTimeMillis())){overviewBusy=false;overviewNotice=Client.text("ui.no_response_refresh_the_summary_84f5f501");if(overviewRefresh!=null)overviewRefresh.active=true;}}
    private String body(){var j=ServerMenuClient.state;
        if(tab.equals("help"))return Json.opt(j,"help","")+"\n\n"+Client.tr("server.helptext").getString();
        return Client.tr("server.admintext").getString();
    }
    @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);UiHeading.page(g,font,Component.literal(tab.equals("admin")?sectionTitle():Client.text("map.tool.help")),width);nav.drawFrame(g);content.text(body());if(!tab.equals("admin"))content.render(g,font);
        if(tab.equals("admin")&&Set.of("overview","reports","diagnostics","tools").contains(adminGroup))for(int row=firstRow;row<Math.min(dashboardSections.size(),firstRow+visibleRows);row++)Ui.text(g,font,UiKit.fit(font,dashboardSections.get(row).title,nav.right()-nav.left()-20),nav.left(),74+(row-firstRow)*86,UiKit.muted(),false);
        Ui.status(g,font,adminGroup.equals("overview")&&!overviewNotice.isEmpty()?overviewNotice:ServerMenuClient.result,nav.left(),height-48,nav.right()-nav.left()-20,height-32);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(!tab.equals("admin")&&(key==266||key==267)){content.scroll(nav.left()+8,106,key==266?6:-6);return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){if(nav.scroll(x,y,dy)){rebuildWidgets();return true;}return !tab.equals("admin")&&content.scroll(x,y,dy)||super.mouseScrolled(x,y,dx,dy);}
    @Override public boolean mouseClicked(double x,double y,int b){if(b==0){if(content.click(x,y))return true;var style=content.link(font,x,y);if(style!=null&&style.getClickEvent()!=null)return handleComponentClicked(style);}return super.mouseClicked(x,y,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return b==0&&content.drag(y)||super.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int b){content.release();return super.mouseReleased(x,y,b);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){if(tab.equals("admin")&&!adminGroup.equals("overview")){group(adminGroup.equals("messages")?"server":"overview");return;}overviewSession.cancel();UiNavigation.back(this,parent);}

    private static final class ActionForm extends Screen {
        private final Screen parent;private final String action;private EditBox message,duration;private String error="";private boolean urgent;
        ActionForm(Screen parent,String action){super(action.equals("pinAnnouncement")?Client.tr("ui.pinned_announcement_1e14a64d"):Client.tr("server."+action));this.parent=parent;this.action=action;}
        private int top(){return UiDialog.top(height,240);}
        private int bottom(){return height-top();}
        private Component durationLabel(){return action.equals("pinAnnouncement")?Client.tr("ui.minutes_1_10080_70d3c704"):Client.tr(action.equals("restart")?"server.seconds":"server.minutes");}
        private boolean timed(){return !action.equals("announce");}
        @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,Math.min(420,width-40),top(),bottom());}
 @Override protected void init(){
            int w=Math.min(420,width-40),x=(width-w)/2;
            String draft=message==null?(action.equals("pinAnnouncement")?Json.opt(ServerMenuClient.state,"pinnedText",""):""):message.getValue(),value=duration==null?(action.equals("maintenance")?"15":"60"):duration.getValue();
            message=addRenderableWidget(UiFields.text(font,x,top()+54,w,20,Client.tr("server.message")));message.setMaxLength(500);message.setValue(draft);
            if(action.equals("announce"))addRenderableWidget(new UiToggle(Client.text("ui.critical_warning_2256a476"),x,top()+88,w,urgent,()->{urgent=!urgent;rebuildWidgets();}));
            if(timed()){duration=addRenderableWidget(UiFields.text(font,x,top()+98,100,20,durationLabel()));duration.setMaxLength(5);duration.setFilter(v->v.matches("[0-9]*"));duration.setValue(value);}
            addRenderableWidget(UiActions.button(Client.tr("server.confirm"),UiActions.Tone.NORMAL,"",b->submit()).bounds(x,bottom()-28,w/2-3,20).build());
            UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(x+w/2+3,bottom()-28,w/2-3,20),this::addRenderableWidget,this::onClose);
            setInitialFocus(message);
        }
        private void submit(){
            int amount=0;if(timed())try{amount=Integer.parseInt(duration.getValue());if(amount<(action.equals("maintenance")?0:1)||amount>(action.equals("maintenance")?1440:action.equals("pinAnnouncement")?10080:86400))throw new NumberFormatException();}catch(NumberFormatException ex){error=Client.tr("server.invalidnumber").getString();return;}
            if(message.getValue().isBlank()){error=Client.tr("server.reportempty").getString();return;}
            var j=new JsonObject();j.addProperty("action",action);j.addProperty("text",message.getValue().strip());if(action.equals("announce"))j.addProperty("urgent",urgent);
            if(action.equals("pinAnnouncement"))j.addProperty("minutes",amount);else if(action.equals("maintenance")){j.addProperty("enabled",true);j.addProperty("minutes",amount);}else if(action.equals("restart"))j.addProperty("seconds",amount);
            Component summary=Component.literal(message.getValue());if(timed())summary=summary.copy().append(" · "+amount+(action.equals("restart")?Client.text("ui.s_488d98ca"):Client.text("ui.min_04530f57")));
            minecraft.setScreen(new UiConfirmDialog(yes->{minecraft.setScreen(yes?parent:this);if(yes)ServerMenuClient.request(j);},title,summary));
        }
        @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);int left=(width-Math.min(420,width-40))/2;
            UiHeading.dialog(g,font,title,left,top(),Math.min(420,width-40));Ui.text(g,font,Client.tr("server.message"),left,top()+40,AccessibilityScreen.foreground(UiPalette.color(0xEEEEEE)));
            if(timed())Ui.text(g,font,durationLabel(),left,top()+84,AccessibilityScreen.foreground(UiPalette.color(0xEEEEEE)));
            Ui.status(g,font,error.isEmpty()?(action.equals("pinAnnouncement")?Client.text("ui.the_message_will_appear_on_the_8ba213b7"):Client.tr("server.form."+action).getString()):error,left,top()+(timed()?130:122),Math.min(420,width-40),bottom()-36);
        });}
        @Override public void tick(){if(!ServerMenuClient.available())minecraft.setScreen(null);else if(!ServerMenuClient.may("rivet."+(action.equals("pinAnnouncement")?"announce":action)))UiNavigation.back(this,parent);}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void onClose(){UiNavigation.back(this,parent);}
    }

}
