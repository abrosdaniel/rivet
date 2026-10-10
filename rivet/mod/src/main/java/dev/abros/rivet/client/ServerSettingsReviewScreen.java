package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.*;
final class ServerSettingsReviewScreen extends ScrollScreen implements CommunityScreen.Receiver {
 private final Screen parent;private final RequestSession session=new RequestSession();private JsonObject data=new JsonObject();private boolean loaded,busy;private String notice="";private final List<String> lines=new ArrayList<>();
 ServerSettingsReviewScreen(Screen parent){super(Client.tr("ui.server_settings_changes_fbb9f52f"));this.parent=parent;}
 private int w(){return Math.min(480,width-32);}private int x(){return(width-w())/2;}private int top(){return UiDialog.top(height,340);}private int bottom(){return height-top();}
 private void request(boolean apply){if(busy)return;var j=new JsonObject();j.addProperty("action","serverConfig");j.addProperty("op",apply?"apply":"preview");if(apply)j.add("hash",data.get("hash"));busy=true;ServerMenuClient.request(session.begin(j,apply,System.currentTimeMillis()));rebuildWidgets();}
 @Override protected void init(){lines.clear();if(data.has("changes"))for(var e:data.getAsJsonArray("changes")){var c=e.getAsJsonObject();String text=Json.str(c,"key")+" · "+(c.get("live").getAsBoolean()?Client.text("ui.can_be_applied_d08b61de"):Client.text("ui.restart_required_8d255547"));if(c.has("after"))text+=" → "+c.get("after");for(var line:font.getSplitter().splitLines(text,w()-14,net.minecraft.network.chat.Style.EMPTY))lines.add(line.getString());}if(data.has("changes")&&lines.isEmpty())lines.add(Client.text("ui.no_changes_e9ff0735"));scrollArea(lines.size(),new dev.abros.rivet.core.NativeLayout.Box(x(),top()+36,Math.max(0,w()),Math.max(0,(bottom()-88)-(top()+36))),14);var apply=addRenderableWidget(UiActions.button(Client.tr("ui.apply_menu_and_sections_b1ba5dc4"),UiActions.Tone.NORMAL,"",b->minecraft.setScreen(new UiConfirmDialog(yes->{minecraft.setScreen(this);if(yes)request(true);},Client.tr("ui.apply_settings_4d4707af"),Client.tr("ui.only_the_menu_and_sections_will_890c01bd")))).bounds(x(),bottom()-78,w(),20).build());apply.active=!busy&&data.has("changes")&&java.util.stream.StreamSupport.stream(data.getAsJsonArray("changes").spliterator(),false).anyMatch(e->e.getAsJsonObject().get("live").getAsBoolean());var refresh=addRenderableWidget(UiActions.button(Client.tr("ui.check_file_again_8a61cb94"),UiActions.Tone.NORMAL,"",b->request(false)).bounds(x(),bottom()-54,w(),20).build());refresh.active=!busy;UiActions.close(new dev.abros.rivet.core.NativeLayout.Box(x(),bottom()-28,w(),20),this::addRenderableWidget,this::onClose);if(!loaded){loaded=true;request(false);}}
 public void receiveCommunity(JsonObject j){if(!session.receive(j))return;busy=false;notice=j.has("error")?Json.opt(j,"text",Client.text("ui.could_not_verify_5278cb0f")):j.has("applied")?Client.text("ui.menu_and_sections_updated_5cc32653"):"";if(j.has("changes"))data=j;rebuildWidgets();}
 @Override public void tick(){if(!ServerMenuClient.admin())onClose();if(session.timeout(System.currentTimeMillis())){busy=false;notice=Client.text("ui.no_response_check_the_file_again_e09ed16e");rebuildWidgets();}}
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float d){UiDialog.draw(g,width,w(),top(),bottom());}
 @Override public void render(GuiGraphics g,int mx,int my,float d){UiDialog.render(parent,this,g,d,()->{super.render(g,mx,my,d);UiHeading.dialog(g,font,title,x(),top(),w());for(int i=firstRow;i<Math.min(lines.size(),firstRow+visibleRows);i++)Ui.text(g,font,lines.get(i),x()+4,top()+36+(i-firstRow)*14,UiPalette.color(0xD7E2EC));Ui.status(g,font,notice,x(),bottom()-103,w(),bottom()-80);});}
 @Override public void onClose(){session.cancel();minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
