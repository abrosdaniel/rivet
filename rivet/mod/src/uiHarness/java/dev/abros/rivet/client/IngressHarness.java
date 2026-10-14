package dev.abros.rivet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import dev.abros.rivet.network.Protocol;

/** Bounded local regression: malformed burst must disconnect, a fresh session must work. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class IngressHarness {
 private static int phase,ticks,cycles;private static long deadline;private static boolean done;
 private static void connect(Minecraft mc,String address){ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),new ServerData("Ingress regression",address,ServerData.Type.OTHER),false,null);}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  String address=System.getenv("RIVET_INGRESS_TEST_ADDRESS");if(address==null||done)return;var mc=Minecraft.getInstance();
  try{
   if(phase==0){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+120000;connect(mc,address);phase=1;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Ingress timeout phase="+phase);
   if(phase==1&&mc.player!=null&&WorldMapClient.ready()&&++ticks>40){for(int n=0;n<300;n++)PacketDistributor.sendToServer(new Protocol.FeatureRequest("{"));phase=2;deadline=System.currentTimeMillis()+15000;return;}
   if(phase==2&&mc.screen instanceof DisconnectedScreen){connect(mc,address);phase=3;deadline=System.currentTimeMillis()+60000;ticks=0;return;}
   if(phase==3&&mc.player!=null&&WorldMapClient.ready()&&++ticks>40){
    int target=Integer.parseInt(System.getenv().getOrDefault("RIVET_INGRESS_CYCLES","1"));if(target<1||target>50)throw new IllegalArgumentException("Ingress cycles must be 1–50");
    cycles++;if(target>1)System.gc();var memory=java.lang.management.ManagementFactory.getMemoryMXBean();
    System.out.println("RIVET_INGRESS_SAMPLE cycle="+cycles+" heap="+memory.getHeapMemoryUsage().getUsed()+" threads="+java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount());
    if(cycles<target){phase=1;ticks=0;deadline=System.currentTimeMillis()+60000;return;}
    System.out.println("RIVET_INGRESS_OK malformed burst disconnected; fresh session works");done=true;mc.stop();}
  }catch(Throwable failure){System.out.println("RIVET_INGRESS_FAILED phase="+phase);failure.printStackTrace();done=true;mc.stop();}
 }
}
