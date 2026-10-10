package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
final class FollowingScreen extends ScrollScreen implements CommunityScreen.Receiver {
 private final Screen parent;private final RequestSession session=new RequestSession();private JsonArray rows=new JsonArray();private boolean loaded,busy;private String notice="";
 FollowingScreen(Screen parent){super(Client.tr("ui.event_organizers_73a0c852"));this.parent=parent;}
 private int w(){return Math.min(390,width-40);}private int x(){return (width-w())/2;}private int top(){return UiDialog.top(height,300);}private int bottom(){return height-top();}
 private void request(String op,JsonObject j){if(busy||!ServerMenuClient.module("events"))return;j.addProperty("action","community");j.addProperty("section","home");j.addProperty("op",op);busy=true;ServerMenuClient.request(session.begin(j,!op.equals("toolsFollowing"),System.currentTimeMillis()));}
 @Override protected void init(){var add=addRenderableWidget(UiActions.button(Client.tr("ui.follow_organizer_7dd02b94"),UiActions.Tone.NORMAL,UiIcons.PLUS,b->FeatureListScreen.pick(this,p->{var j=new JsonObject();j.add("id",p.get("uuid"));request("toolsFollow",j);})).bounds(x(),top()+34,UiSearchToolbar.contentWidth(w()),20).build());add.active=!busy;scrollArea(rows.size(),new dev.abros.rivet.core.NativeLayout.Box(x(),top()+64,Math.max(0,w()),Math.max(0,(bottom()-58)-(top()+64))),26);for(int n=firstRow;n<Math.min(rows.size(),firstRow+visibleRows);n++){var p=rows.get(n).getAsJsonObject();var b=addRenderableWidget(UiActions.button(Component.literal(Json.str(p,"name")+Client.text("ui.unsubscribe_ea138748")),UiActions.Tone.NORMAL,"",v->{var j=new JsonObject();j.add("id",p.get("uuid"));j.addProperty("remove",true);request("toolsFollow",j);}).bounds(x(),top()+64+(n-firstRow)*26,w(),20).build());b.active=!busy;}UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(x(),bottom()-28,w(),20),this::addRenderableWidget,this::onClose);if(!loaded){loaded=true;request("toolsFollowing",new JsonObject());}}
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;if(j.has("error"))notice=Json.opt(j,"text",Client.text("ui.could_not_perform_the_action_de7fb956"));else if(j.has("following")&&j.get("following").isJsonArray()){rows=j.getAsJsonArray("following");notice=rows.isEmpty()?Client.text("ui.follow_to_hear_about_new_events_678f7acc"):"";}else{request("toolsFollowing",new JsonObject());return;}rebuildWidgets();}
 @Override public void tick(){if(!ServerMenuClient.module("events")){onClose();return;}if(session.timeout(System.currentTimeMillis())){busy=false;notice=Client.text("ui.no_response_open_the_list_again_e4c405c1");rebuildWidgets();}}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());Ui.status(g,font,notice,x(),bottom()-54,w(),bottom()-30);});}
 @Override public void onClose(){session.cancel();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
