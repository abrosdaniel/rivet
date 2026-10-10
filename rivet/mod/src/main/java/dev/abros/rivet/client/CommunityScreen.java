package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;

/** Community navigation, paged lists and contextual actions with request-bound responses. */
final class CommunityScreen extends ScrollScreen {
    interface Receiver {void receiveCommunity(JsonObject response);}
    record Row(String label,Runnable click,double progress,List<Row> buttons,Boolean checked){Row(String label,Runnable click,double progress,List<Row> buttons){this(label,click,progress,buttons,null);}Row(String label,Runnable click){this(label,click,-1,List.of());}Row(String label,Runnable click,double progress){this(label,click,progress,List.of());}}
    private final Screen parent;
    final String section;
    final String itemId;JsonObject invitationTarget;private String memberFilter="",sort="default";
    static void groupsFor(Screen parent,String member){var screen=new CommunityScreen(parent,"groups","");screen.memberFilter=member;net.minecraft.client.Minecraft.getInstance().setScreen(screen);}
    static void invite(Screen parent,JsonObject target){var screen=new CommunityScreen(parent,"groups","");screen.invitationTarget=target;net.minecraft.client.Minecraft.getInstance().setScreen(screen);}
    JsonObject data=new JsonObject();
    final List<Row> rows=new ArrayList<>();
    private final List<int[]> rowCards=new ArrayList<>();
    int beginCard(){return rows.size();}
    void endCard(int start){rowCards.add(new int[]{start,rows.size()});text("");}
    private void renderRowCards(GuiGraphics g,int x,int top,int w,int bottom){g.enableScissor(x-4,top,x+w+4,bottom);for(var card:rowCards){int y=top+(card[0]-firstRow)*24,end=top+(card[1]-firstRow)*24;if(end<=top||y>=bottom)continue;g.fill(x-4,y-2,x+w+4,end,AccessibilityScreen.background(UiKit.surface()));}g.disableScissor();}
    private final dev.abros.rivet.core.CommunityWorkspace model;
    void invalidate(){if(inlineParent!=null)inlineParent.model.invalidate();model.invalidate();for(var child:expanded.values())child.invalidate();}
    void invalidate(String topic,String id){if(!home()&&!topic.isEmpty()&&!topic.equals(section))return;if(!id.isEmpty()&&!itemId.isEmpty()&&!itemId.equals(id)&&!topic.equals("home"))return;model.invalidate();for(var child:expanded.values())if(id.isEmpty()||child.itemId.equals(id))child.invalidate(topic,id);}
    void cancelRead(){model.leave();ServerMenuClient.cancelReads(surface());}
    final Set<Integer> choices=new LinkedHashSet<>();
    private String query="";String status="";
    private boolean archive,trash,participating;String groupTab="overview";private boolean detailDragging;private double detailPosition;
    private final List<Row> moreActions=new ArrayList<>();private final Set<Integer> dangerousActions=new HashSet<>();
    boolean mine,loaded,quickAction,choicesDirty;private boolean controlsBusy;
    private EditBox search;private Button notificationButton;private final MenuSidebar navigation=new MenuSidebar(this);
    private CommunityScreen inlineParent;
    private final LinkedHashMap<String,CommunityScreen> expanded=new LinkedHashMap<>();
    Screen surface(){return inlineParent==null?this:inlineParent;}
    void refreshUi(){if(inlineParent!=null){inlineParent.refreshUi();return;}boolean typing=search!=null&&getFocused()==search;int cursor=typing?search.getCursorPosition():0;rebuildWidgets();if(typing&&search!=null){setFocused(search);search.setCursorPosition(cursor);}}
    private void prepareInline(CommunityScreen host){inlineParent=host;minecraft=host.minecraft;font=host.font;width=host.width;height=host.height;}
    private record Anchor(String id){}
    private Anchor anchor(){if(!data.has("entries"))return null;var entries=displayEntries();if(entries.isEmpty())return null;int n=Math.min(firstRow,entries.size()-1);return new Anchor(Json.str(entries.get(n).getAsJsonObject(),"id"));}
    private void restore(Anchor anchor){if(anchor==null||!data.has("entries"))return;int n=0;for(var entry:displayEntries()){if(Json.str(entry.getAsJsonObject(),"id").equals(anchor.id)){if(firstRow!=n){restoreScroll(n);refreshUi();}return;}n++;}}
    private UiListDetail masterDetail(){return UiListDetail.fit(UiWorkspace.fit(width,height).page(),230,height>=300);}
    private boolean splitLayout(){return inlineParent==null&&itemId.isEmpty()&&!home()&&masterDetail().split();}
    private int listWidth(){return masterDetail().list().width();}
    private int detailLeft(){return left()+listWidth()+12;}
    private CommunityScreen selected(){return expanded.isEmpty()?null:expanded.values().iterator().next();}
    private void inlineWidgets(int top){
        var entries=displayEntries();int bottom=height-(home()?40:splitLayout()?82:60),stride=home()&&height<320?56:AccessibilityScreen.cardStride();for(var entry:entries)stride=Math.max(stride,CommunityCard.height(font,entry.getAsJsonObject(),contentWidth()-12,stride-6)+6);stride=Math.min(stride,Math.max(36,bottom-top));scrollArea(entries.size(),new dev.abros.rivet.core.NativeLayout.Box(left(),top,Math.max(0,contentWidth()),Math.max(0,(bottom)-(top))),stride);
        for(int n=firstRow;n<Math.min(entries.size(),firstRow+visibleRows);n++){
            var entry=entries.get(n).getAsJsonObject();
            var card=new CommunityCard(left(),top+(n-firstRow)*stride,contentWidth(),stride-6,entry,()->openEntry(entry));card.selected=selected()!=null&&selected().itemId.equals(Json.str(entry,"id"));addRenderableWidget(card);
        }
        if(splitLayout())detailWidgets();
    }
    private void detailWidgets(){
        var child=selected();if(child==null)return;child.prepareInline(this);child.rows.clear();
        if(child.data.has("detail"))child.detail(child.data.getAsJsonObject("detail"));
        int x=detailLeft(),w=masterDetail().detail().width(),top=child.bodyTop();
        child.visibleRows=Math.max(1,(UiWorkspace.fit(width,height).page().bottom()-top-8)/24);child.firstRow=Math.max(0,Math.min(child.firstRow,Math.max(0,child.rows.size()-child.visibleRows)));
        child.detailControls(this,x,42,w);drawRowWidgets(child,x+8,top,w-16);
        var footer=UiPageFooter.fit(new dev.abros.rivet.core.NativeLayout.Box(x,height-54,w,20));
        if(child.model.uncertain())footer.start(UiActions.Command.RETRY,this::addRenderableWidget,child::retryOrLoad);else if(child.model.more())button(Client.text("ui.load_more_010963d6"),footer.start().x(),footer.start().y(),footer.start().width(),child::retryOrLoad);

    }
    private void retryOrLoad(){if(model.busy())return;if(model.uncertain())model.retry();else if(model.more())nextPage();else load();status=model.status();}

    private int contentBottom(){return height-(!itemId.isEmpty()&&inlineParent==null?58:82);}
    private int bodyTop(){return 42+(section.equals("groups")?76:48);}
    private void drawRowWidgets(CommunityScreen child,int x,int top,int w){
        for(int n=child.firstRow;n<Math.min(child.rows.size(),child.firstRow+child.visibleRows);n++){
            var row=child.rows.get(n);int y=top+(n-child.firstRow)*24;
            if(!row.buttons.isEmpty()){int bw=(w-6*(row.buttons.size()-1))/row.buttons.size();for(int i=0;i<row.buttons.size();i++){var action=row.buttons.get(i);var control=button(font.plainSubstrByWidth(action.label,bw-12),x+i*(bw+6),y,bw,()->{if(!child.model.busy())action.click.run();});control.setTooltip(control.getMessage().getString().equals(action.label)?null:Tooltip.create(Component.literal(action.label)));child.controlsBusy=child.model.busy();control.active=!child.model.busy();}}
            else if(row.checked!=null){var bar=new UiChoiceRow(x,y,w,row.label,row.checked,row.progress,0xFFB49AE8,()->{if(!child.model.busy()&&row.click!=null)row.click.run();});bar.active=!child.model.busy()&&row.click!=null;child.controlsBusy=child.model.busy();addRenderableWidget(bar);}
            else if(row.click!=null){int buttonWidth=w;var control=button(font.plainSubstrByWidth(row.label,buttonWidth-12),x,y,buttonWidth,()->{if(!child.model.busy())row.click.run();});control.setTooltip(control.getMessage().getString().equals(row.label)?null:Tooltip.create(Component.literal(row.label)));child.controlsBusy=child.model.busy();control.active=!child.model.busy();}
        }
    }
    private void detailControls(CommunityScreen host,int x,int y,int w){
        if(!moreActions.isEmpty())host.button(Client.text("ui.actions_928503bd"),x+w-90,y+4,82,this::showMore);
        if(section.equals("groups")&&data.has("detail")){
            boolean manage=data.getAsJsonObject("detail").get("manage").getAsBoolean();
            var tabs=plus()?manage?List.of("overview","members","tasks","events","places","requests"):List.of("overview","members","tasks","events","places"):manage?List.of("overview","members","requests"):List.of("overview","members");
            tabs=tabs.stream().filter(t->!t.equals("tasks")||ServerMenuClient.module("tasks")).filter(t->!t.equals("events")||ServerMenuClient.module("events")).filter(t->!t.equals("places")||data.getAsJsonObject("detail").get("isMember").getAsBoolean()||ServerMenuClient.admin()).toList();if(!tabs.contains(groupTab))groupTab="overview";final var visibleTabs=tabs;
            var labels=tabs.stream().map(CommunityScreen::groupTabLabel).toList();
            UiTabs.build(host,font,new dev.abros.rivet.core.NativeLayout.Box(x+8,y+40,w-16,20),labels,tabs.indexOf(groupTab),host::addRenderableWidget,n->{groupTab=visibleTabs.get(n);resetScroll();if(inlineParent!=null)inlineParent.detailPosition=0;refreshUi();},!model.busy());

        }
    }
    private static String groupTabLabel(String tab){return switch(tab){case "overview"->Client.text("ui.overview_39c63872");case "members"->Client.text("ui.members_fcb848e4");case "tasks"->Client.text("map.layer.tasks");case "events"->Client.text("map.layer.events");case "places"->Client.text("map.tool.markers");default->Client.text("ui.applications_be11d717");};}
    private void renderDetailPane(GuiGraphics g){
        int x=detailLeft(),w=masterDetail().detail().width();g.fill(x,42,x+w,UiWorkspace.fit(width,height).page().bottom(),AccessibilityScreen.background(UiKit.surface()));var child=selected();
        if(child==null){Ui.text(g,font,Client.text("ui.select_an_entry_b8c99587"),x+10,54,UiPalette.color(0xBAC6D2));return;}
        if(child.data.has("detail")){var j=child.data.getAsJsonObject("detail");Ui.text(g,font,font.plainSubstrByWidth(Json.str(j,"title"),w-104),x+8,50,UiKit.text(),false);Ui.text(g,font,font.plainSubstrByWidth(child.detailStatus(j),w-16),x+8,66,child.accent());}
        int top=child.bodyTop();child.renderRowCards(g,x+8,top,w-16,UiWorkspace.fit(width,height).page().bottom()-8);g.enableScissor(x,top,x+w,UiWorkspace.fit(width,height).page().bottom()-8);
        for(int n=child.firstRow;n<Math.min(child.rows.size(),child.firstRow+child.visibleRows);n++){var row=child.rows.get(n);if(row.click==null&&row.progress<0&&row.buttons.isEmpty()&&row.checked==null)UiParagraph.draw(g,font,row.label,x+8,top+(n-child.firstRow)*24+2,UiKit.text());}
        g.disableScissor();
        UiScrollbar.draw(g,dev.abros.rivet.core.ScrollLayout.fit(new dev.abros.rivet.core.NativeLayout.Box(x+8,top,Math.max(0,w-16),Math.max(0,UiWorkspace.fit(width,height).page().bottom()-8-top))).track(),child.visibleRows,child.rows.size(),child.firstRow);
        if(!child.status.isEmpty())Ui.status(g,font,child.status,x,height-78,w,height-56);
    }
    private String detailStatus(JsonObject j){return section.equals("groups")&&Json.str(j,"status").equals("open")?(j.has("recruiting")&&j.get("recruiting").getAsBoolean()?Client.text("ui.registration_open_4bd5a5ce"):Client.text("ui.registration_closed_a0d3acee")):status(Json.str(j,"status"));}
    private void scrollDetail(double delta){var child=selected();if(child==null)return;detailPosition=Math.max(0,Math.min(detailPosition+delta,Math.max(0,child.rows.size()-child.visibleRows)));child.firstRow=(int)detailPosition;refreshUi();if(child.firstRow+child.visibleRows>=child.rows.size())child.nextPage();}
    boolean leavePage(){if(!model.leave())return false;for(var child:expanded.values())if(!child.leavePage())return false;ServerMenuClient.cancelReads(surface());return true;}

    void refreshPermissions(){refreshUi();}
    private static final LocalizedValue<Map<String,String>> NAMES=new LocalizedValue<>(()->Map.ofEntries(Map.entry("profile",Client.text("ui.my_profile_88060502")),Map.entry("settings",Client.text("map.tool.settings")),Map.entry("home",Client.text("ui.home_ffddd31c")),Map.entry("tasks",Client.text("ui.my_tasks_a47c14c1")),Map.entry("players",Client.text("map.layer.players")),Map.entry("board",Client.text("ui.notice_board_dd5c5a3b")),Map.entry("groups",Client.text("ui.groups_903e08f2")),Map.entry("events",Client.text("map.layer.events")),Map.entry("polls",Client.text("ui.polls_8bd653c3")),Map.entry("ideas",Client.text("ui.suggestions_cc07b307")),Map.entry("notifications",Client.text("ui.notifications_ee3c35f3")),Map.entry("help",Client.text("map.tool.help")),Map.entry("admin",Client.text("server.tab.admin"))));
    CommunityScreen(Screen parent,String section,String id){super(Component.literal(name(section)));this.parent=parent;this.section=section;itemId=id;model=new dev.abros.rivet.core.CommunityWorkspace(section,id,ServerMenuClient::request,System::currentTimeMillis);}
    static String name(String section){if(section.equals("groups")&&ServerMenuClient.state.has("communityConfig"))return Json.opt(ServerMenuClient.state.getAsJsonObject("communityConfig"),"groupsTitle",NAMES.get().get(section));return NAMES.get().getOrDefault(section,section);}
    static void open(Screen parent,String section){net.minecraft.client.Minecraft.getInstance().setScreen(new CommunityScreen(parent,section,""));}
    static boolean plus(){return ServerMenuClient.supports("community-plus");}
    static String me(){return Json.opt(ServerMenuClient.state,"uuid","");}
    static boolean can(JsonObject item,String action){return item.has("actions")&&item.getAsJsonArray("actions").contains(new JsonPrimitive(action));}
    boolean owner(JsonObject j){return me().equals(Json.opt(j,"owner",""));}
    private int navigationLeft(){return navigation.left();}
    private int left(){if(inlineParent!=null)return inlineParent.detailLeft();return navigationLeft();}
    private int profileLeft(){return left()+contentWidth()+12;}
    private int controlsWidth(){return UiSearchToolbar.contentWidth(contentWidth());}
    private int homeTop(){return UiWorkspace.fit(width,height).page().y();}
    private boolean home(){return section.equals("home")&&itemId.isEmpty();}
    private boolean sideProfile(){return home()&&height>=360&&UiWorkspace.fit(width,height).page().right()-left()>=490;}
    private int contentWidth(){if(inlineParent!=null)return inlineParent.masterDetail().detail().width()-8;if(splitLayout())return listWidth();return UiWorkspace.fit(width,height).page().right()-left()-(sideProfile()?224:0);}
    private Button button(String label,int x,int y,int w,Runnable callback){return button(label,UiActions.Tone.NORMAL,"",x,y,w,callback);}
    private Button button(String label,UiActions.Tone tone,String icon,int x,int y,int w,Runnable callback){return addRenderableWidget(UiActions.button(Component.literal(label),tone,icon,b->callback.run()).bounds(x,y,w,20).build());}
    @Override protected void init(){controlsBusy=model.busy();
        if(data.has("detail")){rows.clear();detail(data.getAsJsonObject("detail"));}else if(data.has("entries")){rows.clear();list();}
        notificationButton=null;
        navigation.build(section,control->{addRenderableWidget(control);if(control.getMessage().getString().equals(MenuSidebar.inboxLabel(width)))notificationButton=control;});
        UiPageFooter.workspace(width,height).end(UiNavigation.exitCommand(this,parent),this::addRenderableWidget,this::onClose);
        if(itemId.isEmpty()&&!section.equals("home")){
            search=UiSearchToolbar.build(this,font,left(),42,controlsWidth(),query,mine?1:participating?2:0,sort,archive||trash,this::addRenderableWidget,v->query=v,this::resetList,n->{mine=n==1;participating=n==2;resetList();},v->{sort=v;resetList();},()->{query="";mine=false;participating=false;archive=false;trash=false;sort="default";resetList();refreshUi();},!model.busy());

        }
        if(home()){
            {
                button(Client.text("ui.search_the_server_41ae2416"),UiActions.Tone.NORMAL,UiIcons.SEARCH,left(),homeTop(),Math.max(1,controlsWidth()-30),()->minecraft.setScreen(new GlobalSearchScreen(this)));if(contentWidth()>=190)button("⋯",left()+controlsWidth()-24,homeTop(),24,this::profileActions).setTooltip(Tooltip.create(Client.tr("ui.profile_actions_e212452c")));
            }
            if(sideProfile()){
                int px=profileLeft(),by=homeTop()+ProfilePanel.actionsTop(data);
                var profileActions=new ArrayList<UiActions.Action>();
                if(SkinClient.available())profileActions.add(UiActions.action(Client.text("ui.skins_bef1a24f"),()->SkinsScreen.open(this),true));
                if(plus())profileActions.add(UiActions.action(Client.text("ui.about_me_312416bd"),()->minecraft.setScreen(new PersonalProfileScreen(this)),true));
                var tracks=new dev.abros.rivet.core.NativeLayout.Track[profileActions.size()];
                for(int n=0;n<tracks.length;n++)tracks[n]=n==tracks.length-1?dev.abros.rivet.core.NativeLayout.Track.flex(1):dev.abros.rivet.core.NativeLayout.Track.fixed(font.width(profileActions.get(n).label())+16);
                if(!profileActions.isEmpty())UiActions.row(new dev.abros.rivet.core.NativeLayout.Box(px+9,by,194,20),this::addRenderableWidget,tracks,profileActions.toArray(UiActions.Action[]::new));
                if(AuthClient.available())button(Client.text("ui.security_d4fab139"),px+9,by+24,194,()->AuthAccountScreen.open(this));
            }
        }
        int top=contentTop();
        if(cards()){
            inlineWidgets(top);
        }else{
            scrollArea(rows.size(),new dev.abros.rivet.core.NativeLayout.Box(left(),top,Math.max(0,contentWidth()),Math.max(0,(contentBottom())-(top))),24);
            drawRowWidgets(this,left()+12,top,contentWidth()-24);
            if(!itemId.isEmpty())detailControls(this,left(),40,contentWidth());else if(splitLayout())detailWidgets();
        }
        if(itemId.isEmpty()){
            if(!home()&&!section.equals("notifications"))button((trash?Client.text("ui.deleted_655b54d2"):archive?Client.text("ui.archive_3789b399"):Client.text("ui.active_6009f6cb"))+" ▾",left()+Math.max(0,controlsWidth()-(controlsWidth()-6)/2),toolbarBottom(),(controlsWidth()-6)/2,()->{if(!model.busy())minecraft.setScreen(new ChoicePopup(this,Client.text("ui.entries_596144b0"),List.of(Client.text("ui.active_6009f6cb"),Client.text("ui.archive_3789b399"),trashLabel()),i->{archive=i==1;trash=i==2;resetList();}).anchorLabel((trash?Client.text("ui.deleted_655b54d2"):archive?Client.text("ui.archive_3789b399"):Client.text("ui.active_6009f6cb"))+" ▾").current(trash?2:archive?1:0));});
            if(data.has("canCreate")&&data.get("canCreate").getAsBoolean()&&Set.of("board","groups","events","polls","ideas").contains(section))button(createLabel(),UiActions.Tone.PRIMARY,UiIcons.PLUS,left(),toolbarBottom(),(controlsWidth()-6)/2,this::create);
            if(section.equals("notifications"))button(Client.text("ui.mark_all_as_read_9ebe990e"),left(),height-54,125,()->send("read",new JsonObject()));
        }
        UiPageFooter.workspace(width,height).start(model.uncertain()?UiActions.Command.RETRY:UiActions.Command.REFRESH,this::addRenderableWidget,()->{if(model.uncertain()){model.retry();status=model.status();}else{load();if(selected()!=null)selected().load();}});
        if(!loaded){loaded=true;load();}
    }
    private int toolbarBottom(){return 42+UiSearchToolbar.height(controlsWidth(),height,!query.isBlank()||mine||participating||archive||trash||!sort.equals("default"));}
    private int contentTop(){return home()?homeTop()+28:itemId.isEmpty()?toolbarBottom()+(section.equals("notifications")?4:26):bodyTop();}
    private JsonArray displayEntries(){
        var result=new JsonArray();
        if(home()&&data.has("pinnedAnnouncement")){var pin=data.getAsJsonObject("pinnedAnnouncement");if(pin.get("until").getAsLong()>System.currentTimeMillis()&&!Json.str(pin,"text").isBlank()){
            var row=new JsonObject();row.addProperty("id","local:pinned");row.addProperty("section","home");row.addProperty("title",Client.text("ui.staff_announcement_7bd0856b"));row.addProperty("preview",Json.str(pin,"text"));row.addProperty("attention",Client.text("ui.pinned_3d631d0b"));row.addProperty("status",Client.text("ui.open_full_view_686862e2"));result.add(row);
        }}
        if(itemId.isEmpty()&&ModerationVoteScreen.enabled()&&(section.equals("polls")||home()&&Json.opt(ServerMenuClient.moderationVote,"status","").equals("open"))){var row=new JsonObject();row.addProperty("id","local:moderation-vote");row.addProperty("section","moderation");row.addProperty("title",Json.opt(ServerMenuClient.moderationVote,"status","").equals("open")?Client.text("ui.punishment_7386188a")+Json.opt(ServerMenuClient.moderationVote,"name",""):Client.text("ui.rule_violation_64cf60be"));row.addProperty("preview",Json.opt(ServerMenuClient.moderationVote,"status","").equals("open")?Json.opt(ServerMenuClient.moderationVote,"reason",""):Client.text("ui.kick_temporary_ban_or_voice_mute_433bad48"));row.addProperty("attention",Client.text("ui.rule_violations_f15f5499"));row.addProperty("footer",Json.opt(ServerMenuClient.moderationVote,"status","").equals("open")?Client.text("ui.vote_in_progress_open_f207bee6"):Client.text("ui.open_poll_c7a6cb96"));result.add(row);}
        if(data.has("entries")){if(home()){int count=0;for(var entry:data.getAsJsonArray("entries")){var e=entry.getAsJsonObject();if(Json.opt(e,"section","").equals("events")&&(e.has("isParticipant")&&e.get("isParticipant").getAsBoolean()||e.has("isOwner")&&e.get("isOwner").getAsBoolean()||e.has("isSubscribed")&&e.get("isSubscribed").getAsBoolean())&&count++<3)result.add(entry);}}else{for(var entry:data.getAsJsonArray("entries")){if(section.equals("polls")){var poll=entry.getAsJsonObject().deepCopy();poll.addProperty("attention",Client.text("ui.community_vote_b109fa33"));result.add(poll);}else result.add(entry);}}}
        if(home()){
            if(data.has("tasks"))for(var task:data.getAsJsonArray("tasks")){var t=task.getAsJsonObject();var row=t.deepCopy();row.addProperty("id","local:task:"+Json.str(t,"id"));row.addProperty("section","task");row.addProperty("preview",Json.opt(t,"groupName","")+" · "+Json.opt(t,"description",""));long due=t.get("dueAt").getAsLong();row.addProperty("attention",due>0?(due<System.currentTimeMillis()?Client.text("ui.overdue_474c16a8"):Client.text("ui.due_5da03408"))+local(due):Client.text("ui.my_task_710a072a"));result.add(row);}
            if(!sideProfile()){
                var profile=new JsonObject();profile.addProperty("id","local:profile");profile.addProperty("section","profile");profile.addProperty("title",Json.opt(ServerMenuClient.state,"name",Client.text("ui.my_profile_88060502")));profile.addProperty("attention",Client.text("ui.my_profile_88060502"));if(ServerMenuClient.state.has("sessionSeconds"))profile.addProperty("preview",Client.text("ui.session_c5cbab82")+ServerMenuClient.state.get("sessionSeconds").getAsLong()+Client.text("ui.s_488d98ca"));result.add(profile);

            }
        }if(home()){if(data.has("invitations"))for(var invitation:data.getAsJsonArray("invitations"))result.add(invitation);if(data.has("groups"))for(var group:data.getAsJsonArray("groups"))result.add(groupCard(group.getAsJsonObject()));return HomeLayout.arrange(result);}return result;
    }
    private boolean cards(){return itemId.isEmpty()&&!section.equals("notifications")&&data.has("entries");}
    private void openEntry(JsonObject entry){String id=Json.str(entry,"id");
        if(id.equals("local:profile")){profileActions();return;}
        if(id.startsWith("local:task:")&&TaskScreen.available()){minecraft.setScreen(new TaskScreen(this,Json.opt(entry,"group",""),id.substring("local:task:".length())));return;}if(id.startsWith("local:task:")){var group=new CommunityScreen(this,"groups",Json.str(entry,"group"));group.groupTab="tasks";minecraft.setScreen(group);return;}
        if(id.equals("local:pinned")){minecraft.setScreen(new TextScreen(this,Client.tr("ui.staff_announcement_7bd0856b"),Json.str(entry,"preview")+"\n\n"+Json.str(entry,"attention")));return;}
        if(id.equals("local:moderation-vote")){minecraft.setScreen(new ModerationVoteScreen(this,null));return;}
        if(splitLayout()&&selected()!=null&&selected().itemId.equals(id))return;
        var child=new CommunityScreen(this,Json.str(entry,"section"),id);child.invitationTarget=invitationTarget;
        if(!splitLayout()){minecraft.setScreen(child);return;}
        expanded.clear();detailPosition=0;child.prepareInline(this);expanded.put(id,child);child.loaded=true;child.load();refreshUi();
    }


    private String trashLabel(){return data.has("trashDays")?Client.text("ui.deleted_20a087d2")+data.get("trashDays").getAsInt()+Client.text("ui.d_2b44cb29"):Client.text("ui.deleted_655b54d2");}
    private String deleteMessage(){return data.has("trashDays")?Client.text("ui.delete_recovery_is_available_for_4de9c739")+data.get("trashDays").getAsInt()+Client.text("ui.d_2b44cb29"):Client.text("ui.delete_the_server_sets_the_recovery_488e2422");}
    static String statusLabel(String value){return status(value);}
    private String createLabel(){return switch(section){case "board"->Client.text("ui.post_95bcd6b5");case "groups"->Client.text("ui.create_84370a20");case "events"->Client.text("ui.assign_fe128585");case "polls"->Client.text("ui.create_84370a20");case "ideas"->Client.text("ui.suggest_5dd17ad8");default->Client.text("ui.create_84370a20");};}
    private String emptyText(){return switch(section){case "home"->Client.text("ui.you_have_not_joined_any_upcoming_0f0b3d0e");case "board"->Client.text("ui.no_notices_yet_select_post_notice_f1e20800");case "groups"->Client.text("ui.no_groups_yet_select_create_group_7169e325");case "events"->Client.text("ui.no_upcoming_events_new_events_will_e86a537e");case "polls"->Client.text("ui.no_votes_in_progress_7e4264b8");case "ideas"->Client.text("ui.no_suggestions_yet_select_suggest_idea_73490900");case "notifications"->Client.text("ui.all_caught_up_new_replies_and_c8c95631");default->Client.text("ui.no_entries_yet_3bbe9f96");};}
    private int accent(){return switch(section){case "board"->UiPalette.color(0xFFE2BE75);case "groups"->UiPalette.color(0xFF79CBA6);case "events"->UiPalette.color(0xFF82B6F2);case "polls"->UiPalette.color(0xFFB49AE8);case "ideas"->UiPalette.color(0xFFF0A77C);default->UiPalette.color(0xFF8BC7CB);};}
    private void profileActions(){var labels=new ArrayList<String>();var actions=new ArrayList<Runnable>();if(TaskScreen.available()){labels.add(Client.text("ui.my_tasks_a47c14c1"));actions.add(()->minecraft.setScreen(new TaskScreen(this,"","")));}if(ServerMenuClient.supports("player-tools")){if(ServerMenuClient.module("events")){labels.add(Client.text("ui.event_organizers_73a0c852"));actions.add(()->minecraft.setScreen(new FollowingScreen(this)));}labels.add(Client.text("ui.public_locations_c368f520"));actions.add(()->minecraft.setScreen(new PlacesScreen(this)));if(MapPositions.available()){labels.add(Client.text("ui.map_visibility_2edcda31"));actions.add(()->minecraft.setScreen(new MapSharingScreen(this)));}}if(plus()){labels.add(Client.text("ui.about_me_312416bd"));actions.add(()->minecraft.setScreen(new PersonalProfileScreen(this)));}if(SkinClient.available()){labels.add(Client.text("ui.skins_bef1a24f"));actions.add(()->SkinsScreen.open(this));}if(AuthClient.available()){labels.add(Client.text("ui.security_d4fab139"));actions.add(()->AuthAccountScreen.open(this));}minecraft.setScreen(new ChoicePopup(this,Client.text("ui.my_profile_88060502"),labels,i->actions.get(i).run()));}
    private JsonObject groupCard(JsonObject option){var row=new JsonObject();row.addProperty("id",Json.str(option,"value"));row.addProperty("section","groups");row.addProperty("title",Json.str(option,"label"));row.addProperty("attention",Client.text("ui.online_640a02fd")+dev.abros.rivet.core.DisplayCounts.text(option,"onlineCount","—")+" / "+dev.abros.rivet.core.DisplayCounts.text(option,"membersCount","—"));row.addProperty("preview",option.has("nextEvent")?Json.str(option,"nextEvent"):Client.text("ui.no_upcoming_events_d144f5f6"));row.addProperty("footer",option.has("applicationsCount")&&option.get("applicationsCount").getAsInt()>0?Client.text("ui.applications_8f40c1f4")+option.get("applicationsCount").getAsInt():option.has("nextEventAt")?local(option.get("nextEventAt").getAsLong()):Client.text("ui.open_group_63d4261f"));return row;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float d){
        super.renderBackground(g,x,y,d);
        if(splitLayout())renderDetailPane(g);
        if(home()&&sideProfile()){ProfilePanel.render(g,font,profileLeft(),homeTop(),212,true,data);}
        if(!data.has("detail")){
        }
        if(data.has("detail")){
            var item=data.getAsJsonObject("detail");g.fill(left(),40,left()+contentWidth(),rowCards.isEmpty()?contentBottom():bodyTop()-8,AccessibilityScreen.background(UiPalette.color(0xC51B252E)));g.fill(left(),40,left()+3,bodyTop()-8,accent());renderRowCards(g,left()+12,contentTop(),contentWidth()-24,contentBottom());
            String heading=name(section)+" › "+Json.str(item,"title");String info=Json.str(item,"author")+" · "+status(Json.str(item,"status"));
            if(section.equals("groups"))info=Json.str(item,"type")+" · "+item.get("membersCount").getAsInt()+Client.text("ui.members_acb4ce67");
            if(section.equals("events"))info=local(item.get("startsAt").getAsLong())+" · "+item.get("participantsCount").getAsInt()+Client.text("ui.members_acb4ce67");
            if(section.equals("polls"))info=Client.text("ui.ends_0b9a2337")+local(item.get("endsAt").getAsLong());
            if(section.equals("ideas"))info=status(Json.str(item,"status"))+" · "+item.get("supportersCount").getAsInt()+Client.text("ui.supporters_3ccdd6c2");
            Ui.text(g,font,font.plainSubstrByWidth(heading,contentWidth()-104),left()+10,46,UiKit.text(),false);Ui.text(g,font,font.plainSubstrByWidth(info,contentWidth()-20),left()+10,62,accent());
        }
    }
    private dev.abros.rivet.core.CommunityWorkspace.Filters filters(){return new dev.abros.rivet.core.CommunityWorkspace.Filters(query,memberFilter,sort,trash,archive,mine,participating);}
    private void resetList(){if(model.busy())return;model.reset();expanded.clear();resetScroll();load();}
    private void load(){model.load(filters());status=model.status();}
    private void nextPage(){model.next(filters());status=model.status();}
    void send(String op,JsonObject body){model.send(op,body,filters());status=model.status();}
    void receive(JsonObject response){
        if(inlineParent==null)for(var child:expanded.values())if(child.model.matches(response)){var anchor=anchor();child.receive(response);restore(anchor);return;}
        var result=model.receive(response);if(!result.accepted())return;status=model.status();
        if(result.failed()){quickAction=false;if(inlineParent!=null){inlineParent.status=status;refreshUi();}else if(!result.conflict())refreshUi();return;}
        if(quickAction){quickAction=false;model.invalidate();status=Client.text("ui.saved_f0dff5ab");return;}
        if(model.operation().equals("vote"))choicesDirty=false;
        boolean retainChoices=choicesDirty&&data.has("detail")&&section.equals("polls");
        var anchor=anchor();data=model.data();if(!result.changed()&&!controlsBusy)return;
        if(!retainChoices){choices.clear();if(data.has("detail")&&data.getAsJsonObject("detail").has("myVote"))for(var choice:data.getAsJsonObject("detail").getAsJsonArray("myVote"))choices.add(choice.getAsInt());}
        rows.clear();if(data.has("detail"))detail(data.getAsJsonObject("detail"));else list();refreshUi();restore(anchor);
    }
    void text(String text){for(String line:text.split("\n",-1)){if(line.isEmpty()){rows.add(new Row("",null));continue;}var lines=font.getSplitter().splitLines(line,Math.max(40,contentWidth()-40),net.minecraft.network.chat.Style.EMPTY);for(int n=0;n<lines.size();n+=2)rows.add(new Row(lines.get(n).getString()+(n+1<lines.size()?"\n"+lines.get(n+1).getString():""),null));}}
    void action(String name,Runnable action){rows.add(new Row(name,action));}
    void actionRow(List<Row> actions){
        if(actions.isEmpty())return;
        int available=contentWidth()-16;var group=new ArrayList<Row>();int used=0;
        for(var action:actions){int needed=font.width(action.label)+16;if(!group.isEmpty()&&(group.size()==3||(Math.max(used,needed)+4)*(group.size()+1)-4>available)){rows.add(new Row("",null,-1,List.copyOf(group)));group.clear();used=0;}group.add(action);used=Math.max(used,needed);}
        if(!group.isEmpty())rows.add(new Row("",null,-1,List.copyOf(group)));
    }

    private void list(){
        if(section.equals("polls")&&ModerationVoteScreen.enabled())action(Client.text("ui.rule_violation_player_vote_6d45eb7b"),()->minecraft.setScreen(new ModerationVoteScreen(this,null)));
        var entries=data.getAsJsonArray("entries");if(entries==null||entries.isEmpty()){text(query.isBlank()?(mine?Client.text("ui.you_have_no_entries_in_this_6366bd4e"):emptyText()):Client.text("ui.nothing_found_try_another_search_511084af"));return;}
        for(var e:entries){var j=e.getAsJsonObject();String label=Json.str(j,"title");if(section.equals("notifications")){label=(j.get("read").getAsBoolean()?"":"● ")+label;action(label,()->{model.readNotice(Json.str(j,"id"));String target=Json.opt(j,"target","");if(Json.str(j,"section").equals("help")&&!target.isEmpty()){FeatureListScreen.openReport(this,target);return;}if(target.isEmpty()){minecraft.setScreen(new ServerMenuScreen(this,"help"));return;}minecraft.setScreen(new CommunityScreen(this,Json.str(j,"section"),target));});}
            else action(label+" · "+status(Json.str(j,"status")),()->{var screen=new CommunityScreen(this,Json.str(j,"section"),Json.str(j,"id"));screen.invitationTarget=invitationTarget;minecraft.setScreen(screen);});}
    }
    static String status(String value){return switch(value){case "open"->Client.text("server.status.open");case "closed"->Client.text("ui.closed_fc98a6f2");case "new"->Client.text("ui.new_413a2483");case "discussion"->Client.text("ui.under_discussion_c2c53a20");case "planned"->Client.text("ui.scheduled_307b94ae");case "working"->Client.text("ui.in_progress_9db2ea4b");case "done"->Client.text("ui.completed_3bc74501");case "declined"->Client.text("ui.declined_b0a5f2d3");case "pending"->Client.text("ui.awaiting_response_dacc6a50");case "accepted"->Client.text("ui.accepted_32274481");case "cancelled"->Client.text("ui.cancelled_5ebee19f");case "hidden"->Client.text("ui.hidden_6bbdf8a9");case "deleted"->Client.text("ui.in_trash_5507d041");default->value;};}
    static String local(long epoch){return DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm z").format(Instant.ofEpochMilli(epoch).atZone(AccessibilityScreen.zone()));}
    void detail(JsonObject j){
        moreActions.clear();dangerousActions.clear();rowCards.clear();
        if(Json.str(j,"status").equals("deleted")){text(j.has("restoreUntil")?Client.text("ui.deleted_can_be_restored_until_55c23dd8")+local(j.get("restoreUntil").getAsLong()):Client.text("ui.deleted_the_server_sets_the_recovery_78190971"));if(can(j,"restore"))action(Client.text("ui.restore_29f3b29d"),()->send("restore",new JsonObject()));return;}
        if(!section.equals("groups")||groupTab.equals("overview")){int intro=beginCard();text(Json.str(j,"description"));if(Set.of("board","events","groups").contains(section))LocationActions.render(this,j);endCard(intro);}
        boolean manage=j.get("manage").getAsBoolean();
        switch(section){case "board" -> BoardSection.render(this,j);case "ideas" -> IdeasSection.render(this,j);case "polls" -> PollsSection.render(this,j);case "events" -> EventsSection.render(this,j);case "groups" -> GroupsSection.render(this,j);}
        if(data.has("related")&&(!section.equals("groups")||groupTab.equals("overview")||groupTab.equals("events")))for(var related:data.getAsJsonArray("related")){var child=related.getAsJsonObject();if(section.equals("groups")&&groupTab.equals("events")&&!Json.str(child,"section").equals("events"))continue;action(name(Json.str(child,"section"))+": "+Json.str(child,"title"),()->minecraft.setScreen(new CommunityScreen(surface(),Json.str(child,"section"),Json.str(child,"id"))));}
        if(plus())secondary(Client.text("ui.share_in_chat_dafa7f4e"),()->minecraft.setScreen(new ChatScreen("/rivet share "+section+" "+itemId)));
        if(can(j,"edit"))secondary(Client.text("ui.edit_901beb5f"),()->edit(j));
        else if(Set.of("board","events").contains(section)&&can(j,"location"))secondary(Client.text("ui.location_d1df6543"),()->{var preset=new JsonObject();preset.add("location",j.has("location")?j.get("location"):JsonNull.INSTANCE);form(Client.text("ui.location_8e2fe025"),"location",List.of(),preset);});
        if(!owner(j)&&ServerMenuClient.module("reports"))secondary(Client.text("ui.report_5405a3bc"),()->minecraft.setScreen(new ReportScreen(surface(),name(section)+": "+Json.str(j,"title")+" ["+itemId+"]\n",dev.abros.rivet.core.DraftIdentity.entryReport(section,ServerMenuClient.state.has("communityConfig")?Json.opt(ServerMenuClient.state.getAsJsonObject("communityConfig"),"groupsTitle",null):null,Json.str(j,"title"),itemId))));
        if(j.has("history"))secondary(Client.text("ui.change_history_b41a828c"),()->{var labels=new ArrayList<String>();for(var change:j.getAsJsonArray("history")){var h=change.getAsJsonObject();labels.add(local(h.get("at").getAsLong())+" · "+Json.str(h,"author")+" · "+operationLabel(Json.str(h,"operation")));}minecraft.setScreen(new ChoicePopup(surface(),Client.text("ui.recent_changes_9b6c1d4a"),labels,i->{var h=j.getAsJsonArray("history").get(i).getAsJsonObject();if(h.has("changes"))minecraft.setScreen(ChangePreviewScreen.history(surface(),h.getAsJsonArray("changes")));}));});
        if(can(j,"delete"))secondaryDanger(Client.text("ui.delete_f43320b0"),()->confirm(deleteMessage(),"delete",new JsonObject()));
        if(can(j,"hide"))secondary(Client.text("ui.hide_entry_26aa9823"),()->form(Client.text("ui.moderation_80ae616e"),"hide",List.of(new Field("text",Client.text("server.reason"),500)),new JsonObject()));
    }
    void secondary(String label,Runnable action){moreActions.add(new Row(label,action));}
    private void secondaryDanger(String label,Runnable action){dangerousActions.add(moreActions.size());secondary(label,action);}
    private void showMore(){var actions=List.copyOf(moreActions);var menu=new ChoicePopup(surface(),Client.text("ui.actions_9978ac34"),actions.stream().map(Row::label).toList(),i->actions.get(i).click.run()).anchorLabel(Client.text("ui.actions_928503bd"));for(int index:dangerousActions)menu.danger(index);minecraft.setScreen(menu);}
    private void edit(JsonObject item){var fields=new ArrayList<Field>();fields.add(new Field("title",Client.text("map.name"),100));fields.add(new Field("description",Client.text("ui.description_f5441f6a"),1500));var preset=new JsonObject();for(String key:List.of("title","description","type","location"))if(item.has(key))preset.add(key,item.get(key).deepCopy());if(section.equals("groups"))fields.add(new Field("type",Client.text("ui.type_d25691ca"),40,data.getAsJsonObject("config").getAsJsonArray("groupTypes")));if(plus()&&section.equals("board")){CommunityTools.creation(section,fields,preset);if(item.has("trade")){var offer=item.getAsJsonObject("trade");preset.addProperty("trade",Json.str(offer,"type"));for(String key:List.of("item","quantity","terms"))preset.addProperty(key,offer.get(key).getAsString());}}form(Client.text("ui.edit_entry_e99ceeeb"),"edit",fields,preset);}
    private static String operationLabel(String op){return switch(op){case "create"->Client.text("ui.created_aebeb613");case "edit"->Client.text("ui.edited_d1bb41ab");case "withdrawResponse"->Client.text("ui.response_withdrawn_5ea0b99e");case "withdrawApplication"->Client.text("ui.application_withdrawn_ef5e9e3e");case "revokeInvitation"->Client.text("ui.invitation_cancelled_bb1b66bb");case "delete"->Client.text("ui.deleted_af87b40b");case "restore"->Client.text("ui.restored_acea3dba");case "role"->Client.text("ui.leadership_role_changed_4cc3be83");default->Client.text("ui.updated_484f3ba9");};}
    String shortName(String uuid){if(uuid.equals(me()))return Json.opt(ServerMenuClient.state,"name",Client.text("ui.you_adb4032a"));if(data.has("names")&&data.getAsJsonObject("names").has(uuid))return data.getAsJsonObject("names").get(uuid).getAsString();return uuid.substring(0,8);}
    void confirm(String title,String op,JsonObject body){var dialog=new UiConfirmDialog(yes->{minecraft.setScreen(surface());if(yes)send(op,body);},Component.literal(title),Component.literal(data.has("detail")?Json.str(data.getAsJsonObject("detail"),"title"):""));if(op.equals("delete"))dialog.dangerous();minecraft.setScreen(dialog);}
    void form(String title,String op,List<Field> fields,JsonObject preset){var initial=preset.deepCopy();if(data.has("detail"))initial.add("revision",data.getAsJsonObject("detail").get("revision"));minecraft.setScreen(new CommunityForm(this,title,op,fields,initial,body->submitForm(op,body)));}
    private void submitForm(String op,JsonObject body){model.submitForm(op,body);}
    private void create(){var fields=new ArrayList<Field>();fields.add(new Field("title",Client.text("map.name"),100));fields.add(new Field("description",Client.text("ui.description_f5441f6a"),1500));var preset=new JsonObject();switch(section){
        case "board"->{fields.add(new Field("days",Client.text("ui.duration_days_1_90_b956ab4f"),2));preset.addProperty("days","14");var categories=data.getAsJsonObject("config").getAsJsonArray("categories");if(!categories.isEmpty()){var options=new JsonArray();var empty=new JsonObject();empty.addProperty("value","");empty.addProperty("label",Client.text("map.uncategorized"));options.add(empty);options.addAll(categories);fields.add(new Field("category",Client.text("map.category"),40,options));}}
        case "groups"->fields.add(new Field("type",Client.text("ui.type_d25691ca"),40,data.getAsJsonObject("config").getAsJsonArray("groupTypes")));
        case "events"->{fields.add(new Field("startsAt",Client.text("ui.start_local_time_90299f1c"),30));fields.add(new Field("capacity",Client.text("ui.capacity_0_up_to_300_01ae6a39"),3));fields.add(new Field("durationMinutes",Client.text("ui.duration_in_minutes_1_1440_7d964022"),4));preset.addProperty("durationMinutes","60");preset.addProperty("capacity","0");}
        case "polls"->{fields.add(new Field("endsAt",Client.text("ui.end_local_time_1f21347a"),30));fields.add(new Field("options",Client.text("ui.options_separated_by_2_8_298895d4"),808));for(String key:List.of("multiple","changeVote","liveResults")){var options=new JsonArray();for(boolean enabled:List.of(false,true)){var option=new JsonObject();option.addProperty("value",String.valueOf(enabled));option.addProperty("label",Client.text(enabled?"ui.yes_8d2fab2d":"ui.no_f82a8219"));options.add(option);}fields.add(new Field(key,key.equals("multiple")?Client.text("ui.multiple_choices_810a876c"):key.equals("changeVote")?Client.text("ui.allow_changing_votes_0a4ebc41"):Client.text("ui.show_results_immediately_3ca9d48f"),10,options));}}
    }if(plus())CommunityTools.creation(section,fields,preset);if(Set.of("board","events").contains(section)&&data.has("groups")&&!data.getAsJsonArray("groups").isEmpty()){var options=new JsonArray();var none=new JsonObject();none.addProperty("value","");none.addProperty("label",Client.text("ui.no_group_95c83efe"));options.add(none);options.addAll(data.getAsJsonArray("groups"));fields.add(new Field("group",Client.text("ui.group_4fb407c1"),40,options));}form(switch(section){case "board"->Client.text("ui.new_notice_466874b0");case "groups"->Client.text("ui.new_group_f7940191");case "events"->Client.text("ui.new_event_cb52ab8e");case "polls"->Client.text("ui.new_poll_e510ccd2");case "ideas"->Client.text("ui.new_suggestion_71a4e6df");default->Client.text("ui.new_entry_ee90723b");},"create",fields,preset);}
    static String optionValue(JsonElement option){return option.isJsonObject()?Json.str(option.getAsJsonObject(),"value"):option.getAsString();}
    static String optionLabel(JsonElement option){return option.isJsonObject()?Json.str(option.getAsJsonObject(),"label"):option.getAsString();}
    record Field(String key,String label,int limit,JsonArray options){Field(String key,String label,int limit){this(key,label,limit,null);}}
    @Override protected void onScrollEnd(){nextPage();}
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){
        if(navigation.scroll(x,y,dy)){refreshUi();return true;}
        if(splitLayout()&&x>=detailLeft()&&y>=42&&y<UiWorkspace.fit(width,height).page().bottom()){scrollDetail(-dy*3);return true;}
        return super.mouseScrolled(x,y,dx,dy);
    }
    @Override public boolean mouseClicked(double x,double y,int button){var child=selected();if(splitLayout()&&child!=null&&button==0&&x>=detailLeft()+masterDetail().detail().width()-14&&x<=detailLeft()+masterDetail().detail().width()-8&&y>=child.bodyTop()&&y<UiWorkspace.fit(width,height).page().bottom()-8){detailDragging=true;seekDetail(y);return true;}return super.mouseClicked(x,y,button);}
    private void seekDetail(double y){var child=selected();if(child==null)return;int track=UiWorkspace.fit(width,height).page().bottom()-8-child.bodyTop(),thumb=Math.max(12,track*child.visibleRows/Math.max(1,child.rows.size()));detailPosition=0;scrollDetail((y-child.bodyTop()-thumb/2.0)/Math.max(1,track-thumb)*Math.max(0,child.rows.size()-child.visibleRows));}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(detailDragging&&button==0){seekDetail(y);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){detailDragging=false;return super.mouseReleased(x,y,button);}
    boolean pendingMutation(){return model.busy()&&!model.reading()||expanded.values().stream().anyMatch(CommunityScreen::pendingMutation);}
    @Override boolean cancelInteraction(){if(pendingMutation())return false;if(super.cancelInteraction())return true;if(selected()!=null){expanded.clear();refreshUi();return true;}return false;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if((key==264||key==265)&&getFocused() instanceof CommunityCard focused&&focused.getX()==left()){var entries=displayEntries();int at=-1;for(int n=0;n<entries.size();n++)if(Json.str(entries.get(n).getAsJsonObject(),"id").equals(focused.targetId())){at=n;break;}if(at>=0){int next=Math.max(0,Math.min(entries.size()-1,at+(key==264?1:-1)));if(next<firstRow)restoreScroll(next);else if(next>=firstRow+visibleRows)restoreScroll(next-visibleRows+1);String target=Json.str(entries.get(next).getAsJsonObject(),"id");refreshUi();for(var c:children())if(c instanceof CommunityCard card&&card.targetId().equals(target)){setFocused(card);break;}return true;}}if((key==257||key==335)&&getFocused()==search){resetList();return true;}if(splitLayout()&&selected()!=null&&getFocused() instanceof AbstractWidget widget&&widget.getX()>=detailLeft()){if(key==266||key==267){scrollDetail((key==266?-1:1)*selected().visibleRows);return true;}}return super.keyPressed(key,scan,modifiers);}
    @Override public void render(GuiGraphics g,int x,int y,float d){super.render(g,x,y,d);UiHeading.page(g,font,Component.literal(name(section)),width);navigation.drawFrame(g);int top=contentTop();if(cards()&&displayEntries().isEmpty())UiState.draw(g,font,model.failed()||model.uncertain()?UiState.Kind.ERROR:model.busy()?UiState.Kind.LOADING:query.isBlank()&&!mine&&!participating?UiState.Kind.EMPTY:UiState.Kind.FILTERED,model.failed()||model.uncertain()?Client.text("ui.could_not_load_entries_ed210a71"):model.busy()?Client.text("ui.loading_entries_5de6d81f"):query.isBlank()?Client.text("ui.no_entries_yet_f40d3d7a"):Client.text("ui.nothing_found_1e1b70b1"),model.failed()||model.uncertain()?status:model.busy()?Client.text("ui.fetching_current_data_from_the_server_1e212f93"):query.isBlank()?(mine?Client.text("ui.you_have_no_entries_in_this_6366bd4e"):emptyText()):Client.text("ui.clear_or_change_your_search_bdd8ba67"),left()+8,top+8,contentWidth()-16,contentBottom());if(!cards())for(int n=firstRow;n<Math.min(rows.size(),firstRow+visibleRows);n++)if(rows.get(n).click==null&&rows.get(n).progress<0&&rows.get(n).buttons.isEmpty()&&rows.get(n).checked==null)UiParagraph.draw(g,font,rows.get(n).label,left()+12,top+(n-firstRow)*24+2,UiKit.text());Ui.status(g,font,status,left(),contentBottom()+4,contentWidth(),height-30);}
    @Override public void tick(){if(inlineParent==null)for(var child:expanded.values())child.tick();if(notificationButton!=null){int unread=ServerMenuClient.state.has("unread")?ServerMenuClient.state.get("unread").getAsInt():0;String caption=(width<420?Client.text("ui.inbox_e926c35f"):Client.text("ui.notifications_ee3c35f3"))+(unread>0?" ("+unread+")":"");if(!notificationButton.getMessage().getString().equals(caption))notificationButton.setMessage(Component.literal(caption));}if(!ServerMenuClient.available())minecraft.setScreen(null);else{boolean changed=model.tick(filters());if(changed){status=model.status();refreshUi();}}}
    @Override public void onClose(){if(!leavePage())return;model.close();ServerMenuClient.cancelReads(surface());if(parent instanceof CommunityScreen screen)screen.invalidate();UiNavigation.back(this,parent);}
    @Override public boolean isPauseScreen(){return false;}
}
