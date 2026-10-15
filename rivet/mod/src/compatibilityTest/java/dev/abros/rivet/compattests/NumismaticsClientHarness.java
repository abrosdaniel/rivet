package dev.abros.rivet.compattests;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;

@EventBusSubscriber(modid="rivet_compat_tests",value=Dist.CLIENT)
public final class NumismaticsClientHarness {
 private static boolean connecting,done;private static long deadline;
 @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event){
  if(!"bridge".equals(System.getenv("RIVET_NUMISMATICS_TEST"))||connecting||!(event.getScreen() instanceof TitleScreen))return;
  connecting=true;deadline=System.currentTimeMillis()+90000;var mc=Minecraft.getInstance();
  ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(System.getenv().getOrDefault("RIVET_NUMISMATICS_ADDRESS","127.0.0.1:25592")),new net.minecraft.client.multiplayer.ServerData("Numismatics test",System.getenv().getOrDefault("RIVET_NUMISMATICS_ADDRESS","127.0.0.1:25592"),net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);
 }
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(!connecting||done)return;var mc=Minecraft.getInstance();
  try{
   if(System.currentTimeMillis()>deadline)throw new AssertionError("Numismatics bridge timeout");
   if(mc.player==null||!dev.abros.rivet.client.NumismaticsClientAdapter.active())return;
   var cls=Class.forName("dev.ithundxr.createnumismatics.util.UsernameUtils");var instance=cls.getField("INSTANCE").get(null);var get=cls.getMethod("getName",UUID.class,String.class);
   UUID offline=UUID.fromString("0a6e1c6b-f97c-46dd-8c83-81154e003c8d");
   if(!"OfflineBankOwner".equals(get.invoke(instance,offline,"fallback")))return;
   if(!mc.player.getGameProfile().getName().equals(get.invoke(instance,mc.player.getUUID(),"fallback")))throw new AssertionError("Online identity mismatch");
   var labels=(Map<UUID,String>)Class.forName("dev.ithundxr.createnumismatics.NumismaticsClient").getField("bankAccountLabels").get(null);
   UUID custom=UUID.randomUUID();labels.put(custom,"Custom bank");
   try{if(!"Custom bank".equals(get.invoke(instance,custom,"fallback")))throw new AssertionError("Client label lost");}finally{labels.remove(custom);}
   // A handled UUID must not be queued for Mojang lookup by the original method.
   var tried=cls.getDeclaredField("tried");tried.setAccessible(true);
   if(((Set<?>)tried.get(instance)).contains(offline))throw new AssertionError("Rivet UUID sent to Mojang");
   if(get.invoke(instance,null,null)!=null)throw new AssertionError("Null fallback lost");
   System.out.println("RIVET_NUMISMATICS_CLIENT_OK");done=true;if(System.getenv("RIVET_ALL_ADAPTERS_TEST")==null)mc.stop();
  }catch(Throwable error){System.out.println("RIVET_NUMISMATICS_CLIENT_FAILED");error.printStackTrace();done=true;mc.stop();}
 }
}
