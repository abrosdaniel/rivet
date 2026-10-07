package dev.abros.rivet.client;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** Shared navigation for built-in server sections. */
final class MenuSidebar {
 private static final Map<Screen,Integer> positions=new WeakHashMap<>();
 static void clear(){positions.clear();}
 static boolean scrollFor(Screen host,double x,double y,double dy){if(!positions.containsKey(host))return false;var nav=new MenuSidebar(host);nav.workspace=UiWorkspace.fit(host.width,host.height);if(nav.workspace.compact())return false;nav.offset=positions.getOrDefault(host,0);if(!nav.scroll(x,y,dy))return false;positions.put(host,nav.offset);host.resize(Minecraft.getInstance(),host.width,host.height);return true;}
 static void drawFor(Screen host,net.minecraft.client.gui.GuiGraphics g){if(!positions.containsKey(host))return;var nav=new MenuSidebar(host);nav.workspace=UiWorkspace.fit(host.width,host.height);nav.offset=positions.getOrDefault(host,0);nav.drawFrame(g);}
 private int offset;private final Screen host;private UiWorkspace workspace;
 MenuSidebar(Screen host){this.host=host;}
 int right(){return host.width-UiWorkspace.OUTER_X;}
 int left(){return UiWorkspace.fit(host.width,host.height).page().x();}
 static List<String> sections(){var keys=new ArrayList<String>();var config=ServerMenuClient.state.has("communityConfig")?ServerMenuClient.state.getAsJsonObject("communityConfig"):new JsonObject();for(String key:List.of("home","players","tasks","board","groups","events","polls","ideas","help","admin")){if(!ServerMenuClient.module(key))continue;if(!Set.of("tasks").contains(key)&&ServerMenuClient.state.has("features")&&!ServerMenuClient.state.getAsJsonArray("features").contains(new JsonPrimitive(key)))continue;if(key.equals("admin")&&!ServerMenuClient.staff())continue;if(config.has("sections")&&!Set.of("tasks","help","admin").contains(key)&&!config.getAsJsonArray("sections").contains(new JsonPrimitive(key)))continue;keys.add(key);}return keys;}
 static void configure(Button button,String key){if(key.equals("tasks")&&!TaskScreen.available()){button.active=false;button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Сервер не поддерживает личные задачи. Обновите Rivet на клиенте и сервере.")));}}
 void build(String current,Consumer<Button> add){offset=positions.getOrDefault(host,offset);workspace=UiWorkspace.fit(host.width,host.height);buildFrame(current,add);positions.put(host,offset);}
 static String inboxLabel(int width){int unread=ServerMenuClient.state.has("unread")?ServerMenuClient.state.get("unread").getAsInt():0;return (width<420?"Входящие":"Уведомления")+(unread>0?" ("+unread+")":"");}
 private void buildFrame(String current,Consumer<Button> add){var keys=sections();if(workspace.compact()){var choices=List.copyOf(keys);var header=workspace.header();int menuWidth=Math.max(60,Math.min(112,UiBrand.left(Minecraft.getInstance().font,header)-header.x()-8));var button=UiActions.button(Component.literal(CommunityScreen.name(current)+" ▾"),UiActions.Tone.NORMAL,"",b->Minecraft.getInstance().setScreen(new ChoicePopup(host,"Раздел",choices.stream().map(CommunityScreen::name).toList(),n->navigate(choices.get(n)),b).current(Math.max(0,choices.indexOf(current))))).bounds(header.x(),header.y(),menuWidth,20).build();add.accept(button);}var area=navigationArea();int shown=Math.max(1,area.height()/24);offset=Math.max(0,Math.min(offset,Math.max(0,keys.size()-shown)));for(int n=offset;!workspace.compact()&&n<Math.min(keys.size(),offset+shown);n++){String key=keys.get(n);var button=new SidebarButton(area.x(),area.y()+(n-offset)*24,area.width(),CommunityScreen.name(key),current.equals(key),()->navigate(key));configure(button,key);add.accept(button);}if(!ServerMenuClient.state.has("features")||ServerMenuClient.state.getAsJsonArray("features").contains(new JsonPrimitive("notifications"))){var header=workspace.header();add.accept(UiActions.button(Component.literal(inboxLabel(host.width)),UiActions.Tone.NORMAL,"",b->navigate("notifications")).bounds(header.right()-(host.width<420?68:130),header.y(),host.width<420?68:130,20).build());}}
 private dev.abros.rivet.core.NativeLayout.Box navigationArea(){return workspace.sidebar();}
 private void navigate(String key){UiNavigation.open(host,key);}

 boolean scroll(double x,double y,double dy){if(workspace!=null&&(y<navigationArea().y()||y>=navigationArea().bottom()))return false;return scroll(x,dy);}
 void drawFrame(net.minecraft.client.gui.GuiGraphics g){if(workspace==null||workspace.compact())return;var area=navigationArea();int count=sections().size(),shown=Math.max(1,area.height()/24);if(count<=shown)return;int h=Math.max(12,area.height()*shown/count),y=area.y()+(area.height()-h)*offset/(count-shown);g.fill(area.right()+4,area.y(),area.right()+6,area.bottom(),UiPalette.scrollTrack());g.fill(area.right()+4,y,area.right()+6,y+h,UiPalette.scrollThumb());}
 boolean scroll(double x,double dy){if(dy==0||workspace!=null&&(x<workspace.sidebar().x()||x>=workspace.sidebar().right())||workspace==null&&(x<0||x>=left()))return false;offset=Math.max(0,Math.min(Math.max(0,sections().size()-Math.max(1,navigationArea().height()/24)),offset+(dy<0?1:-1)));positions.put(host,offset);return true;}
}
