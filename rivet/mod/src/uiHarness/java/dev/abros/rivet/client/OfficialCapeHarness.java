package dev.abros.rivet.client;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
/** Exercises real server delivery and the wardrobe/world consumers with a local Auth-style UUID. */
final class OfficialCapeHarness {
 private static int step;private static String hash,customHash;private static long next;
 static void tick(Minecraft mc,long now)throws Exception{
  if(now<next||SkinClient.busy||!SkinClient.library.has("profile"))return;next=now+1200;
  switch(step){
   case 0->{if(System.getenv("RIVET_OFFICIAL_RELOAD")==null)mc.getConnection().sendCommand("officialCapeFixture");step++;}
   case 1->{SkinClient.command("capeSelect","",false);step++;}
   case 2->{var profile=SkinClient.library.getAsJsonObject("profile");hash=Json.opt(profile,"officialCapeHash","");if(hash.isEmpty())return;var cape=mc.player.getSkin().capeTexture();if(cape==null||!cape.getPath().endsWith(hash))return;
    check(cape.equals(SkinClient.ordinarySkin().capeTexture()),"official wardrobe texture");check(mc.player.getSkin().elytraTexture()==null,"official elytra texture");
    mc.setScreen(new SkinsScreen(null,true));step++;}
   case 3->{var f=SkinsScreen.class.getDeclaredField("previewPlayer");f.setAccessible(true);var player=(net.minecraft.client.player.RemotePlayer)f.get(mc.screen);check(player.getSkin().capeTexture()!=null&&player.getSkin().capeTexture().getPath().endsWith(hash),"wardrobe actual model");
    UiCaptureHarness.grab(new java.io.File(System.getenv("RIVET_HD_CAPTURES")),"official-cape.png",mc.getMainRenderTarget(),m->{});SkinClient.command("capeHide","",false);step++;}
   case 4->{check(mc.player.getSkin().capeTexture()==null,"hidden official cape");check(SkinClient.ordinarySkin().capeTexture()!=null,"official preview available while hidden");SkinClient.command("capeSelect","",false);step++;}
   case 5->{check(mc.player.getSkin().capeTexture()!=null&&mc.player.getSkin().capeTexture().getPath().endsWith(hash),"restored official cape");var image=new java.awt.image.BufferedImage(64,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<17;y++)for(int x=0;x<22;x++)image.setRGB(x,y,0xffaa6688);var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"PNG",out);byte[] png=out.toByteArray();customHash=dev.abros.rivet.core.Hashes.sha256(dev.abros.rivet.core.skins.CapeImage.normalize(png,1048576));SkinClient.upload("Official switching test",false,png,true);step++;}
   case 6->{var cape=mc.player.getSkin().capeTexture();if(cape==null||!cape.getPath().endsWith(customHash))return;check(SkinClient.ordinarySkin().capeTexture().getPath().endsWith(hash),"custom does not replace official preview");check(net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/elytra.png").equals(mc.player.getSkin().elytraTexture()),"custom empty wings preserve vanilla elytra");SkinClient.command("capeSelect","",false);step++;}
   case 7->{var cape=mc.player.getSkin().capeTexture();if(cape==null||!cape.getPath().endsWith(hash))return;check(mc.player.getSkin().elytraTexture()==null,"official wings restored after custom cape");System.out.println("RIVET_OFFICIAL_CAPE_OK");mc.stop();step++;}
  }
 }
 private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
