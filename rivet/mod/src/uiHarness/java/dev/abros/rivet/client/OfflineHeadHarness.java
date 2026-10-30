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
 private static int phase=-1,connections;private static long deadline,next;private static boolean done,submitted;
 private static CompletableFuture<ResolvableProfile> resolving;private static ResolvableProfile profile;private static String hash,waiting="start";
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  String address=System.getenv("RIVET_OFFLINE_HEAD_ADDRESS");if(address==null||done)return;var mc=Minecraft.getInstance();try{
   if(phase==4){if(System.currentTimeMillis()<next)return;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),new ServerData("Offline head rejoin",address,ServerData.Type.OTHER),false,null);deadline=System.currentTimeMillis()+120000;phase=2;return;}
   if(phase<0){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+120000;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),new ServerData("Offline head test",address,ServerData.Type.OTHER),false,null);phase=connections==0?0:2;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Offline head timeout phase="+phase+" waiting="+waiting);
   if(mc.screen instanceof AuthScreen auth&&!submitted){for(var child:auth.children())if(child instanceof EditBox box)box.setValue("offline head test password");var field=AuthScreen.class.getDeclaredField("submit");field.setAccessible(true);((Button)field.get(auth)).onPress();submitted=true;return;}
   if(mc.player==null||!SkinClient.available()||!ServerMenuClient.available()||!SkinClient.library.has("entries"))return;
   if(phase==0){mc.player.connection.sendCommand("offlineHeadFixture");phase=1;}
   else if(phase==1){for(var message:((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages()){String text=message.content().getString();if(text.startsWith("HEAD_READY "))hash=text.substring(11);}if(hash==null)return;
    for(var stack:mc.player.getInventory().items)if(stack.is(Items.PLAYER_HEAD)&&stack.get(DataComponents.PROFILE)!=null){resolving=stack.get(DataComponents.PROFILE).resolve();phase=2;break;}}
   else if(phase==2){if(!resolving.isDone())return;profile=resolving.join();if(!profile.id().orElseThrow().equals(dev.abros.rivet.server.OfflineHeadFixture.OWNER))throw new IllegalStateException("Offline head resolved to another UUID: "+profile.id());
    if(mc.getConnection().getPlayerInfo(profile.id().orElseThrow())!=null)throw new IllegalStateException("Fixture owner unexpectedly online");phase=3;}
   else if(phase==3){var skin=mc.getSkinManager().getInsecureSkin(profile.gameProfile());if(!skin.texture().toString().equals("rivet:skins/"+hash))return;
    var stale=new ResolvableProfile(new com.mojang.authlib.GameProfile(net.minecraft.Util.NIL_UUID,"_tot_keks_"));
    var expected=net.minecraft.client.renderer.RenderType.entityTranslucent(skin.texture());
    var rendered=net.minecraft.client.renderer.blockentity.SkullBlockRenderer.getRenderType(net.minecraft.world.level.block.SkullBlock.Types.PLAYER,stale);
    if(rendered!=expected){if(System.currentTimeMillis()>deadline-30000)throw new IllegalStateException("Already resolved head did not use Rivet skin");return;}
    waiting="wrong UUID";var wrong=new ResolvableProfile(new com.mojang.authlib.GameProfile(java.util.UUID.fromString("873806dc-24b9-47c0-87ac-50c8fd2549f1"),"_tot_keks_"));
    if(net.minecraft.client.renderer.blockentity.SkullBlockRenderer.getRenderType(net.minecraft.world.level.block.SkullBlock.Types.PLAYER,wrong)!=expected)return;
    if(net.minecraft.client.renderer.blockentity.SkullBlockRenderer.getRenderType(net.minecraft.world.level.block.SkullBlock.Types.PLAYER,profile)!=expected)return;
    var automatic=new com.mojang.authlib.GameProfile(profile.gameProfile().getId(),profile.gameProfile().getName());
    if(!profile.properties().containsKey("rivet:head_account"))throw new IllegalStateException("Name resolution lost head provenance");
    automatic.getProperties().putAll(profile.properties());automatic.getProperties().put("textures",new com.mojang.authlib.properties.Property("textures","eyJ0ZXh0dXJlcyI6e319"));
    if(net.minecraft.client.renderer.blockentity.SkullBlockRenderer.getRenderType(net.minecraft.world.level.block.SkullBlock.Types.PLAYER,new ResolvableProfile(automatic))!=expected)throw new IllegalStateException("Automatically fetched texture hid Rivet skin");
    waiting="online owner";var online=new ResolvableProfile(mc.player.getGameProfile());
    if(net.minecraft.client.renderer.blockentity.SkullBlockRenderer.getRenderType(net.minecraft.world.level.block.SkullBlock.Types.PLAYER,online)!=expected)return;
    waiting="placed head";if(!(mc.level.getBlockEntity(new net.minecraft.core.BlockPos(1,100,0)) instanceof net.minecraft.world.level.block.entity.SkullBlockEntity block)||block.getOwnerProfile()==null)return;
    if(net.minecraft.client.renderer.blockentity.SkullBlockRenderer.getRenderType(net.minecraft.world.level.block.SkullBlock.Types.PLAYER,block.getOwnerProfile())!=expected)return;
    var properties=new com.mojang.authlib.properties.PropertyMap();properties.put("textures",new com.mojang.authlib.properties.Property("textures","eyJ0ZXh0dXJlcyI6e319"));var decorative=new ResolvableProfile(Optional.of("Decoration"),Optional.empty(),properties);if(decorative.resolve().join()!=decorative)throw new IllegalStateException("Decorative profile rewritten");
    var decoration=new ResolvableProfile(Optional.of("_ToT_KeKs_"),Optional.of(dev.abros.rivet.server.OfflineHeadFixture.OWNER),properties);
    if(net.minecraft.client.renderer.blockentity.SkullBlockRenderer.getRenderType(net.minecraft.world.level.block.SkullBlock.Types.PLAYER,decoration)!=net.minecraft.client.renderer.RenderType.entityTranslucent(SkinClient.vanillaSkin(decoration.gameProfile()).texture()))throw new IllegalStateException("Decorative texture overridden");
    if(connections++==0){phase=4;next=System.currentTimeMillis()+1000;submitted=false;mc.level.disconnect();mc.disconnect();return;}
    System.out.println("RIVET_OFFLINE_HEAD_OK resolved=true wrongUuid=true lowercase=true placed=true online=true reconnect=true name=true stableUuid=true offline=true customTexture=true decorative=true");done=true;mc.stop();}
  }catch(Throwable failure){done=true;System.out.println("RIVET_OFFLINE_HEAD_FAILED phase="+phase);failure.printStackTrace();mc.stop();}
 }
}
