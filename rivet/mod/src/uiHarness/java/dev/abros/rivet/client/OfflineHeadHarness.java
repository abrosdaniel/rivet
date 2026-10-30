package dev.abros.rivet.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;
import java.util.concurrent.CompletableFuture;
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class OfflineHeadHarness {
 private static int phase=-1;private static long deadline;private static boolean done,submitted;
 private static CompletableFuture<ResolvableProfile> resolving;private static ResolvableProfile profile;private static String hash;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  String address=System.getenv("RIVET_OFFLINE_HEAD_ADDRESS");if(address==null||done)return;var mc=Minecraft.getInstance();try{
   if(phase<0){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+120000;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),new ServerData("Offline head test",address,ServerData.Type.OTHER),false,null);phase=0;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Offline head timeout phase="+phase);
   if(mc.screen instanceof AuthScreen auth&&!submitted){for(var child:auth.children())if(child instanceof EditBox box)box.setValue("offline head test password");var field=AuthScreen.class.getDeclaredField("submit");field.setAccessible(true);((Button)field.get(auth)).onPress();submitted=true;return;}
   if(mc.player==null||!SkinClient.available()||!ServerMenuClient.available()||!SkinClient.library.has("entries"))return;
   if(phase==0){mc.player.connection.sendCommand("offlineHeadFixture");phase=1;}
   else if(phase==1){for(var message:((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages()){String text=message.content().getString();if(text.startsWith("HEAD_READY "))hash=text.substring(11);}if(hash==null)return;
    for(var stack:mc.player.getInventory().items)if(stack.is(Items.PLAYER_HEAD)&&stack.get(DataComponents.PROFILE)!=null){resolving=stack.get(DataComponents.PROFILE).resolve();phase=2;break;}}
   else if(phase==2){if(!resolving.isDone())return;profile=resolving.join();if(!profile.id().orElseThrow().equals(dev.abros.rivet.server.OfflineHeadFixture.OWNER))throw new IllegalStateException("Offline head resolved to another UUID: "+profile.id());
    if(mc.getConnection().getPlayerInfo(profile.id().orElseThrow())!=null)throw new IllegalStateException("Fixture owner unexpectedly online");phase=3;}
   else if(phase==3){var skin=mc.getSkinManager().getInsecureSkin(profile.gameProfile());if(!skin.texture().toString().equals("rivet:skins/"+hash))return;
    var properties=new com.mojang.authlib.properties.PropertyMap();properties.put("textures",new com.mojang.authlib.properties.Property("textures","decorative-fixture"));var decorative=new ResolvableProfile(Optional.of("Decoration"),Optional.empty(),properties);if(decorative.resolve().join()!=decorative)throw new IllegalStateException("Decorative profile rewritten");
    System.out.println("RIVET_OFFLINE_HEAD_OK name=true stableUuid=true offline=true customTexture=true decorative=true");done=true;mc.stop();}
  }catch(Throwable failure){done=true;System.out.println("RIVET_OFFLINE_HEAD_FAILED phase="+phase);failure.printStackTrace();mc.stop();}
 }
}
