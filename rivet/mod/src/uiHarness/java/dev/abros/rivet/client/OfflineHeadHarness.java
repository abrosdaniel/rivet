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
 private static boolean switched,libraryFresh;private static String originalSkin,newHash;
 private static net.minecraft.world.item.ItemStack issued;
 private static net.minecraft.world.level.block.entity.SkullBlockEntity placed;
 private static net.minecraft.client.renderer.RenderType originalRender,newRender,ordinaryRender;
 private static CompletableFuture<ResolvableProfile> resolving;private static ResolvableProfile profile;private static String hash,waiting="start";
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  String address=System.getenv("RIVET_OFFLINE_HEAD_ADDRESS");if(address==null||done)return;var mc=Minecraft.getInstance();try{
   if(phase==4){if(System.currentTimeMillis()<next)return;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),new ServerData("Offline head rejoin",address,ServerData.Type.OTHER),false,null);deadline=System.currentTimeMillis()+120000;phase=2;return;}
   if(phase<0){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+120000;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),new ServerData("Offline head test",address,ServerData.Type.OTHER),false,null);phase=connections==0?0:2;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Offline head timeout phase="+phase+" waiting="+waiting);
   if(mc.screen instanceof AuthScreen auth&&!submitted){for(var child:auth.children())if(child instanceof EditBox box)box.setValue("offline head test password");var field=AuthScreen.class.getDeclaredField("submit");field.setAccessible(true);((Button)field.get(auth)).onPress();submitted=true;return;}
   if(mc.player==null||!SkinClient.available()||!ServerMenuClient.available()||!SkinClient.library.has("entries"))return;
   if(phase>=5&&phase<=8){checkSkinChange(mc);return;}
   if(phase==0){mc.player.connection.sendCommand("offlineHeadFixture");phase=1;}
   else if(phase==1){for(var message:((dev.abros.rivet.mixin.ChatHistoryAccessor)mc.gui.getChat()).rivet$messages()){String text=message.content().getString();if(text.startsWith("HEAD_READY "))hash=text.substring(11);}if(hash==null)return;
    for(var stack:mc.player.getInventory().items)if(stack.is(Items.PLAYER_HEAD)&&stack.get(DataComponents.PROFILE)!=null&&stack.get(DataComponents.PROFILE).name().orElse("").equalsIgnoreCase(dev.abros.rivet.server.OfflineHeadFixture.NAME)){resolving=stack.get(DataComponents.PROFILE).resolve();phase=2;break;}}
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
    if(!switched){
     if(SkinClient.busy)return;
     if(!libraryFresh){libraryFresh=true;SkinClient.command("list","",false);return;}
     for(var stack:mc.player.getInventory().items)if(stack.is(Items.PLAYER_HEAD)&&stack.get(DataComponents.PROFILE)!=null&&stack.get(DataComponents.PROFILE).name().orElse("").equalsIgnoreCase(mc.player.getGameProfile().getName()))issued=stack;
     if(issued==null){waiting="issued online head";return;}
     var resolved=issued.get(DataComponents.PROFILE).resolve();if(!resolved.isDone())return;issued.set(DataComponents.PROFILE,resolved.join());
     if(!(mc.level.getBlockEntity(new net.minecraft.core.BlockPos(2,100,0)) instanceof net.minecraft.world.level.block.entity.SkullBlockEntity ownBlock))return;placed=ownBlock;
     if(render(issued.get(DataComponents.PROFILE))!=expected||render(placed.getOwnerProfile())!=expected)return;
     originalSkin=dev.abros.rivet.core.Json.str(SkinClient.library.getAsJsonObject("profile"),"active");originalRender=expected;
     var image=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setRGB(x,y,0xff4568ee);
     var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
     newHash=dev.abros.rivet.core.Hashes.sha256(dev.abros.rivet.core.skins.SkinImage.normalize(bytes.toByteArray(),1024*1024));
     newRender=net.minecraft.client.renderer.RenderType.entityTranslucent(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("rivet","skins/"+newHash));
     ordinaryRender=net.minecraft.client.renderer.RenderType.entityTranslucent(SkinClient.vanillaSkin(mc.player.getGameProfile()).texture());
     var diskField=SkinClient.class.getDeclaredField("DISK");diskField.setAccessible(true);((java.util.concurrent.ExecutorService)diskField.get(null)).execute(()->{try{Thread.sleep(1000);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
     phase=5;deadline=System.currentTimeMillis()+30000;SkinClient.upload("Head switch fixture",false,bytes.toByteArray());return;
    }
    checkRefreshFrames(wrong,expected);
    if(connections++==0){phase=4;next=System.currentTimeMillis()+1000;submitted=false;mc.level.disconnect();mc.disconnect();return;}
    System.out.println("RIVET_OFFLINE_HEAD_OK resolved=true wrongUuid=true lowercase=true placed=true online=true reconnect=true name=true stableUuid=true offline=true customTexture=true decorative=true");done=true;mc.stop();}
  }catch(Throwable failure){done=true;System.out.println("RIVET_OFFLINE_HEAD_FAILED phase="+phase);failure.printStackTrace();mc.stop();}
 }
 private static net.minecraft.client.renderer.RenderType render(ResolvableProfile profile){return net.minecraft.client.renderer.blockentity.SkullBlockRenderer.getRenderType(net.minecraft.world.level.block.SkullBlock.Types.PLAYER,profile);}
 private static void checkSkinChange(Minecraft mc){
  if(!mc.player.getInventory().items.contains(issued)||mc.level.getBlockEntity(new net.minecraft.core.BlockPos(2,100,0))!=placed)throw new IllegalStateException("Head was replaced during skin switch");
  var item=render(issued.get(DataComponents.PROFILE));var block=render(placed.getOwnerProfile());
  var alternate=phase>=7?ordinaryRender:newRender;
  if(item!=originalRender&&item!=alternate||block!=originalRender&&block!=alternate)throw new IllegalStateException("Skin switch flashed a default texture phase="+phase+" item="+item+" block="+block+" expected="+alternate+" player="+mc.player.getGameProfile().getId()+" issued="+issued.get(DataComponents.PROFILE).id()+" placed="+placed.getOwnerProfile().id());
  var wanted=phase==5?newRender:phase==7?ordinaryRender:originalRender;if(SkinClient.busy||item!=wanted||block!=wanted)return;
  if(phase==5){System.out.println("RIVET_HEAD_SKIN_CHANGED issued=true placed=true transport=upload");phase=6;SkinClient.command("select",originalSkin,false);return;}
  if(phase==6){System.out.println("RIVET_HEAD_SKIN_CHANGED issued=true placed=true transport=select restored=true");phase=7;SkinClient.command("select","",false);return;}
  if(phase==7){System.out.println("RIVET_HEAD_SKIN_CHANGED issued=true placed=true ordinary=true");phase=8;SkinClient.command("select",originalSkin,false);return;}
  switched=true;phase=3;deadline=System.currentTimeMillis()+120000;
 }
 private static void checkRefreshFrames(ResolvableProfile wrong,net.minecraft.client.renderer.RenderType expected)throws Exception{
  var field=SkinClient.class.getDeclaredField("identities");field.setAccessible(true);var lookup=field.get(null);var cacheField=lookup.getClass().getDeclaredField("cache");cacheField.setAccessible(true);
  @SuppressWarnings("unchecked")var cache=(Map<String,Object>)cacheField.get(lookup);
  cache.remove("id:"+wrong.gameProfile().getId());
  for(int frame=0;frame<120;frame++)if(net.minecraft.client.renderer.blockentity.SkullBlockRenderer.getRenderType(net.minecraft.world.level.block.SkullBlock.Types.PLAYER,wrong)!=expected)throw new IllegalStateException("Head flashed to default while UUID refresh was pending, render="+frame);
  String key="name:_tot_keks_";var cached=cache.get(key);if(cached==null)throw new IllegalStateException("Missing warmed name lookup");var identity=cached.getClass().getDeclaredMethod("identity");identity.setAccessible(true);var constructor=cached.getClass().getDeclaredConstructor(Optional.class,long.class);constructor.setAccessible(true);cache.put(key,constructor.newInstance(identity.invoke(cached),0L));
  for(int frame=0;frame<120;frame++)if(net.minecraft.client.renderer.blockentity.SkullBlockRenderer.getRenderType(net.minecraft.world.level.block.SkullBlock.Types.PLAYER,wrong)!=expected)throw new IllegalStateException("Head flashed to default while name refresh was pending, render="+frame);
  System.out.println("RIVET_HEAD_REFRESH_STABLE renders=240 connection="+connections);
 }

}
