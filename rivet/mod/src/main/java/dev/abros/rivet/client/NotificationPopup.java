package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Personal inbox overlay. Keeps loaded rows visible while refreshing each page. */
final class NotificationPopup extends ScrollScreen implements CommunityScreen.Receiver {
    private final Screen parent;
    private final dev.abros.rivet.core.PagedMenuController controller=new dev.abros.rivet.core.PagedMenuController("notifications",ServerMenuClient::request,System::currentTimeMillis);
    private JsonArray entries=new JsonArray();
    private List<String> sources=List.of();
    private String status="",category="",operation="";
    private boolean started,preferencesLoaded;
    private String initialNotice="";private JsonObject preferences=new JsonObject();

    NotificationPopup(Screen parent) { super(Client.tr("ui.notifications_ee3c35f3")); this.parent = parent; }
    NotificationPopup(Screen parent,String notice) { this(parent);initialNotice=notice; }
    void refreshVote(){rebuildWidgets();}
    private boolean activeVote(){return Json.opt(ServerMenuClient.moderationVote,"status","").equals("open")&&ServerMenuClient.moderationVote.has("endsAt")&&ServerMenuClient.moderationVote.get("endsAt").getAsLong()>System.currentTimeMillis();}
    void invalidate() { controller.invalidate(); }
    private int left() { return (width - panelWidth()) / 2; }
    private int top() { return Math.max(8, (height - 320) / 2); }
    private int bottom() { return Math.min(height - 8, top() + 320); }
    private int panelWidth() { return Math.min(410, width - 24); }

    @Override protected void init() {
        ModalLayer.prepare(parent,this);
        int x = left(), w = panelWidth(), y = top();
        var categories=new ArrayList<String>();categories.add("");categories.addAll(sources);if(!category.isEmpty()&&!categories.contains(category))categories.add(category);
        var labels=categories.stream().map(key->key.isEmpty()?Client.text("ui.all_notifications_605d7789"):CommunityScreen.name(key)).toList();addRenderableWidget(UiActions.button(Component.literal(labels.get(categories.indexOf(category))+" ▾"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ChoicePopup(this,Client.text("map.category"),labels,index->{if(controller.busy())return;category=categories.get(index);controller.reset();entries=controller.entries();resetScroll();send("list","");rebuildWidgets();},b).current(categories.indexOf(category)))).bounds(x+10,y+34,w-30,20).build());
        if(activeVote())addRenderableWidget(UiActions.button(Component.literal(font.plainSubstrByWidth(Client.text("ui.punishment_vote_23267740")+Json.opt(ServerMenuClient.moderationVote,"name",""),w-40)),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ModerationVoteScreen(this,null))).bounds(x+10,y+60,w-30,24).build());
        scrollArea(entries.size(),new dev.abros.rivet.core.NativeLayout.Box(x,y + (activeVote()?96:64),Math.max(0,w-10),Math.max(0,(bottom() - (controller.failed()?86:64))-(y + (activeVote()?96:64)))),40);
        for (int i = firstRow; i < Math.min(entries.size(), firstRow + visibleRows); i++) {
            var notice = entries.get(i).getAsJsonObject();
            addRenderableWidget(new NotificationRow(x+10,y+(activeVote()?96:64)+(i-firstRow)*40,w-30,notice,()->{if(controller.busy())return;controller.refresh();send("noticeOpen",Json.str(notice,"id"));}));
        }
        var footer=UiActions.row(new dev.abros.rivet.core.NativeLayout.Box(x+10,bottom()-52,w-20,20),this::addRenderableWidget,
            UiActions.action(Client.text("ui.mark_all_as_read_9ebe990e"),()->{if(controller.busy())return;controller.refresh();send("read","");},!controller.busy()&&java.util.stream.StreamSupport.stream(entries.spliterator(),false).anyMatch(e->!e.getAsJsonObject().get("read").getAsBoolean())),
            UiActions.action(Client.text("map.tool.settings"),()->minecraft.setScreen(new CommunityPreferences(this,preferences,sources)),preferencesLoaded));
        footer.get(1).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(preferencesLoaded?Client.text("ui.which_events_to_send_and_how_4c311e7a"):Client.text("ui.loading_settings_from_the_server_8c05c354"))));
        if(controller.failed()){var retry=UiActions.command(UiActions.Command.RETRY,new dev.abros.rivet.core.NativeLayout.Box(x+10,bottom()-76,w-20,20),this::addRenderableWidget,()->{if(controller.busy())return;if(operation.equals("noticeNavigate")||operation.equals("noticeOpen")){controller.refresh();send("list","");}else controller.retry();rebuildWidgets();});retry.active=!controller.busy();}
        UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(x+10,bottom()-28,w-20,20),this::addRenderableWidget,this::onClose);

        if (!started) { started = true; send(initialNotice.isEmpty()?"list":"noticeOpen", initialNotice);initialNotice=""; }
    }

    private void send(String op,String id){send(op,id,controller.page());}
    private void send(String op,String id,int page){
        operation=op;
        var packet=new JsonObject();packet.addProperty("category",category);packet.addProperty("action","community");
        packet.addProperty("section","notifications");packet.addProperty("op",op);packet.addProperty("id",id);
        controller.request(packet,page);
    }
    private void navigate(JsonObject notice) {
        minecraft.setScreen(this);
        if(controller.busy())return;
        controller.refresh();
        var packet=new JsonObject();packet.addProperty("action","community");packet.addProperty("section","notifications");
        operation="noticeNavigate";packet.addProperty("op","noticeNavigate");packet.add("id",notice.get("id"));if(notice.has("sourceRevision"))packet.add("sourceRevision",notice.get("sourceRevision"));
        controller.request(packet,0);
    }
    private void showNotice(JsonObject notice) {
        minecraft.setScreen(new TextScreen(this,Component.literal(Json.opt(notice,"title","")),Json.opt(notice,"body",""),true){
            @Override protected void actions(dev.abros.rivet.core.NativeLayout.Box area) {
                if(!notice.has("canNavigate")||!notice.get("canNavigate").getAsBoolean()){super.actions(area);return;}
                UiActions.row(area,this::addRenderableWidget,
                    UiActions.action(Client.text("ui.open_full_view_686862e2"),()->navigate(notice),true),
                    UiActions.action(Client.text("close"),this::onClose,true));
            }
        });
    }
    @Override public void receiveCommunity(JsonObject reply){
        boolean hadPreferences=preferencesLoaded,wasFailed=controller.failed();
        var result=controller.receive(reply);if(!result.accepted())return;
        if(result.failed()){status=controller.error();rebuildWidgets();return;}
        var metadata=controller.reply();if(metadata.has("preferences")&&metadata.get("preferences").isJsonObject()){preferences=metadata.getAsJsonObject("preferences");preferencesLoaded=true;}
        if(metadata.has("notificationCategories"))sources=metadata.getAsJsonArray("notificationCategories").asList().stream().map(JsonElement::getAsString).toList();
        entries=controller.entries();
        if(metadata.has("notice")){showNotice(metadata.getAsJsonObject("notice"));return;}
        if(metadata.has("noticeRoute")){
            var route=metadata.getAsJsonObject("noticeRoute");
            openRoute(route);return;
        }
        status = entries.isEmpty() ? (activeVote()?"":Client.text("ui.no_notifications_yet_f392d922")) : "";
        if(result.changed()||hadPreferences!=preferencesLoaded||wasFailed)rebuildWidgets();
    }

    private void openRoute(JsonObject route) {
        String section=Json.str(route,"section"),id=Json.str(route,"id");
        if(dev.abros.rivet.core.RetiredFeatures.contains(section)){status=Client.text("core.feature.retired");rebuildWidgets();return;}
        switch(section) {
            case "server" -> minecraft.setScreen(new ServerMenuScreen(this,"help"));
            case "map-publications" -> minecraft.setScreen(MapPublicationsScreen.own(this,UUID.fromString(id),Json.str(route,"type")));
            case "spark" -> minecraft.setScreen(new SparkDiagnosticsScreen(this));
            case "help" -> FeatureListScreen.openReport(this,id);
            default -> minecraft.setScreen(new CommunityScreen(this,section,id));
        }
    }

    private void more(){if(controller.nextAllowed()&&firstRow+visibleRows>=entries.size())send("list","",controller.loadedPage()+1);}
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        if (x < left() || x > left() + panelWidth()) return false;
        boolean used = super.mouseScrolled(x, y, dx, dy); if (dy < 0) more(); return used;
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        boolean used = super.keyPressed(key, scan, modifiers); if (key == 267 || key == 269) more(); return used;
    }
    @Override public void tick(){
        if(!ServerMenuClient.available()){minecraft.setScreen(null);return;}
        if(controller.timeout()){status=controller.error();rebuildWidgets();}
        else if(controller.refreshDue())send("list","",controller.page()+1);
        else if(controller.dirtyDue()){controller.refresh();send("list","");}
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && (x < left() || x > left() + panelWidth() || y < top() || y > bottom())) { onClose(); return true; }
        return super.mouseClicked(x, y, button);
    }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float d) {UiDialog.surface(g,left(),top(),panelWidth(),bottom()-top());}
    @Override public void render(GuiGraphics g, int x, int y, float d) {
        UiDialog.render(parent,this,g,d,()->{
            super.render(g,x,y,d);
            UiHeading.dialog(g,font,title,left()+12,top(),panelWidth()-24);
            if(!status.isEmpty()||controller.busy())Ui.text(g,font,font.plainSubstrByWidth(controller.busy()?Client.text("ui.updating_5c450e67"):status,panelWidth()-24),left()+12,top()+25,AccessibilityScreen.foreground(UiPalette.color(0xBAC7D2)));
        });
    }
    @Override public void onClose() { controller.leave();ServerMenuClient.cancelReads(this);if(parent instanceof CommunityScreen screen)screen.invalidate();else if(parent instanceof FeatureListScreen screen)screen.invalidate();minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
