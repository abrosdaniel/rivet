package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
final class PrivacyScreen extends Screen implements CommunityScreen.Receiver {
 private final Screen parent;private String mode="public",notice="";private boolean statistics=true,loaded,busy;private final RequestSession session=new RequestSession();
 PrivacyScreen(Screen parent){super(Client.tr("ui.profile_visibility_4e183428"));this.parent=parent;}
 private int top(){return UiDialog.top(height,200);}private int bottom(){return height-top();}private int w(){return Math.min(380,width-32);}private int x(){return (width-w())/2;}
 private void request(boolean save){if(busy)return;var j=new JsonObject();j.addProperty("action","community");j.addProperty("section","home");j.addProperty("op",save?"toolsPrivacySave":"toolsPrivacy");j.addProperty("mode",mode);j.addProperty("statistics",statistics);busy=true;ServerMenuClient.request(session.begin(j,save,System.currentTimeMillis()));}
 @Override protected void init(){var modes=List.of("public","members","private");var names=List.of(Client.text("ui.all_players_7b6d95ec"),Client.text("server.groups"),Client.text("ui.only_me_be26ab44"));var choice=addRenderableWidget(UiActions.button(Component.literal(Client.text("ui.about_me_dcf1d723")+names.get(modes.indexOf(mode))+" ▾"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new ChoicePopup(this,Client.text("ui.who_can_see_the_description_95d4bdd1"),names,n->{mode=modes.get(n);rebuildWidgets();},b))).bounds(x(),top()+36,w(),20).build());choice.active=!busy;addRenderableWidget(UiActions.button(Component.literal(Client.text("ui.show_statistics_91d8cf3d")+(statistics?Client.text("ui.yes_d4f57dba"):Client.text("ui.no_ced07fd1"))),UiActions.Tone.NORMAL,"",b->{statistics=!statistics;rebuildWidgets();}).bounds(x(),top()+64,w(),20).build());var save=addRenderableWidget(UiActions.button(Client.tr("map.tool.save"),UiActions.Tone.PRIMARY,UiIcons.SAVE,b->request(true)).bounds(x(),bottom()-54,w(),20).build());save.active=!busy&&loaded;UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(x(),bottom()-28,w(),20),this::addRenderableWidget,this::onClose);if(!loaded){loaded=true;request(false);}}
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;notice=j.has("error")?Json.opt(j,"text",Client.text("ui.could_not_save_9f476bf2")):Client.text("ui.saved_f0dff5ab");if(j.has("settings")){mode=Json.opt(j.getAsJsonObject("settings"),"mode","public");statistics=j.getAsJsonObject("settings").get("statistics").getAsBoolean();}rebuildWidgets();}
 @Override public void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;notice=Client.text("ui.no_response_try_saving_again_a12a6126");rebuildWidgets();}}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());Ui.text(g,font,notice,x(),bottom()-78,UiPalette.color(0xBAC7D2));});}
 @Override public void onClose(){if(!busy){session.cancel();minecraft.setScreen(parent);}}
 @Override public boolean isPauseScreen(){return false;}
}
