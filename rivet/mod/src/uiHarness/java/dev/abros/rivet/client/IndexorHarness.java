package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class IndexorHarness {
 private static String lastScreen="";
 private static int phase;private static boolean submitted,done;private static long deadline;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  String address=System.getenv("RIVET_INDEXOR_ADDRESS");if(address==null||done)return;var mc=Minecraft.getInstance();
  try{
   String screen=mc.screen==null?"world":mc.screen.getClass().getSimpleName();if(!screen.equals(lastScreen)){lastScreen=screen;System.out.println("RIVET_INDEXOR_SCREEN "+screen);}
   if(phase==0){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+180000;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),new ServerData("Indexor regression",address,ServerData.Type.OTHER),false,null);phase=1;return;}
   if(mc.screen instanceof DisconnectedScreen)throw new AssertionError("Disconnected during Indexor test");
   if(System.currentTimeMillis()>deadline)throw new AssertionError("Indexor client timeout phase="+phase);
   if(mc.screen instanceof AuthScreen auth&&!submitted){for(var child:auth.children())if(child instanceof EditBox box)box.setValue("indexor isolated test password");var field=AuthScreen.class.getDeclaredField("submit");field.setAccessible(true);((Button)field.get(auth)).onPress();submitted=true;return;}
   if(mc.player==null||!WorldMapClient.ready())return;
   if(phase==1){mc.player.connection.sendCommand("indexorFixture");phase=2;}
   for(var line:((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages()){
    String text=line.content().getString();if(text.startsWith("INDEXOR_FAILED"))throw new AssertionError(text);
    if(text.startsWith("INDEXOR_DONE")){System.out.println("RIVET_INDEXOR_CLIENT_OK "+text);done=true;mc.stop();return;}
   }
  }catch(Throwable failure){done=true;System.out.println("RIVET_INDEXOR_CLIENT_FAILED");failure.printStackTrace();mc.stop();}
 }
}
