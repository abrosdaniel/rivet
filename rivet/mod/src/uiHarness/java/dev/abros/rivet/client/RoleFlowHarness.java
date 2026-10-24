package dev.abros.rivet.client;

import com.google.gson.*;
import dev.abros.rivet.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import java.util.*;
import java.util.concurrent.*;

/** Real UI with deterministic role-scoped transport; database ACLs are tested separately. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class RoleFlowHarness {
 private static final List<VisualMatrixHarness.Frame> FRAMES=VisualMatrixHarness.flows(8,1);
 private static int frame=-1,phase;private static long at;private static boolean done;
 private static long deadline;private static boolean started;
 private static Screen opened;private static int scene;private static String reportCursor="";
 private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 private static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private static JsonObject fixture(String section)throws Exception{var m=CommunityUiHarness.class.getDeclaredMethod("entry",String.class,int.class);m.setAccessible(true);return (JsonObject)m.invoke(null,section,0);}
 private static void request(JsonObject q){
  var result=new JsonObject();result.addProperty("kind","community");result.add("request",q.get("request"));String section=Json.opt(q,"section","home");result.addProperty("section",section);result.add("config",CommunityStore.defaults());result.add("names",new JsonObject());result.add("preferences",new JsonObject());result.addProperty("nextCursor","");
  try{
   if(Json.opt(q,"action","").equals("reports")){
    reportCursor=Json.opt(q,"cursor","");result.addProperty("nextCursor",reportCursor.isEmpty()?"page2":"");var rows=new JsonArray();
    for(int n=0;n<4;n++){var r=new JsonObject();r.addProperty("id",new UUID(0,n+100).toString());r.addProperty("revision",1);r.addProperty("player","Player "+n);r.addProperty("message","Help with installation");r.addProperty("status","open");rows.add(r);}result.add("reports",rows);
   }else if(section.equals("notifications")){
    var rows=new JsonArray();var r=fixture("board");r.addProperty("target",new UUID(0,10).toString());r.addProperty("read",true);r.addProperty("section","board");rows.add(r);result.add("entries",rows);
   }else if(!Json.opt(q,"id","").isEmpty()){
    var d=fixture(section);d.add("id",q.get("id"));
    if(section.equals("groups")){
     boolean member=scene>=1&&scene<=3,manager=scene>=2&&scene<=4;d.addProperty("isMember",member);d.addProperty("manage",manager);d.addProperty("myRole",scene==3?"leader":scene==2?"assistant":member?"member":"");d.addProperty("isOwner",scene==3);if(scene!=3)d.addProperty("owner",new UUID(0,99).toString());if(!member&&scene!=4)d.add("groupItems",new JsonArray());
    }
    if(section.equals("events")){d.addProperty("capacity",1);d.addProperty("participantsCount",1);d.addProperty("isParticipant",false);var actions=new JsonArray();actions.add("join");d.add("actions",actions);}
    result.add("detail",d);
   }else result.add("entries",new JsonArray());
  }catch(Exception failure){throw new IllegalStateException(failure);}
  CompletableFuture.delayedExecutor(30,TimeUnit.MILLISECONDS).execute(()->Minecraft.getInstance().execute(()->ServerMenuClient.receive(result)));
 }
 private static Button button(String key){String label=Client.text(key);for(var c:Minecraft.getInstance().screen.children())if(c instanceof Button b&&b.active&&b.getMessage().getString().equals(label))return b;throw new AssertionError("Missing button: "+label);}
 @SubscribeEvent public static void render(ScreenEvent.Render.Post event){
  if(System.getenv("RIVET_ROLE_FLOW_TEST")==null||done)return;var mc=Minecraft.getInstance();
  if(!started){started=true;deadline=System.currentTimeMillis()+120000;System.out.println("RIVET_ROLE_FLOW_START "+mc.screen.getClass().getSimpleName());}
  if(System.currentTimeMillis()>deadline){done=true;System.out.println("RIVET_ROLE_FLOW_FAILED timeout frame="+frame+" phase="+phase+" screen="+mc.screen.getClass().getSimpleName());mc.stop();return;}
  if(frame<0){if(!(mc.screen instanceof TitleScreen))return;frame=0;}
  if(System.currentTimeMillis()<at)return;at=System.currentTimeMillis()+180;
  try{
   if(phase==0){
    if(frame==FRAMES.size()){done=true;System.out.println("RIVET_ROLE_FLOW_OK: "+frame+" role/scale/theme scenarios");mc.stop();return;}
    var f=FRAMES.get(frame);f.apply();scene=f.scene();CommunityUiHarness.open("normal");ServerMenuClient.state.addProperty("admin",scene==4);ServerMenuClient.state.addProperty("staff",scene==6||scene==4);var caps=new JsonObject();caps.addProperty("rivet.reports",scene==6);ServerMenuClient.state.add("capabilities",caps);ServerMenuClient.previewTransport=RoleFlowHarness::request;
    if(scene<=4){var screen=new CommunityScreen(new TitleScreen(),"groups",new UUID(0,10).toString());screen.groupTab=scene<=1?"tasks":"requests";opened=screen;mc.setScreen(screen);}
    else if(scene==5){opened=new CommunityScreen(new TitleScreen(),"events",new UUID(0,11).toString());mc.setScreen(opened);}
    else if(scene==6){reportCursor="";opened=new ReportQueueScreen(new TitleScreen(),"high");mc.setScreen(opened);}
    else{opened=new NotificationPopup(mc.screen);mc.setScreen(opened);}
    System.out.println("RIVET_ROLE_FLOW_SCENE "+frame+" "+scene);phase=1;return;
   }
   if(scene<=4){var screen=(CommunityScreen)opened;if(!screen.data.has("detail"))return;check(screen.groupTab.equals(scene==0?"overview":scene==1?"tasks":"requests"),"Private tab mismatch for role "+scene);UiGeometryHarness.verify(screen);}
   else if(scene==5){var screen=(CommunityScreen)opened;if(!screen.data.has("detail"))return;boolean found=false;for(int row:List.of(0,4,8,12)){screen.revealRow(row);screen.rebuildWidgets();try{button("flow.joinWaitlist");found=true;break;}catch(AssertionError missing){}}check(found,"Full event must explain waitlist before joining");}
   else if(scene==6){
    if((boolean)field(opened,"busy"))return;
    check(!ServerMenuClient.admin()&&MenuSidebar.sections().contains("admin"),"Reports moderator entry");button("ui.high_priority_9ad02c50");UiGeometryHarness.verify(opened);
    if(phase==1){button("server.next").onPress();phase=2;return;}
    if(phase==2){check(reportCursor.equals("page2"),"Next report page");button("ui.top_of_list_a28e0e21").onPress();phase=3;return;}
    check(reportCursor.isEmpty(),"Return to first report page");
   }else{
    if(phase==1){var card=opened.children().stream().filter(c->c instanceof NotificationRow).map(c->(NotificationRow)c).findFirst();if(card.isEmpty())return;card.get().onPress();phase=2;return;}
    if(mc.screen==opened)return;check(mc.screen instanceof CommunityScreen,"Notification target");check(field(mc.screen,"parent")==opened,"Return must preserve inbox context");mc.screen.onClose();check(mc.screen==opened,"Back to inbox");
   }
   frame++;phase=0;if(frame%8==0)System.out.println("RIVET_ROLE_FLOW_PROGRESS "+frame+"/"+FRAMES.size());
  }catch(Throwable failure){done=true;System.out.println("RIVET_ROLE_FLOW_FAILED frame="+frame+" phase="+phase);failure.printStackTrace();mc.stop();}
 }
}
