package dev.abros.rivet.client;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
/** Native client checks for server feature policy without a network or screenshots. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class ModulePolicyHarness {
 private static boolean done;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post ignored){
  var mc=Minecraft.getInstance();if(System.getenv("RIVET_MODULE_POLICY_CHECK")==null||done||!(mc.screen instanceof TitleScreen))return;done=true;
  var previous=ServerMenuClient.state;
  try{
   var state=new JsonObject();state.addProperty("admin",true);state.addProperty("staff",true);state.add("features",dev.abros.rivet.core.Json.parse("{\"items\":[\"home\",\"players\",\"task-tools\",\"groups\",\"board\",\"events\",\"polls\",\"ideas\",\"info\",\"help\",\"admin\"]}").get("items"));ServerMenuClient.state=state;
   check(TaskScreen.available(),"Old server task support lost");check(ServerMenuClient.may("rivet.reports"),"Old server permissions lost");
   var modules=new JsonObject();for(String id:dev.abros.rivet.core.FeatureModules.OPTIONAL)modules.addProperty(id,false);state.add("modules",modules);
   check(!TaskScreen.available(),"Disabled tasks exposed");check(!ServerMenuClient.may("rivet.reports"),"Admin bypasses reports policy");check(!ServerMenuClient.may("rivet.maintenance"),"Admin bypasses control policy");check(ServerMenuClient.may("rivet.diagnostics"),"Shared diagnostics disabled");
   var sidebar=MenuSidebar.sections();for(String id:java.util.List.of("tasks","groups","board","events","polls","ideas"))check(!sidebar.contains(id),"Disabled sidebar section: "+id);check(sidebar.containsAll(java.util.List.of("home","players","help","admin")),"Shared navigation lost");
   var help=new ServerMenuScreen(null,"help");mc.setScreen(help);check(help.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->((net.minecraft.client.gui.components.Button)w).getMessage().getString()).noneMatch(label->label.equals(Client.tr("server.report").getString())||label.equals(Client.tr("server.myReports").getString())),"Disabled reports remain in help");
   var administration=new ServerMenuScreen(null,"admin");mc.setScreen(administration);check(administration.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->((net.minecraft.client.gui.components.Button)w).getMessage().getString()).noneMatch(label->label.equals("Обращения")),"Disabled report tab remains");
   modules.addProperty("tasks",true);check(TaskScreen.available()&&MenuSidebar.sections().contains("tasks"),"Personal tasks require groups");
   System.out.println("RIVET_MODULE_POLICY_OK legacy server + disabled sidebar + admin permissions + shared diagnostics + personal tasks");
  }catch(Throwable failure){System.out.println("RIVET_MODULE_POLICY_FAILED");failure.printStackTrace();}
  finally{ServerMenuClient.state=previous;mc.stop();}
 }
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
