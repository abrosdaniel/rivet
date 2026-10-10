package dev.abros.rivet.client;
import com.google.gson.*;
import dev.abros.rivet.core.*;
import dev.abros.rivet.core.map.MapPositionPolicy;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** The same editable privacy form in settings and the profile entry point. */
final class MapPositionSettings {
 private final Screen owner;private final Runnable changed;private final RequestSession session=new RequestSession();private boolean loaded,busy,ready,groups,readFailed;private String mode="full",audience="all",notice=Client.text("server.loading");
 MapPositionSettings(Screen owner,Runnable changed){this.owner=owner;this.changed=changed;}
 private void request(boolean save){if(busy||!MapPositions.available())return;var q=new JsonObject();q.addProperty("action","community");q.addProperty("section","home");q.addProperty("op",save?"mapPositionSave":"mapPositionSettings");if(save)q.add("settings",new MapPositionPolicy(mode,audience).json());busy=true;readFailed=false;ServerMenuClient.request(session.begin(q,save,System.currentTimeMillis()));}
 void build(NativeLayout.Box area,int first,int visible,Consumer<AbstractWidget> add){if(!loaded){loaded=true;request(false);}for(int row=first;row<Math.min(6,first+visible);row++){int y=area.y()+(row-first)*28;switch(row){
  case 0->{var values=List.of("full","nearby","hidden");choice(Client.text("map.caveMode"),List.of(Client.text("ui.entire_map_db550a84"),Client.text("ui.nearby_only_05ec059c"),Client.text("ui.hidden_6bbdf8a9")),values.indexOf(mode),n->mode=values.get(n),area.x(),y,area.width(),add);}
  case 1->{var values=groups||audience.equals("groups")?List.of("all","groups","none"):List.of("all","none");var labels=values.stream().map(a->a.equals("all")?Client.text("ui.all_players_7b6d95ec"):a.equals("groups")?groups?Client.text("ui.members_of_my_groups_4c86290a"):Client.text("ui.groups_disabled_4a2b97cb"):Client.text("ui.nobody_9e87637f")).toList();choice(Client.text("ui.who_can_see_it_1b9c88f2"),labels,values.indexOf(audience),n->audience=values.get(n),area.x(),y,area.width(),add);}
  case 2->{if(!ready&&readFailed){UiActions.command(UiActions.Command.RETRY,new NativeLayout.Box(area.x(),y,area.width(),20),add,()->{request(false);changed.run();}).active=!busy;}else{var button=UiActions.button(Client.tr("map.tool.save"),UiActions.Tone.PRIMARY,UiIcons.SAVE,b->{request(true);changed.run();}).bounds(area.x(),y,area.width(),24).build();button.active=ready&&!busy;add.accept(button);}}
 }} }
 private void choice(String label,List<String> labels,int selected,java.util.function.IntConsumer apply,int x,int y,int w,Consumer<AbstractWidget> add){var button=UiActions.button(Component.literal(label+": "+labels.get(selected)+" ▾"),UiActions.Tone.NORMAL,"",b->Minecraft.getInstance().setScreen(new ChoicePopup(owner,label,labels,n->{apply.accept(n);notice=Client.text("ui.changes_take_effect_after_saving_f80c7a3d");changed.run();},b).current(selected))).bounds(x,y,w,24).build();button.active=ready&&!busy;add.accept(button);}
 void render(GuiGraphics g,NativeLayout.Box area,int first,int visible){var font=Minecraft.getInstance().font;var policy=ServerMenuClient.state.getAsJsonObject("map");int radius=policy!=null&&policy.has("nearbyRadius")?policy.get("nearbyRadius").getAsInt():128;var lines=List.of(Client.text("ui.nearby_only_up_to_757cbc99")+radius+Client.text("ui.blocks_in_the_same_dimension_6207d218"),Client.text("ui.coordinates_are_not_saved_in_map_34ca21ec"),notice);for(int i=0;i<lines.size();i++){int row=i+3;if(row>=first&&row<first+visible)Ui.text(g,font,UiKit.fit(font,lines.get(i),area.width()),area.x(),area.y()+(row-first)*28+7,UiKit.muted(),false);}}
 void receive(JsonObject j){if(!session.receive(j))return;busy=false;if(j.has("error")){notice=Json.opt(j,"text",Client.text("ui.could_not_load_settings_73615d81"));readFailed=!ready;}else{var policy=MapPositionPolicy.read(j.getAsJsonObject("positionSettings"));mode=policy.mode();audience=policy.audience();groups=j.get("positionGroups").getAsBoolean();ready=true;notice=Json.str(session.command(),"op").equals("mapPositionSave")?Client.text("ui.visibility_saved_e6d56238"):Client.text("ui.select_the_mode_and_audience_for_52a3283c");}changed.run();}
 void tick(){if(session.timeout(System.currentTimeMillis())){busy=false;loaded=false;notice=Client.text("ui.no_server_response_retrying_0e97f919");changed.run();}}
}
