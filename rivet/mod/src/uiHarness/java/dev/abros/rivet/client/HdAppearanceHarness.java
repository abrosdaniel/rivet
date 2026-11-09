package dev.abros.rivet.client;

import dev.abros.rivet.core.Hashes;
import dev.abros.rivet.core.Json;
import dev.abros.rivet.core.skins.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;

/** Uses the real upload, profile, texture-manager, wardrobe and skull renderer paths. */
final class HdAppearanceHarness {
 private static int step,width,capeWidth;private static long next;private static String skinHash,capeHash,skinId,before;private static boolean done;
 static void tick(Minecraft mc,long now)throws Exception{
  if(done||now<next||!SkinClient.available()||SkinClient.busy||!SkinClient.library.has("maxResolution"))return;next=now+700;
  if(System.getenv("RIVET_HD_RELOAD")!=null){
   var profile=SkinClient.library.getAsJsonObject("profile");String savedSkin=Json.str(profile,"hash"),savedCape=Json.str(profile,"capeHash");var appearance=mc.player.getSkin();
   if(savedSkin.isEmpty()||savedCape.isEmpty()||!appearance.texture().getPath().endsWith(savedSkin)||appearance.capeTexture()==null||!appearance.capeTexture().getPath().endsWith(savedCape))return;
   int size=SkinClient.library.get("maxResolution").getAsInt();checkPixels(mc,appearance.texture(),size,size);checkPixels(mc,appearance.capeTexture(),size,size/2);
   check(SkullBlockRenderer.getRenderType(SkullBlock.Types.PLAYER,new ResolvableProfile(mc.player.getGameProfile()))==RenderType.entityTranslucent(appearance.texture()),"persisted HD skull renderer");
   check(appearance.elytraTexture()==null,"persisted HD wing area");System.out.println("RIVET_HD_RELOAD_OK width="+size);done=true;mc.stop();return;
  }
  switch(step){
   case 0->{width=SkinClient.library.get("maxResolution").getAsInt();capeWidth=SkinClient.library.getAsJsonObject("capes").get("maxResolution").getAsInt();before=Json.str(SkinClient.library.getAsJsonObject("profile"),"hash");SkinClient.upload("HD over limit",false,png(width*2,false));step++;}
   case 1->{check(!SkinClient.status.isEmpty(),"server rejected oversized skin");check(before.equals(Json.str(SkinClient.library.getAsJsonObject("profile"),"hash")),"rejection preserves skin");next=now+3500;step++;}
   case 2->{byte[] png=System.getenv("RIVET_HD_SKIN")==null?png(width,false):java.nio.file.Files.readAllBytes(java.nio.file.Path.of(System.getenv("RIVET_HD_SKIN")));skinHash=Hashes.sha256(SkinImage.normalize(png,8388608));SkinClient.upload("HD "+width,false,png);step++;}
   case 3->{if(!mc.player.getSkin().texture().getPath().endsWith(skinHash))return;skinId=Json.str(SkinClient.library.getAsJsonObject("profile"),"active");checkPixels(mc,mc.player.getSkin().texture(),width,width);mc.options.guiScale().set(3);mc.resizeDisplay();RivetHud.QUEUE.clear();mc.setScreen(new SkinsScreen(null));step++;}
   case 4->{capture(mc,"hd-skin-layers-"+width);var field=SkinsScreen.class.getDeclaredField("secondLayer");field.setAccessible(true);field.setBoolean(mc.screen,false);((SkinsScreen)mc.screen).rebuildWidgets();step++;}
   case 5->{capture(mc,"hd-skin-base-"+width);SkinClient.command("model",skinId,true);step++;}
   case 6->{if(mc.player.getSkin().model()!=net.minecraft.client.resources.PlayerSkin.Model.SLIM)return;var owner=new ResolvableProfile(mc.player.getGameProfile());var expected=RenderType.entityTranslucent(mc.player.getSkin().texture());if(SkullBlockRenderer.getRenderType(SkullBlock.Types.PLAYER,owner)!=expected)return;check(mc.getSkinManager().getInsecureSkin(mc.player.getGameProfile()).texture().equals(mc.player.getSkin().texture()),"skin manager HD path");mc.setScreen(new HeadScreen(owner));step++;}
   case 7->{capture(mc,"hd-head-"+width);before=Json.str(SkinClient.library.getAsJsonObject("profile"),"capeHash");SkinClient.upload("HD cape over limit",false,png(capeWidth*2,true),true);step++;}
   case 8->{check(!SkinClient.status.isEmpty(),"server rejected oversized cape");check(before.equals(Json.str(SkinClient.library.getAsJsonObject("profile"),"capeHash")),"rejection preserves cape");next=now+3500;step++;}
   case 9->{byte[] png=png(capeWidth,true);capeHash=Hashes.sha256(CapeImage.normalize(png,2097152));SkinClient.upload("HD cape "+capeWidth,false,png,true);step++;}
   case 10->{var cape=mc.player.getSkin().capeTexture();if(cape==null||!cape.getPath().endsWith(capeHash))return;checkPixels(mc,cape,capeWidth,capeWidth/2);check(mc.player.getSkin().elytraTexture()==null,"HD cape wing area detected");mc.setScreen(new SkinsScreen(null,true));equipment(mc,3);step++;}
   case 11->{capture(mc,"hd-elytra-"+capeWidth);equipment(mc,0);step++;}
   case 12->{capture(mc,"hd-cape-"+capeWidth);var bytes=SkinClient.class.getDeclaredField("textureBytes");bytes.setAccessible(true);check(bytes.getLong(null)<=32L*1024*1024,"decoded texture budget");System.out.println("RIVET_HD_APPEARANCE_OK width="+width+" capeWidth="+capeWidth);done=true;if(System.getenv("RIVET_HD_EXIT")!=null)mc.stop();}
  }
 }
 private static void equipment(Minecraft mc,int mode)throws Exception{var f=SkinsScreen.class.getDeclaredField("previewEquipment");f.setAccessible(true);f.setInt(mc.screen,mode);var m=SkinsScreen.class.getDeclaredMethod("updateEquipment");m.setAccessible(true);m.invoke(mc.screen);}
 private static void checkPixels(Minecraft mc,net.minecraft.resources.ResourceLocation id,int w,int h){var texture=mc.getTextureManager().getTexture(id);check(texture instanceof net.minecraft.client.renderer.texture.DynamicTexture,"dynamic HD texture");var pixels=((net.minecraft.client.renderer.texture.DynamicTexture)texture).getPixels();check(pixels!=null&&pixels.getWidth()==w&&pixels.getHeight()==h,"HD retained without downsampling");}
 private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 private static void capture(Minecraft mc,String name){UiCaptureHarness.grab(new java.io.File(System.getenv("RIVET_HD_CAPTURES")),name+".png",mc.getMainRenderTarget(),m->{});}
 private static byte[] png(int width,boolean cape)throws Exception{
  int h=cape?width/2:width,k=width/64;var image=new java.awt.image.BufferedImage(width,h,java.awt.image.BufferedImage.TYPE_INT_ARGB);
  for(int y=0;y<h;y++)for(int x=0;x<width;x++){
   int color=0xff1e648a|((x+y)%8)*0x010101;
   if(cape){color=x>=22*k&&x<46*k&&y<22*k?0xfff2b54b:((x/k)%5==0?0:0xff39a873);}
   else if(y<16*k){color=x>=32*k?((x/k+y/k)%3==0?0x88f4b942:0):(x/k%4==0?0xfff3dca7:0xff458fb0);}
   else if(y>=32*k&&y<48*k||y>=48*k&&x<16*k||y>=48*k&&x>=48*k)color=(x/k+y/k)%3==0?0x99f4b942:0;
   image.setRGB(x,y,color);
  }
  var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"PNG",out);return out.toByteArray();
 }
 private static final class HeadScreen extends Screen {
  private final net.minecraft.world.item.ItemStack head=new net.minecraft.world.item.ItemStack(Items.PLAYER_HEAD);
  HeadScreen(ResolvableProfile owner){super(Component.literal("HD head"));head.set(net.minecraft.core.component.DataComponents.PROFILE,owner);}
  @Override public void render(GuiGraphics g,int mx,int my,float dt){g.fill(0,0,width,height,0xff252533);g.drawCenteredString(font,"HD head + face: "+HdAppearanceHarness.width+" px",width/2,25,0xffffff);g.pose().pushPose();g.pose().translate(width/2f-80,height/2f-32,0);g.pose().scale(4,4,4);g.renderItem(head,0,0);g.pose().popPose();net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g,minecraft.player.getSkin(),width/2+24,height/2-32,64);}
  @Override public boolean isPauseScreen(){return false;}
 }
}
