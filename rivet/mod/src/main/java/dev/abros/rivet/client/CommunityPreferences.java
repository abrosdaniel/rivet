package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
/** Toggles persist immediately. Failed network saves keep the same command for safe retry. */
final class CommunityPreferences extends ScrollScreen implements CommunityScreen.Receiver {
 private JsonObject preferences;private final Screen parent;private final JsonArray muted=new JsonArray();private final RequestSession session=new RequestSession();private boolean busy,retry;private boolean delivery;private String status="";private final List<String> rowLabels=new ArrayList<>();
 CommunityPreferences(Screen parent,JsonObject prefs){super(Client.tr("ui.notification_settings_a8b79046"));this.parent=parent;this.preferences=prefs.deepCopy();if(prefs.has("muted"))prefs.getAsJsonArray("muted").forEach(muted::add);}
 private void toggle(String key){if(busy||retry)return;var value=new JsonPrimitive(key);if(muted.contains(value))muted.remove(value);else muted.add(value);var body=new JsonObject();body.addProperty("action","community");body.addProperty("op","preferences");body.addProperty("section","home");var prefs=preferences.deepCopy();prefs.add("muted",muted.deepCopy());body.add("preferences",prefs);busy=true;status=Client.text("ui.saving_632fd0d9");ServerMenuClient.request(session.begin(body,true,System.currentTimeMillis()));rebuildWidgets();}
 private void snooze(){preferences.addProperty("snoozeUntil",preferences.has("snoozeUntil")&&preferences.get("snoozeUntil").getAsLong()>System.currentTimeMillis()?0:System.currentTimeMillis()+3600000);var j=new JsonObject();j.addProperty("action","community");j.addProperty("section","home");j.addProperty("op","preferences");var p=preferences.deepCopy();p.add("muted",muted.deepCopy());j.add("preferences",p);busy=true;ServerMenuClient.request(session.begin(j,true,System.currentTimeMillis()));rebuildWidgets();}
 private int panelTop(){return UiDialog.top(height,280);}
 private int panelBottom(){return height-panelTop();}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,contentWidth()+24,panelTop(),panelBottom());}
 private int contentWidth(){return Math.min(360,width-48);}
 @Override protected void init(){rowLabels.clear();int w=contentWidth(),x=(width-w)/2,top=panelTop();
  UiTabs.build(this,font,new dev.abros.rivet.core.NativeLayout.Box(x,top+30,UiSearchToolbar.contentWidth(w),20),List.of(Client.text("ui.by_section_bd2c54c2"),Client.text("ui.delivery_dcbcbc1d")),delivery?1:0,this::addRenderableWidget,n->{delivery=n==1;resetScroll();rebuildWidgets();},!busy);
  scrollArea(delivery?6:5,new dev.abros.rivet.core.NativeLayout.Box(x,top+68,Math.max(0,w),Math.max(0,(panelBottom()-60)-(top+68))),30);
  for(int n=firstRow;n<Math.min(delivery?6:5,firstRow+visibleRows);n++){final int index=n;int y=top+68+(n-firstRow)*30;
   String label,value;Runnable change;
   if(!delivery){String key=List.of("board","groups","events","polls","ideas").get(n);label=CommunityScreen.name(key);value=muted.contains(new JsonPrimitive(key))?Client.text("ui.no_f82a8219"):Client.text("ui.yes_8d2fab2d");change=()->toggle(key);}
   else if(n<3){int setting=n==1?1:n==2?2:0;label=List.of(Client.text("ui.in_game_toasts_573ea836"),Client.text("ui.sound_3bcc5196"),Client.text("ui.server_restart_1def2a80")).get(n);value=ServerMenuClient.enabled(setting)?Client.text("ui.yes_8d2fab2d"):Client.text("ui.no_f82a8219");change=()->{ServerMenuClient.toggle(setting);rebuildWidgets();};}
   else if(n==3){label=Client.text("ui.quiet_mode_1_hour_3a7115f1");value=preferences.has("snoozeUntil")&&preferences.get("snoozeUntil").getAsLong()>System.currentTimeMillis()?Client.text("ui.on_a60b4b83"):Client.text("ui.off_622574d5");change=this::snooze;}
   else if(n==5){label=Client.text("ui.toast_appearance_and_filters_1ed330d1");value=Client.text("ui.configure_d2fcc48e");change=()->minecraft.setScreen(new HudSettingsScreen(this));}
   else{label=Client.text("ui.invitations_and_responses_c433a04b");value=Client.text("ui.configure_d2fcc48e");change=()->PersonalProfileScreen.ignores(this,"");}
   rowLabels.add(label);var button=delivery&&index>=4?addRenderableWidget(UiActions.button(Component.literal(value),UiActions.Tone.NORMAL,"",b->change.run()).bounds(x+UiSearchToolbar.contentWidth(w)-86,y,86,24).build()):addRenderableWidget(new UiToggle(value,x+UiSearchToolbar.contentWidth(w)-86,y,86,value.equals(Client.text("ui.yes_8d2fab2d"))||value.equals(Client.text("ui.on_a60b4b83")),change));button.setTooltip(Tooltip.create(Component.literal(label)));button.active=delivery&&index<3||!busy&&!retry;
  }
  if(retry)addRenderableWidget(UiActions.button(Client.tr("ui.retry_saving_a77832ec"),UiActions.Tone.NORMAL,UiIcons.REFRESH,b->{busy=true;retry=false;status=Client.text("ui.saving_632fd0d9");ServerMenuClient.request(session.retry(System.currentTimeMillis()));rebuildWidgets();}).bounds(x,panelBottom()-52,w-100,20).build());
  addRenderableWidget(UiActions.button(Client.tr("done"),UiActions.Tone.NORMAL,"",b->onClose()).bounds(x+w-80,panelBottom()-28,80,20).build()).active=!busy;
 }
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;if(j.has("error")){status=Json.opt(j,"text",Client.text("ui.could_not_save_9f476bf2"));retry=true;}else{status=Client.text("ui.saved_f0dff5ab");retry=false;invalidateParent();}rebuildWidgets();}
 @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;retry=true;status=Client.text("ui.no_response_try_saving_again_a12a6126");rebuildWidgets();}}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);int left=(width-contentWidth())/2;for(int n=0;n<rowLabels.size();n++){int yy=panelTop()+68+n*30;g.fill(left,yy,left+UiSearchToolbar.contentWidth(contentWidth())-94,yy+24,UiPalette.color(0xFF172630));Ui.text(g,font,font.plainSubstrByWidth(rowLabels.get(n),UiSearchToolbar.contentWidth(contentWidth())-110),left+8,yy+8,UiPalette.color(0xD8E9F2),false);}UiHeading.dialog(g,font,title,(width-(contentWidth()))/2,panelTop(),contentWidth());Ui.status(g,font,status.isEmpty()?(delivery?Client.text("ui.settings_apply_to_this_client_c0266cb2"):Client.text("ui.notifications_for_selected_sections_saved_automatically_eb7d125d")):status,(width-contentWidth()+24)/2,panelBottom()-56,contentWidth()+24,panelBottom()-34);});}
 @Override public void onClose(){if(busy)return;session.cancel();invalidateParent();minecraft.setScreen(parent);}
 private void invalidateParent(){if(parent instanceof NotificationPopup p)p.invalidate();else if(parent instanceof CommunityScreen p)p.invalidate();}
 @Override public boolean isPauseScreen(){return false;}
}
