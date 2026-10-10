package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Edits only the viewer's public description and private contact preferences. */
final class PersonalProfileScreen extends ScrollScreen implements CommunityScreen.Receiver {
 private final Screen parent;private final RequestSession session=new RequestSession();private boolean ignored,busy,loaded,retry;private String about="",interests="",target="",status="";private JsonArray entries=new JsonArray();
 PersonalProfileScreen(Screen parent){super(Client.tr("ui.about_me_312416bd"));this.parent=parent;}
 static void ignores(Screen parent,String target){var screen=new PersonalProfileScreen(parent);screen.ignored=true;screen.target=target;net.minecraft.client.Minecraft.getInstance().setScreen(screen);}
 private int panelTop(){return UiDialog.top(height,ignored?280:Math.max(40,Math.min(80,height-204))+168);}
 private int panelBottom(){return height-panelTop();}
 private void request(String op,JsonObject body){if(busy)return;body.addProperty("action","community");body.addProperty("section","home");body.addProperty("op",op);var command=session.begin(body,!java.util.Set.of("plusProfile","plusIgnores").contains(op),System.currentTimeMillis());busy=true;retry=false;ServerMenuClient.request(command);rebuildWidgets();}
 private void button(String text,int x,int y,int w,Runnable run){var b=addRenderableWidget(UiActions.button(Component.literal(text),UiActions.Tone.NORMAL,"",v->run.run()).bounds(x,y,w,20).build());b.active=!busy;}
 @Override public void renderBackground(GuiGraphics g,int x,int y,float d){UiDialog.draw(g,width,Math.min(380,width-32),panelTop()-4,panelBottom());}
 @Override protected void init(){int w=Math.min(380,width-32),x=(width-w)/2,top=panelTop();
  if(!ignored){if(ServerMenuClient.supports("player-tools"))button(Client.text("ui.visibility_1a62f363"),x+w-104,top+2,104,()->minecraft.setScreen(new PrivacyScreen(this)));var edit=addRenderableWidget(UiFields.multiline(font,x,top+26,w,Math.max(40,Math.min(80,height-204)),Client.tr("ui.about_me_312416bd"),Client.tr("ui.about_me_312416bd")));edit.active=!busy;edit.setCharacterLimit(300);edit.setValue(about);edit.setValueListener(v->about=v);int y=top+52+edit.getHeight();
   button(Client.text("map.tool.save"),x,ignored?panelBottom()-54:panelTop()+Math.max(40,Math.min(80,height-204))+102,w,()->{var j=new JsonObject();j.addProperty("about",about);j.addProperty("interests",interests);request("plusProfileSave",j);});
  }else{button(target.isBlank()?Client.text("ui.select_player_697ae486"):target,x,top+26,UiSearchToolbar.contentWidth(w)-96,()->FeatureListScreen.pick(this,player->{target=Json.str(player,"name");rebuildWidgets();}));button(Client.text("ui.restrict_92a86e44"),x+UiSearchToolbar.contentWidth(w)-90,top+26,90,()->{var j=new JsonObject();j.addProperty("target",target);request("plusIgnore",j);});scrollArea(entries.size(),new dev.abros.rivet.core.NativeLayout.Box(x,top+56,Math.max(0,w),Math.max(0,(panelBottom()-50)-(top+56))),26);for(int i=firstRow;i<Math.min(entries.size(),firstRow+visibleRows);i++){var entry=entries.get(i).getAsJsonObject();button(Json.str(entry,"name")+Client.text("ui.allow_044ab35e"),x,top+56+(i-firstRow)*26,w,()->{var j=new JsonObject();j.addProperty("target",Json.str(entry,"id"));j.addProperty("remove",true);request("plusIgnore",j);});}}
  if(retry){clearWidgets();UiActions.command(UiActions.Command.RETRY,new dev.abros.rivet.core.NativeLayout.Box(x,ignored?panelBottom()-54:panelTop()+Math.max(40,Math.min(80,height-204))+102,w,20),this::addRenderableWidget,()->{busy=true;retry=false;ServerMenuClient.request(session.retry(System.currentTimeMillis()));rebuildWidgets();});}UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(x,ignored?panelBottom()-28:panelTop()+Math.max(40,Math.min(80,height-204))+128,w,20),this::addRenderableWidget,this::onClose);
  if(!loaded){loaded=true;request(ignored?"plusIgnores":"plusProfile",new JsonObject());}
 }
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;if(j.has("error")){status=Json.opt(j,"text",Client.text("ui.error_72aecd9a"));retry=!java.util.Set.of("INVALID","FORBIDDEN","EXPIRED").contains(Json.opt(j,"code",""));}else{if(j.has("profile")){about=Json.opt(j.getAsJsonObject("profile"),"about","");interests=Json.opt(j.getAsJsonObject("profile"),"interests","");}if(j.has("entries"))entries=j.getAsJsonArray("entries");status="";}rebuildWidgets();}
 @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;retry=true;status=Client.text("ui.no_response_it_is_safe_to_27824cb3");rebuildWidgets();}}
 @Override public void render(GuiGraphics g,int x,int y,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,x,y,d);UiHeading.dialog(g,font,ignored?Client.tr("ui.invitations_and_responses_c433a04b"):title,(width-Math.min(380,width-32))/2,panelTop()-4,Math.min(380,width-32)-(ignored?0:110));if(!ignored){int top=panelTop(),h=Math.max(40,Math.min(80,height-204));Ui.text(g,font,Client.text("ui.tell_us_about_yourself_up_to_eee9d07b"),(width-Math.min(380,width-32))/2,top+h+40,UiKit.muted());Ui.status(g,font,status,(width-Math.min(380,width-32))/2,top+h+78,Math.min(380,width-32),top+h+99);}else {if(entries.isEmpty())Ui.centered(g,font,Client.text("ui.no_restricted_players_dd9dfa38"),width/2,panelTop()+66,UiPalette.color(0xBAC7D2));Ui.status(g,font,status,(width-Math.min(380,width-32))/2,panelBottom()-50,Math.min(380,width-32),panelBottom()-30);}});}
 @Override public void onClose(){if(busy)return;session.cancel();UiNavigation.back(this,parent);}
 @Override public boolean isPauseScreen(){return false;}
}
