package dev.abros.rivet.client;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import dev.abros.rivet.core.Json;
/** Opt-in integration smoke against an isolated local server. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class SkinUiHarness {
 private static String address(){return System.getenv().getOrDefault("RIVET_SKIN_SMOKE_ADDRESS","127.0.0.1:25576");}
 private static java.util.function.Supplier<net.minecraft.client.resources.PlayerSkin> retainedSkin;
 private static final java.util.List<VisualMatrixHarness.Frame> WARDROBE=VisualMatrixHarness.samples(3,0,1,2);private static int wardrobeFrame;
 private static CommunityScreen returningHome;
 private static String capeId,outfitId;private static int step;private static long next,deadline;private static String id;private static byte[] png;
 @SubscribeEvent public static void tick(ClientTickEvent.Post event){String output=System.getenv("RIVET_SKIN_SMOKE");if(output==null||step<0)return;var mc=Minecraft.getInstance();long now=System.currentTimeMillis();if(now<next)return;
  try{if(deadline==0)deadline=now+150000;if(now>deadline)throw new IllegalStateException("Skin smoke timeout, step "+step+" status "+SkinClient.status);switch(step){
   case 0->{if(!(mc.screen instanceof TitleScreen))return;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address()),new ServerData("Skin smoke",address(),ServerData.Type.OTHER),false,null);step++;}
   case 1->{if(mc.player==null||!SkinClient.available())return;if(System.getenv("RIVET_VOTE_NOTICE_TEST")!=null){if(!Json.opt(ServerMenuClient.moderationVote,"status","").equals("open"))return;if(RivetHud.QUEUE.advance(net.minecraft.Util.getMillis(),2,true).isEmpty())return;System.out.println("RIVET_LATE_JOIN_VOTE_NOTICE_OK");}next=now+1500;step++;}
   case 2->{mc.options.guiScale().set(1);mc.resizeDisplay();returningHome=new CommunityScreen(null,"home","");mc.setScreen(returningHome);press(mc,Client.text("wardrobe.title"));if(!(mc.screen instanceof SkinsScreen))throw new IllegalStateException("Appearance home entry failed");mc.screen.onClose();step=41;}
   case 41->{if(mc.screen!=returningHome)throw new IllegalStateException("Wardrobe lost home parent");if(!returningHome.data.has("entries"))return;if(!returningHome.status.isEmpty())throw new IllegalStateException("Home did not recover after wardrobe");System.out.println("RIVET_WARDROBE_HOME_RETURN_OK");press(mc,"⋯");press(mc,Client.text("wardrobe.title"));if(!(mc.screen instanceof SkinsScreen))throw new IllegalStateException("Appearance profile entry failed");mc.screen.onClose();SkinsScreen.open(null);step=3;}
   case 3->{if(SkinClient.busy)return;retainedSkin=mc.getSkinManager().lookupInsecure(mc.player.getGameProfile());if(!SkinClient.library.has("entries"))throw new IllegalStateException(SkinClient.status);var image=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setRGB(x,y,y<16?0xffeeaa88:0xff6622aa);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"PNG",bytes);png=bytes.toByteArray();SkinClient.upload("Проверка скина",false,png);step++;}
   case 4->{if(SkinClient.busy)return;if(SkinClient.library.getAsJsonArray("entries").isEmpty())throw new IllegalStateException(SkinClient.status);id=Json.str(SkinClient.library.getAsJsonObject("profile"),"active");if(id.isEmpty())throw new IllegalStateException("Upload did not activate");step++;next=now+10000;}
   case 5->{if(!mc.player.getSkin().texture().getNamespace().equals("rivet"))return;checkSkinPaths(mc,true);var dir=new java.io.File(output);dir.mkdirs();UiCaptureHarness.grab(dir,"skin-wide.png",mc.getMainRenderTarget(),m->{});SkinClient.command("model",id,true);step++;}
   case 6->{if(SkinClient.busy)return;if(mc.player.getSkin().model()!=net.minecraft.client.resources.PlayerSkin.Model.SLIM)return;checkSkinPaths(mc,true);next=now+1000;step++;}
   case 7->{verifyEquipment(mc);var preview=SkinsScreen.class.getDeclaredField("previewPlayer");preview.setAccessible(true);var entity=(net.minecraft.client.player.RemotePlayer)preview.get(mc.screen);for(var part:net.minecraft.world.entity.player.PlayerModelPart.values())if(!entity.isModelPartShown(part))throw new IllegalStateException("Hidden overlay "+part);for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().startsWith("Второй слой:")){b.onPress();break;}if(entity.isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart.HAT))throw new IllegalStateException("Overlay toggle failed");for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().startsWith("Второй слой:")){b.onPress();break;}var rotation=SkinsScreen.class.getDeclaredField("rotation");rotation.setAccessible(true);rotation.setFloat(mc.screen,1f);for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals("Вернуть вид")){b.onPress();break;}if(rotation.getFloat(mc.screen)!=0)throw new IllegalStateException("Preview reset failed");UiCaptureHarness.grab(new java.io.File(output),"skin-slim.png",mc.getMainRenderTarget(),m->{});SkinClient.command("rename",id,false,"Новое название");step++;}
   case 8->{if(SkinClient.busy)return;if(!"Новое название".equals(Json.str(SkinClient.library.getAsJsonArray("entries").get(0).getAsJsonObject(),"name")))throw new IllegalStateException("Rename failed");SkinClient.command("select","",false);step++;}
   case 9->{if(SkinClient.busy)return;if(!Json.str(SkinClient.library.getAsJsonObject("profile"),"active").isEmpty())throw new IllegalStateException("Reset failed");checkSkinPaths(mc,false);press(mc,Client.text("ui.actions_928503bd"));press(mc,Client.text("skin.undoAppearance"));step=12;}
   case 12->{if(SkinClient.busy)return;if(!id.equals(Json.str(SkinClient.library.getAsJsonObject("profile"),"active")))throw new IllegalStateException("Undo did not restore selected skin");if(mc.player.getSkin().model()!=net.minecraft.client.resources.PlayerSkin.Model.SLIM)return;checkSkinPaths(mc,true);if(SkinClient.library.get("canUndoAppearance").getAsBoolean())throw new IllegalStateException("Undo remained available");SkinClient.command("select","",false);step=13;}
   case 13->{if(SkinClient.busy)return;checkSkinPaths(mc,false);var image=new java.awt.image.BufferedImage(64,32,java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<32;y++)for(int x=0;x<64;x++)image.setRGB(x,y,0xffdd8844);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"PNG",bytes);SkinClient.upload("Cape test",false,bytes.toByteArray(),true);step=14;}
   case 14->{if(SkinClient.busy)return;capeId=Json.str(SkinClient.library.getAsJsonObject("capes"),"active");if(capeId.isEmpty())throw new IllegalStateException("Cape upload did not activate");var cape=mc.player.getSkin().capeTexture();if(cape==null||!cape.getPath().startsWith("capes/"))return;mc.setScreen(new SkinsScreen(null,true));UiGeometryHarness.verify(mc.screen);var rotation=SkinsScreen.class.getDeclaredField("rotation");rotation.setAccessible(true);rotation.setFloat(mc.screen,(float)Math.PI);var preview=SkinsScreen.class.getDeclaredField("previewPlayer");preview.setAccessible(true);if(!cape.equals(((net.minecraft.client.player.RemotePlayer)preview.get(mc.screen)).getSkin().capeTexture()))throw new IllegalStateException("Cape preview differs from player");var dir=new java.io.File(output);dir.mkdirs();UiCaptureHarness.grab(dir,"cape-preview.png",mc.getMainRenderTarget(),m->{});SkinClient.command("outfitSave","",false,"Test outfit");step=19;}
   case 15->{if(SkinClient.busy)return;if(mc.player.getSkin().capeTexture()!=null)throw new IllegalStateException("Hidden cape still rendered");var wheel=new QuickSkinsScreen(mc.screen);mc.setScreen(wheel);var idsField=QuickSkinsScreen.class.getDeclaredField("ids");idsField.setAccessible(true);var ids=(java.util.List<?>)idsField.get(wheel);int selected=ids.indexOf("outfit:"+outfitId);if(selected<0)throw new IllegalStateException("Outfit absent from wheel");var choose=QuickSkinsScreen.class.getDeclaredMethod("choose",int.class);choose.setAccessible(true);choose.invoke(wheel,selected);step=16;}
   case 16->{if(SkinClient.busy)return;if(mc.player.getSkin().capeTexture()==null)return;press(mc,Client.text("ui.actions_928503bd"));press(mc,Client.text("skin.undoAppearance"));step=21;}
   case 21->{if(SkinClient.busy)return;if(mc.player.getSkin().capeTexture()!=null)return;if(!SkinClient.library.getAsJsonObject("profile").get("capeHidden").getAsBoolean()||SkinClient.library.get("canUndoAppearance").getAsBoolean())throw new IllegalStateException("Whole appearance undo did not restore hidden cape exactly once");SkinClient.command("outfitSelect",outfitId,false);step=22;}
   case 22->{if(SkinClient.busy)return;if(mc.player.getSkin().capeTexture()==null)return;SkinClient.command("capeDelete",capeId,false);step=17;}
   case 17->{if(SkinClient.busy)return;if(!SkinClient.library.getAsJsonObject("capes").getAsJsonArray("entries").isEmpty())throw new IllegalStateException("Cape deletion failed");if(!java.util.Objects.equals(mc.player.getSkin().capeTexture(),SkinClient.ordinarySkin().capeTexture()))throw new IllegalStateException("Cape deletion lost official fallback");SkinsScreen.open(null);step=18;}
   case 19->{if(SkinClient.busy)return;outfitId=Json.str(SkinClient.library.getAsJsonArray("outfits").get(0).getAsJsonObject(),"id");step=30;}
   case 30->{
    if(wardrobeFrame==WARDROBE.size()){UiPalette.preview(0);mc.options.guiScale().set(1);mc.resizeDisplay();mc.setScreen(new SkinsScreen(null,true));press(mc,Client.text("cape.hide"));if(mc.player.getSkin().capeTexture()==null||SkinClient.busy)throw new IllegalStateException("Cape preview applied before confirmation");press(mc,Client.text("map.caveApply"));step=15;break;}
    var frame=WARDROBE.get(wardrobeFrame);frame.apply();mc.setScreen(new SkinsScreen(null));String label=Client.text(frame.scene()==0?"ui.skins_bef1a24f":frame.scene()==1?"cape.title":"outfit.title");
    if(mc.screen.children().stream().noneMatch(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(label))){var selector=mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().endsWith(" ▾")).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow();selector.onPress();}
    press(mc,label);UiGeometryHarness.verify(mc.screen);if(!(mc.screen instanceof SkinsScreen))throw new IllegalStateException("Category opened nested screen");
    if(frame.scene()==0){press(mc,"Новое название");if(SkinClient.busy||!Json.str(SkinClient.library.getAsJsonObject("profile"),"active").isEmpty())throw new IllegalStateException("Preview applied skin");var preview=SkinsScreen.class.getDeclaredField("previewPlayer");preview.setAccessible(true);if(!mc.player.getSkin().capeTexture().equals(((net.minecraft.client.player.RemotePlayer)preview.get(mc.screen)).getSkin().capeTexture()))throw new IllegalStateException("Skin preview lost active cape");}
    if(frame.scene()==2){press(mc,"Test outfit");if(SkinClient.busy)throw new IllegalStateException("Preview applied outfit");
     var outfits=SkinClient.library.getAsJsonArray("outfits");var duplicate=outfits.get(0).getAsJsonObject().deepCopy();duplicate.addProperty("id","duplicate-preview");duplicate.addProperty("name","Same appearance");outfits.add(duplicate);((SkinsScreen)mc.screen).rebuildWidgets();press(mc,"Same appearance");
     if(mc.screen.children().stream().noneMatch(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(Client.text("wardrobe.applied"))&&!b.active))throw new IllegalStateException("Equivalent outfit was not marked applied");
     outfits.remove(duplicate);((SkinsScreen)mc.screen).updated();
    }
    var search=mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.EditBox).map(c->(net.minecraft.client.gui.components.EditBox)c).findFirst().orElseThrow();mc.screen.setFocused(search);search.setValue("no-match-wardrobe");
    if(mc.screen.children().stream().anyMatch(c->c instanceof UiSkinCard))throw new IllegalStateException("Search did not filter collection");
    var filtered=(net.minecraft.client.gui.components.EditBox)mc.screen.getFocused();if(!filtered.getValue().equals("no-match-wardrobe"))throw new IllegalStateException("Search lost keyboard focus");filtered.setValue("");
    if(mc.screen.children().stream().noneMatch(c->c instanceof UiSkinCard))throw new IllegalStateException("Clearing search did not restore collection");
    step=31;next=now+200;
   }
   case 31->{UiGeometryHarness.verify(mc.screen);UiCaptureHarness.grab(new java.io.File(output),String.format("wardrobe-%03d.png",wardrobeFrame),mc.getMainRenderTarget(),m->{});wardrobeFrame++;step=30;}
   case 18->{if(SkinClient.busy)return;SkinClient.command("outfitDelete",outfitId,false);step=20;}
   case 20->{if(SkinClient.busy)return;SkinClient.command("delete",id,false);step=10;}
   case 10->{if(SkinClient.busy)return;if(!SkinClient.library.getAsJsonArray("entries").isEmpty())throw new IllegalStateException("Delete failed");System.out.println("RIVET_SKINS_INTEGRATION_OK");if(System.getenv("RIVET_SKIN_PICKER_TEST")!=null){var choose=SkinsScreen.class.getDeclaredMethod("chooseFile");choose.setAccessible(true);choose.invoke(mc.screen);next=now+70000;step=11;}else{step=-1;mc.stop();}}
   case 11->{if(mc.player==null||!mc.getConnection().getConnection().isConnected())throw new IllegalStateException("Connection stopped during picker");System.out.println("RIVET_SKIN_PICKER_70_SECONDS_OK");step=-1;}
  }}catch(Exception error){error.printStackTrace();System.out.println("RIVET_SKINS_INTEGRATION_FAILED");step=-1;mc.stop();}
 }
 private static void verifyEquipment(Minecraft mc)throws Exception{
  var slots=new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET};
  var before=java.util.Arrays.stream(slots).map(slot->mc.player.getItemBySlot(slot).copy()).toList();String active=Json.str(SkinClient.library.getAsJsonObject("profile"),"active");
  for(String mode:java.util.List.of("skin.preview.armor","skin.preview.elytra","skin.preview.current","skin.preview.none")){
   press(mc,Client.text("ui.actions_928503bd"));press(mc,Client.text("skin.preview.equipment"));press(mc,Client.text(mode));
   var field=SkinsScreen.class.getDeclaredField("previewPlayer");field.setAccessible(true);var preview=(net.minecraft.client.player.RemotePlayer)field.get(mc.screen);
   for(int n=0;n<slots.length;n++)if(!net.minecraft.world.item.ItemStack.matches(before.get(n),mc.player.getItemBySlot(slots[n])))throw new IllegalStateException("Preview changed player inventory");
   var chest=preview.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
   if(mode.equals("skin.preview.armor")&&!chest.is(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE)||mode.equals("skin.preview.elytra")&&!chest.is(net.minecraft.world.item.Items.ELYTRA)||mode.equals("skin.preview.none")&&!chest.isEmpty())throw new IllegalStateException("Wrong preview equipment: "+mode);
   if(mode.equals("skin.preview.current"))for(int n=0;n<slots.length;n++)if(!net.minecraft.world.item.ItemStack.matches(before.get(n),preview.getItemBySlot(slots[n])))throw new IllegalStateException("Current equipment preview mismatch");
   if(SkinClient.busy||!active.equals(Json.str(SkinClient.library.getAsJsonObject("profile"),"active")))throw new IllegalStateException("Preview changed active appearance");
  }
 }
 private static void press(Minecraft mc,String label){mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(label)&&b.active).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow(()->new IllegalStateException("Missing skin action: "+label)).onPress();}
 private static void checkSkinPaths(Minecraft mc,boolean custom)throws Exception{
  var profile=mc.player.getGameProfile();var expected=mc.player.getSkin();
  var manager=mc.getSkinManager();
  for(var actual:java.util.List.of(manager.getInsecureSkin(profile),retainedSkin.get(),manager.getOrLoad(profile).getNow(expected))){
   if(!actual.texture().equals(expected.texture())||actual.model()!=expected.model())throw new IllegalStateException("Profile skin path mismatch");
  }
  if(!custom&&!SkinClient.ordinarySkin().texture().equals(expected.texture()))throw new IllegalStateException("Ordinary thumbnail mismatch");
  var officialCape=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft","test-cape");var officialElytra=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft","test-elytra");
  var original=new net.minecraft.client.resources.PlayerSkin(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft","test-official"),null,officialCape,officialElytra,net.minecraft.client.resources.PlayerSkin.Model.SLIM,true);
  var decorated=SkinClient.skin(profile.getId(),original);if(!officialCape.equals(decorated.capeTexture())||!officialElytra.equals(decorated.elytraTexture()))throw new IllegalStateException("Rivet skin discarded official cape/elytra");
  if(!custom&&!decorated.equals(original))throw new IllegalStateException("Vanilla skin discarded");
 }
}
