package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
/** Read-only role inspection: never changes the administrator's identity or rights. */
final class RolePreviewScreen extends ScrollScreen implements CommunityScreen.Receiver {
 private final Screen parent;private final RequestSession session=new RequestSession();private JsonArray roles=new JsonArray();private boolean loaded,busy;private int selected;private String notice="";
 RolePreviewScreen(Screen parent){super(Client.tr("ui.role_permissions_preview_e35dff84"));this.parent=parent;}
 private int w(){return Math.min(430,width-32);}private int x(){return (width-w())/2;}private int top(){return UiDialog.top(height,350);}private int end(){return height-top();}
 private void load(){var j=new JsonObject();j.addProperty("action","rolePreview");busy=true;ServerMenuClient.request(session.begin(j,false,System.currentTimeMillis()));}
 @Override protected void init(){if(!roles.isEmpty()){selected=Math.min(selected,roles.size()-1);var role=roles.get(selected).getAsJsonObject();addRenderableWidget(UiActions.button(Component.literal(Json.str(role,"name")+" ▾"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ChoicePopup(this,Client.text("ui.select_a_role_abb10714"),roles.asList().stream().map(e->Json.str(e.getAsJsonObject(),"name")).toList(),n->{selected=n;resetScroll();rebuildWidgets();},b).current(selected))).bounds(x()+12,top()+36,UiSearchToolbar.contentWidth(w()-24),20).build());var caps=role.getAsJsonObject("capabilities");var keys=new ArrayList<>(caps.keySet());scrollArea(keys.size(),new dev.abros.rivet.core.NativeLayout.Box(x()+12,top()+70,Math.max(0,w()-24),Math.max(0,(end()-72)-(top()+70))),26);for(int n=firstRow;n<Math.min(keys.size(),firstRow+visibleRows);n++){String key=keys.get(n);addRenderableWidget(new UiChoiceRow(x()+12,top()+70+(n-firstRow)*26,w()-24,label(key),caps.get(key).getAsBoolean(),-1,UiKit.ACCENT,()->{})).active=false;}}
  UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(x(),end()-28,w()-12,20),this::addRenderableWidget,this::onClose);if(!loaded){loaded=true;load();}}
 private static String label(String key){return switch(key){case "rivet.admin"->Client.text("ui.full_administration_3b947dd3");case "rivet.events"->Client.text("ui.event_creation_55ac688b");case "rivet.auth.reset"->Client.text("ui.reset_authentication_2201ab24");case "rivet.vote.protected"->Client.text("ui.protection_from_punishment_votes_2de328f6");case "rivet.stats.edit"->Client.text("ui.statistics_changed_2cbf7371");case "rivet.stats.view"->Client.text("ui.view_statistics_a6589a22");case "rivet.announce"->Client.text("server.notices");case "rivet.maintenance"->Client.text("ui.server_maintenance_7969d865");case "rivet.restart"->Client.text("ui.server_shutdown_7c5d4a79");case "rivet.reports"->Client.text("ui.report_management_3315c826");case "rivet.diagnostics"->Client.text("ui.client_diagnostics_a55a12b1");default->key;};}
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;if(j.has("error"))notice=Json.opt(j,"text",Client.text("ui.unavailable_949ac1f2"));else if(j.has("roles")){roles=j.getAsJsonArray("roles");notice=roles.isEmpty()?Client.text("ui.could_not_retrieve_loaded_luckperms_roles_d64401c8"):Json.opt(j,"context","");}rebuildWidgets();}
 @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;notice=Client.text("ui.no_response_reopen_this_window_a3ea9202");rebuildWidgets();}if(!ServerMenuClient.admin())onClose();}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),end());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x()+12,top(),w()-24);Ui.status(g,font,busy?Client.text("ui.loading_roles_4386b264"):notice,x()+12,end()-66,w()-24,end()-34);});}
 @Override public void onClose(){session.cancel();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
