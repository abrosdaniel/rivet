package dev.abros.rivet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Connection-only entry point for real-wire harnesses; never edits map state or opens screens. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class TestConnectionHarness {
 private static boolean started,done;private static long deadline;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  String address=System.getenv("RIVET_TEST_CONNECT");if(address==null||done)return;var mc=Minecraft.getInstance();
  if(!started){if(!(mc.screen instanceof TitleScreen))return;started=true;deadline=System.currentTimeMillis()+120000;mc.options.pauseOnLostFocus=false;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),new ServerData("Wire check",address,ServerData.Type.OTHER),false,null);}
  if(mc.player!=null){done=true;return;}
  if(started&&System.currentTimeMillis()>deadline){done=true;System.out.println("RIVET_TEST_CONNECTION_FAILED timeout");mc.stop();}
 }
}
