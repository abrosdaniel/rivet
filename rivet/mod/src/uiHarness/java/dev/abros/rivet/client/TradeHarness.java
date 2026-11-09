package dev.abros.rivet.client;
import dev.abros.rivet.server.TradeMenu;
import dev.abros.rivet.network.TradeWire;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class TradeHarness {
 private static int step=-1;private static long deadline,next;private static boolean done;private static String id;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){String address=System.getenv("RIVET_TRADE_ADDRESS"),scenario=System.getenv("RIVET_TRADE_SCENARIO");if(address==null||done)return;var mc=Minecraft.getInstance();long now=System.currentTimeMillis();try{
  if(step<0){if(!(mc.screen instanceof TitleScreen))return;deadline=now+120000;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),new ServerData("Trades",address,ServerData.Type.OTHER),false,null);step=0;return;}
  if(!scenario.equals("review")&&now>deadline)throw new IllegalStateException("Timeout "+scenario+" step="+step);
  if(mc.player==null||!ServerMenuClient.available()||now<next)return;next=now+700;if(!scenario.equals("review")||step<8)mc.getToasts().clear();
  if(scenario.equals("hdReview")){HdAppearanceHarness.tick(mc,now);return;}
  if(scenario.equals("capeReview")){
   int index=step/5,phase=step%5;if(index>=3)return;
   if(phase==0){if(!SkinClient.available()||SkinClient.busy)return;mc.options.guiScale().set(3);mc.resizeDisplay();var png=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(System.getenv("RIVET_CAPE_FIXTURES"),index+".png"));id=dev.abros.rivet.core.Hashes.sha256(dev.abros.rivet.core.skins.CapeImage.normalize(png,1048576));SkinClient.upload("Cape test "+index,false,png,true);step++;}
   else if(phase==1){if(SkinClient.busy)return;var skin=mc.player.getSkin();if(skin.capeTexture()==null||!skin.capeTexture().getPath().endsWith(id))return;var expected=net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");check(index==2?!expected.equals(skin.elytraTexture()):expected.equals(skin.elytraTexture()),"live elytra texture selection "+index);var capes=SkinClient.library.getAsJsonObject("capes");if(capes==null)return;boolean matched=false;for(var entry:capes.getAsJsonArray("entries")){var row=entry.getAsJsonObject();if(dev.abros.rivet.core.Json.str(row,"id").equals(dev.abros.rivet.core.Json.str(capes,"active"))&&dev.abros.rivet.core.Json.str(row,"hash").equals(id))matched=true;}if(!matched)return;RivetHud.QUEUE.clear();var screen=new SkinsScreen(null,true);var query=SkinsScreen.class.getDeclaredField("query");query.setAccessible(true);query.set(screen,"Cape test "+index);mc.setScreen(screen);var equipment=SkinsScreen.class.getDeclaredField("previewEquipment");equipment.setAccessible(true);equipment.setInt(screen,3);var update=SkinsScreen.class.getDeclaredMethod("updateEquipment");update.setAccessible(true);update.invoke(screen);var preview=SkinsScreen.class.getDeclaredField("previewPlayer");preview.setAccessible(true);var player=(net.minecraft.client.player.RemotePlayer)preview.get(screen);check(java.util.Objects.equals(player.getSkin().elytraTexture(),skin.elytraTexture()),"wardrobe matches world elytra "+index);step++;}
   else if(phase==2){capture("cape-elytra-"+index);var equipment=SkinsScreen.class.getDeclaredField("previewEquipment");equipment.setAccessible(true);equipment.setInt(mc.screen,0);var update=SkinsScreen.class.getDeclaredMethod("updateEquipment");update.setAccessible(true);update.invoke(mc.screen);step++;}
   else if(phase==3){capture("cape-alpha-"+index);step++;}
   else{next=now+3500;System.out.println("RIVET_CAPE_ELYTRA_OK "+index);step++;if(index==2){System.out.println("RIVET_CAPE_REVIEW_READY");done=true;}}
   return;
  }
  if(scenario.equals("adminReview")){
   if(step==0){mc.options.guiScale().set(2);mc.resizeDisplay();command("rivet tradeadmin");step++;}
   else if(step>=1&&step<=3&&mc.screen instanceof TradeAdminScreen){check(mc.screen.children().stream().anyMatch(c->c instanceof TradeCard),"admin cards visible");capture("trade-admin-cards-"+(step+1));if(step==3){mc.screen.keyPressed(269,0,0);}else{mc.options.guiScale().set(step+2);mc.resizeDisplay();}step++;}
   else if(step==4&&mc.screen instanceof TradeAdminScreen admin){var field=TradeAdminScreen.class.getDeclaredField("data");field.setAccessible(true);var data=(com.google.gson.JsonObject)field.get(admin);if(data.get("page").getAsInt()==0)return;check(data.getAsJsonArray("rows").size()>12,"admin infinite list appends");mc.screen.keyPressed(268,0,0);mc.screen.children().stream().filter(c->c instanceof TradeCard).map(c->(TradeCard)c).findFirst().orElseThrow().onPress();step++;}
   else if(step>=5&&step<=7&&mc.screen instanceof TradeAdminScreen){var area=UiWorkspace.fit(mc.screen.width,mc.screen.height).page();for(var widget:mc.screen.children())if(widget instanceof net.minecraft.client.gui.components.EditBox field)check(field.getY()>=area.y()+112,"admin trays clear moderation controls");capture("trade-admin-trays-"+(9-step));if(step==7){System.out.println("RIVET_TRADE_ADMIN_REVIEW_READY");done=true;}else{mc.options.guiScale().set(8-step);mc.resizeDisplay();}step++;}
   return;
  }
  if(scenario.equals("review")){
   if(step<=24)RivetHud.QUEUE.clear();
   if(step==0){check(MenuSidebar.sections().contains("trades"),"Trade entry in menu");command("rivet trades");step++;}
   else if(step==1&&mc.screen instanceof TradeListScreen){mc.options.guiScale().set(2);mc.resizeDisplay();step++;}
   else if(step>=2&&step<=4&&mc.screen instanceof TradeListScreen){capture("review-list-scale-"+step);if(step==4)command("tradeFixture review");else{mc.options.guiScale().set(step+1);mc.resizeDisplay();}step++;}
   else if(step==5&&mc.screen instanceof TradeScreen screen){if(screen.rivetStyle())pressButton(7);check(menuHeight(mc)==234,"stable window size");hover(TradeMenu.STRIDE);step++;}
   else if(step==6&&mc.screen instanceof TradeScreen){check(mc.screen.children().size()==8,"trade controls with appearance toggle");capture("review-changes");mc.options.guiScale().set(2);mc.resizeDisplay();step++;}
   else if(step>=7&&step<=9&&mc.screen instanceof TradeScreen){check(mc.screen.height>=menuHeight(mc),"native trade fits viewport");capture("review-scale-"+(step-5));mc.options.guiScale().set(step==9?3:step-4);mc.resizeDisplay();step++;}
   else if(step==10&&mc.player.containerMenu instanceof TradeMenu m){click(m,0,0,ClickType.PICKUP);step++;}
   else if(step==11&&mc.player.containerMenu instanceof TradeMenu m){check(m.dirty,"review dirty state");capture("review-draft");click(m,0,0,ClickType.PICKUP);step++;}
   else if(step==12&&mc.player.containerMenu instanceof TradeMenu m){check(!m.dirty&&m.otherAccepted,"undo retains other consent");pressButton(1);step++;}
   else if(step==13&&mc.screen instanceof TradeScreen){check(button(0).getMessage().getString().equals(Client.text("trade.cancel.confirm")),"cancel confirmation is explicit");capture("review-cancel-confirm");pressButton(1);step++;}
   else if(step==14&&mc.player.containerMenu instanceof TradeMenu m){click(m,0,1,ClickType.PICKUP);step++;}
   else if(step==15&&mc.screen instanceof TradeScreen screen&&mc.player.containerMenu instanceof TradeMenu m){check(m.getCarried().getCount()==64,"style test cursor");if(!screen.rivetStyle())pressButton(7);check(mc.screen==screen&&mc.player.containerMenu==m&&m.getCarried().getCount()==64,"toggle preserves container and cursor");mc.options.guiScale().set(2);mc.resizeDisplay();step++;}
   else if(step>=16&&step<=18&&mc.screen instanceof TradeScreen screen&&mc.player.containerMenu instanceof TradeMenu m){check(screen.rivetStyle()&&m.getCarried().getCount()==64,"Rivet style survives resizing with carried stack");capture("review-rivet-scale-"+(step-14));mc.options.guiScale().set(step==18?3:step-13);mc.resizeDisplay();step++;}
   else if(step==19&&mc.screen instanceof TradeScreen screen){pressButton(7);check(!screen.rivetStyle(),"return to Minecraft style");capture("review-native-restored");pressButton(7);click((TradeMenu)mc.player.containerMenu,0,0,ClickType.PICKUP);step++;}
   else if(step==20&&mc.player.containerMenu instanceof TradeMenu m){check(m.getCarried().isEmpty()&&!m.dirty&&m.otherAccepted,"switching styles preserves offer and partner consent");capture("review-rivet-final");step++;}
   else if(step==21){UiPalette.preview(UiPalette.names().indexOf("Ivory"));step++;}
   else if(step==22){capture("review-rivet-light");UiPalette.preview(0);var field=AccessibilityScreen.class.getDeclaredField("contrast");field.setAccessible(true);field.setBoolean(null,true);step++;}
   else if(step==23){capture("review-rivet-contrast");var field=AccessibilityScreen.class.getDeclaredField("contrast");field.setAccessible(true);field.setBoolean(null,false);step++;}
   else if(step==24){command("rivet trades 1 0");step++;}
   else if(step==25&&mc.screen instanceof TradeListScreen){capture("review-trade-list-final");System.out.println("RIVET_TRADE_REVIEW_READY");step++;}
   return;
  }
  if(scenario.equals("admin")||scenario.equals("adminResume")||scenario.equals("adminCancel")){
   RivetHud.QUEUE.clear();
   if(step==0){command("rivet tradeadmin");step++;}
   else if(step==1&&mc.screen instanceof TradeAdminScreen){var row=mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().contains("RivetTradeA")).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow();row.onPress();step++;}
   else if(step==2&&mc.screen instanceof TradeAdminScreen admin){check(!(mc.player.containerMenu instanceof TradeMenu),"staff inspection has no inventory container");var field=TradeAdminScreen.class.getDeclaredField("data");field.setAccessible(true);var data=(com.google.gson.JsonObject)field.get(admin);check(data.get("mode").getAsString().equals("detail"),"staff detail");var input=admin.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.EditBox).map(c->(net.minecraft.client.gui.components.EditBox)c).findFirst().orElseThrow();input.setValue("Trade dispute review");String key=scenario.equals("admin")?"trade.admin.freeze":scenario.equals("adminResume")?"trade.admin.unfreeze":"trade.admin.cancel";admin.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(Client.text(key))).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow().onPress();step++;}
   else if(step==3&&mc.screen instanceof UiConfirmDialog){mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(Client.text("ui.confirm_0467ae4b"))).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow().onPress();step++;}
   else if(step==4&&mc.screen instanceof TradeAdminScreen admin){var field=TradeAdminScreen.class.getDeclaredField("data");field.setAccessible(true);var data=(com.google.gson.JsonObject)field.get(admin);check(data.get("frozen").getAsBoolean()==scenario.equals("admin"),"moderation persisted");if(scenario.equals("adminCancel"))check(!data.get("active").getAsBoolean(),"staff cancellation closes escrow");admin.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(Client.text("trade.admin.history"))).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow().onPress();step++;}
   else if(step==5&&mc.screen instanceof TradeAdminScreen admin){var field=TradeAdminScreen.class.getDeclaredField("data");field.setAccessible(true);var data=(com.google.gson.JsonObject)field.get(admin);if(!data.get("mode").getAsString().equals("history"))return;check(data.getAsJsonArray("rows").get(0).getAsJsonObject().get("reason").getAsString().equals("Trade dispute review"),"required reason in durable history");capture("trade-"+scenario+"-history");finish(scenario);}
   return;
  }
  if(scenario.equals("frozenOwner")){
   if(step==0){command("tradeFixture active");step++;}
   else if(step==1&&mc.player.containerMenu instanceof TradeMenu m){check(m.frozen&&m.getSlot(0).getItem().getCount()==128,"freeze survives server restart");click(m,0,0,ClickType.PICKUP);net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.abros.rivet.network.TradeWire.Action(m.containerId,m.revision,3));step++;}
   else if(step==2&&mc.player.containerMenu instanceof TradeMenu m){check(m.frozen&&m.getCarried().isEmpty()&&m.getSlot(0).getItem().getCount()==128&&!button(0).active,"frozen escrow rejects clicks and forged confirmation");capture("trade-frozen-owner");finish(scenario);}return;
  }
  if(scenario.equals("adminReturn")){
   if(step==0){command("tradeFixture seed");step++;}
   else if(step==1){command("tradeFixture open");step++;}
   else if(step==2&&mc.player.containerMenu instanceof TradeMenu m){check(m.status.equals("cancelled")&&m.getSlot(0).getItem().is(Items.DIAMOND)&&m.getSlot(0).getItem().getCount()==128,"staff returns original owner items");action(m,2);step++;}
   else if(step==3&&mc.player.containerMenu instanceof TradeMenu m){check(m.ownEmpty&&mc.player.getInventory().countItem(Items.DIAMOND)==384,"staff return collected exactly once");finish(scenario);}return;
  }
  if(scenario.equals("adminDenied")){
   if(step==0){command("tradeFixture adminDenied");command("rivet tradeadmin");step++;}
   else if(step==1){check(!ServerMenuClient.may("rivet.trade.view")&&!(mc.screen instanceof TradeAdminScreen),"ordinary player cannot inspect trades");finish(scenario);}return;
  }
  if(scenario.equals("adminReturnB")){
   if(step==0){command("tradeFixture seed");step++;}
   else if(step==1){command("tradeFixture open");step++;}
   else if(step==2&&mc.player.containerMenu instanceof TradeMenu m){check(m.status.equals("cancelled")&&m.getSlot(0).getItem().is(Items.EMERALD)&&m.getSlot(0).getItem().getCount()==64,"staff returns other owner items");action(m,2);step++;}
   else if(step==3&&mc.player.containerMenu instanceof TradeMenu m){check(m.ownEmpty&&mc.player.getInventory().countItem(Items.EMERALD)==320,"other owner collects exactly once");finish(scenario);}return;
  }
  if(scenario.equals("cards")){
   RivetHud.QUEUE.clear();
   if(step==0){command("tradeFixture cards");step++;}
   else if(step==1&&mc.screen instanceof TradeListScreen list){var field=TradeListScreen.class.getDeclaredField("rows");field.setAccessible(true);var rows=(com.google.gson.JsonArray)field.get(list);check(rows.size()==10,"bounded first page");check(rows.get(0).getAsJsonObject().getAsJsonArray("give").get(0).getAsJsonObject().get("count").getAsInt()==128,"newest offer first");check(rows.get(1).getAsJsonObject().get("receiveCount").getAsInt()==0,"unpublished counteroffer stays private");list.keyPressed(269,0,0);step++;}
   else if(step==2&&mc.screen instanceof TradeListScreen list){var field=TradeListScreen.class.getDeclaredField("rows");field.setAccessible(true);var rows=(com.google.gson.JsonArray)field.get(list);if(rows.size()<=10)return;check(rows.size()==java.util.stream.StreamSupport.stream(rows.spliterator(),false).map(r->r.getAsJsonObject().get("id").getAsString()).distinct().count(),"appended cards have no duplicates");list.keyPressed(268,0,0);mc.options.guiScale().set(2);mc.resizeDisplay();step++;}
   else if(step>=3&&step<=5&&mc.screen instanceof TradeListScreen list){check(list.children().stream().anyMatch(c->c instanceof TradeCard),"cards visible at every scale");capture("trade-cards-scale-"+(step-1));if(step==5){UiPalette.preview(UiPalette.names().indexOf("Ivory"));step++;}else{mc.options.guiScale().set(step);mc.resizeDisplay();step++;}}
   else if(step==6){capture("trade-cards-light");UiPalette.preview(0);finish(scenario);}
   return;
  }
  if(scenario.equals("notices")){
   if(step==0){check(MenuSidebar.sections().contains("trades"),"Trade menu entry");mc.setScreen(new NotificationPopup(null));step++;}
   else if(step==1&&mc.screen instanceof NotificationPopup){var row=mc.screen.children().stream().filter(c->c instanceof NotificationRow r&&r.getMessage().getString().contains("RivetTrade")).map(c->(NotificationRow)c).findFirst();if(row.isPresent()){capture("trade-notifications");row.get().onPress();step++;}}
   else if(step==2&&mc.screen instanceof TextScreen){var open=mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals(Client.text("ui.open_full_view_686862e2"))).map(c->(net.minecraft.client.gui.components.Button)c).findFirst();check(open.isPresent(),"notification offers persisted trade destination");open.get().onPress();step++;}
   else if(step==3&&mc.screen instanceof TradeScreen&&mc.player.containerMenu instanceof TradeMenu m){check(!m.dealId.isEmpty(),"notification opens real owned trade container");capture("trade-notification-open");finish(scenario);}
   return;
  }
  if(scenario.equals("seed")){if(step++==0)command("tradeFixture seed");else finish(scenario);return;}
  var menu=mc.player.containerMenu instanceof TradeMenu m?m:null;
  if(scenario.equals("offer")){
   if(step==0){command("tradeFixture seed");step++;}
   else if(step==1){command("rivet trade RivetTradeB");step++;}
   else if(menu!=null&&step>=2&&step<=5){click(menu,TradeMenu.HOTBAR+step-2,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==6){check(menu.getSlot(0).getItem().getCount()==256,"aggregate > stack");id=menu.dealId;click(menu,0,1,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==7){check(menu.getCarried().getCount()==64,"bounded cursor");mc.player.closeContainer();step++;}
   else if(step==8){command("rivet tradeopen "+id);step++;}
   else if(menu!=null&&step==9){check(menu.getCarried().getCount()==64,"cursor survives close");click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==10){check(menu.getSlot(0).getItem().getCount()==256,"cursor returned");action(menu,0);step++;}
   else if(menu!=null&&step==11&&menu.status.equals("open")){capture("offer");finish(scenario);}
  }else if(scenario.equals("counter")){
   if(step==0){command("tradeFixture open");step++;}
   else if(menu!=null&&step==1){check(menu.getSlot(TradeMenu.STRIDE).getItem().getCount()==256,"offline sender offer");click(menu,TradeMenu.HOTBAR,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==2){action(menu,0);step++;}
   else if(menu!=null&&step==3&&!menu.dirty){check(menu.accepted&&!menu.otherAccepted,"sending counter offer consents once");finish(scenario);}
  }else if(scenario.equals("edit")){
   if(step==0){command("tradeFixture open");step++;}
   else if(menu!=null&&step==1){check(menu.otherAccepted,"recipient consent");net.neoforged.neoforge.network.PacketDistributor.sendToServer(new TradeWire.Action(menu.containerId,menu.revision-1,0));step++;}
   else if(menu!=null&&step==2){check(!menu.accepted,"stale consent denied");click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==3){check(menu.otherAccepted&&menu.dirty,"draft preserves but suspends consent");click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==4){check(!menu.dirty&&menu.otherAccepted,"restoring items preserves consent");click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==5){check(menu.dirty,"new draft");action(menu,0);step++;}
   else if(menu!=null&&step==6){check(!menu.dirty&&!menu.otherAccepted,"published change clears consent");click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==7){check(menu.dirty,"restoring after publish needs new publish");action(menu,0);step++;}
   else if(menu!=null&&step==8){check(!menu.dirty&&!menu.otherAccepted&&menu.accepted,"new offer consents only its sender");finish(scenario);}
   else if(menu!=null&&step==9&&menu.accepted)finish(scenario);
  }else if(scenario.equals("draftLeave")){
   if(step==0){command("tradeFixture open");step++;}
   else if(menu!=null&&step==1){check(menu.otherAccepted,"draft begins with partner consent");click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==2){check(menu.dirty&&menu.getCarried().getCount()==64,"draft and cursor ready for disconnect");check(menu.changes.get(0).before().getCount()==256&&menu.changes.get(0).after().getCount()==192,"own draft comparison");finish(scenario);}
  }else if(scenario.equals("draftObserver")){
   if(step==0){command("tradeFixture open");step++;}
   else if(menu!=null&&step==1){check(menu.otherDirty&&menu.accepted,"offline draft suspends previous consent");check(menu.getSlot(TradeMenu.STRIDE).getItem().getCount()==256,"partner sees published items only");check(menu.changes.get(TradeMenu.STRIDE).after().getCount()==256,"comparison does not expose private draft");net.neoforged.neoforge.network.PacketDistributor.sendToServer(new TradeWire.Action(menu.containerId,menu.revision,0));step++;}
   else if(menu!=null&&step==2){check(menu.status.equals("open")&&menu.accepted,"server rejects completion with dirty participant");click(menu,TradeMenu.HOTBAR+1,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==3){check(menu.dirty&&menu.otherDirty,"both participants have drafts");click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==4){check(!menu.dirty&&menu.otherDirty,"independent draft restoration");click(menu,TradeMenu.HOTBAR+1,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==5){check(menu.getCarried().isEmpty(),"second cursor restored");finish(scenario);}
  }else if(scenario.equals("draftReturn")){
   if(step==0){command("tradeFixture open");step++;}
   else if(menu!=null&&step==1){check(menu.dirty&&menu.otherAccepted,"draft and consent survived restart");check(menu.getCarried().getCount()==64&&menu.getSlot(0).getItem().getCount()==192,"draft escrow and cursor exact after restart");click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==2){check(!menu.dirty&&menu.otherAccepted&&menu.getSlot(0).getItem().getCount()==256,"restored draft needs no partner reconfirmation");capture("draft-restored");finish(scenario);}
  }else if(scenario.equals("complete")){
   if(step==0){command("tradeFixture open");step++;}
   else if(menu!=null&&step==1){check(menu.otherAccepted&&!menu.accepted,"offline revised consent");action(menu,0);step++;}
   else if(menu!=null&&step==2&&menu.status.equals("completed")){check(menu.getSlot(0).getItem().getCount()==256,"ownership transferred");check(!menu.getSlot(TradeMenu.STRIDE).isActive()&&menu.getSlot(0).x==74,"result has one centered tray");capture("completed-result");finish(scenario);}
  }else if(scenario.equals("claimA")){
   if(step==0){command("tradeFixture open");step++;}
   else if(menu!=null&&step==1){check(menu.status.equals("completed")&&menu.getSlot(0).getItem().is(Items.EMERALD),"completed persists restart");action(menu,2);step++;}
   else if(menu!=null&&step==2){check(menu.getSlot(0).getItem().isEmpty(),"claim empties escrow");check(mc.player.getInventory().countItem(Items.EMERALD)==64,"received exactly once");check(button(0).active&&button(0).getMessage().getString().equals(Client.text("trade.done")),"empty result offers Done");action(menu,2);check(!(mc.screen instanceof TradeScreen),"Done closes result");finish(scenario);}
  }else if(scenario.equals("claimB")){
   if(step==0){command("tradeFixture full");step++;}
   else if(step==1){command("tradeFixture open");step++;}
   else if(menu!=null&&step==2){action(menu,2);step++;}
   else if(menu!=null&&step==3){check(menu.getSlot(0).getItem().getCount()==192,"full inventory retains remainder");check(mc.player.getInventory().countItem(Items.DIAMOND)==64,"partial claim exact");capture("claim");finish(scenario);}
  }else if(scenario.equals("resume")){
   if(step==0){command("tradeFixture completed");step++;}
   else if(menu!=null&&step==1){check(menu.status.equals("completed"),"completed after abrupt stop");check(menu.getSlot(0).getItem().getCount()==192,"escrow after abrupt stop");check(mc.player.getInventory().countItem(Items.DIAMOND)==64,"receipt inventory after abrupt stop");finish(scenario);}
  }else if(scenario.equals("components")){
   if(step==0){command("tradeFixture components");step++;}
   else if(step==1){command("rivet trade RivetTradeB");step++;}
   else if(menu!=null&&step==2){click(menu,TradeMenu.HOTBAR,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==3){var box=menu.getSlot(0).getItem();check(box.is(Items.SHULKER_BOX)&&box.getHoverName().getString().equals("Trade preservation"),"named component escrow");check(box.get(net.minecraft.core.component.DataComponents.CONTAINER).stream().mapToInt(s->s.is(Items.DIAMOND)?s.getCount():0).sum()==37,"container contents escrow");action(menu,1);step++;}
   else if(menu!=null&&step==4){click(menu,0,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==5){var box=mc.player.getInventory().items.stream().filter(s->s.is(Items.SHULKER_BOX)).findFirst().orElseThrow();check(box.getHoverName().getString().equals("Trade preservation"),"named component return");check(box.get(net.minecraft.core.component.DataComponents.CONTAINER).stream().mapToInt(s->s.is(Items.DIAMOND)?s.getCount():0).sum()==37,"container contents return");finish(scenario);}
  }else if(scenario.equals("gift")){
   RivetHud.QUEUE.clear();
   if(step==0){command("tradeFixture seed");step++;}
   else if(step==1){command("rivet trade RivetTradeB");step++;}
   else if(menu!=null&&step==2){click(menu,TradeMenu.HOTBAR,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==3){pressButton(0);check(button(0).getMessage().getString().equals(Client.text("trade.free.confirm")),"explicit free-transfer confirmation");mc.options.guiScale().set(2);mc.resizeDisplay();step=40;}
   else if(menu!=null&&step>=40&&step<=42){capture("free-transfer-warning-scale-"+(step-38));if(step==42){mc.options.guiScale().set(3);mc.resizeDisplay();step=4;}else{mc.options.guiScale().set(step-37);mc.resizeDisplay();step++;}}
   else if(menu!=null&&step==4){check(menu.status.equals("draft")&&!menu.accepted,"first click never sends or transfers items");pressButton(1);check(button(0).getMessage().getString().equals(Client.text("trade.send")),"back keeps draft");pressButton(0);click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==5){check(!button(0).getMessage().getString().equals(Client.text("trade.free.confirm")),"editing resets free consent");click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==6){check(menu.getCarried().isEmpty()&&menu.getSlot(0).getItem().getCount()==64,"warning and edits preserve escrow");action(menu,0);step++;}
   else if(menu!=null&&step==7&&menu.status.equals("open")){check(menu.accepted,"confirmed unilateral send records consent");finish(scenario);}
  }else if(scenario.equals("giftAccept")){
   if(step==0){command("tradeFixture seed");step++;}
   else if(step==1){command("tradeFixture open");step++;}
   else if(menu!=null&&step==2){check(menu.ownEmpty,"unified offer recipient empty tray");action(menu,0);step++;}
   else if(menu!=null&&step==3&&menu.status.equals("completed")){action(menu,2);step++;}
   else if(menu!=null&&step==4){check(mc.player.getInventory().countItem(Items.DIAMOND)==64&&menu.ownEmpty,"gift collect via result");finish(scenario);}
  }else if(scenario.equals("fullResult")){
   if(step==0){command("tradeFixture packed");step++;}
   else if(step==1){command("tradeFixture completed");step++;}
   else if(menu!=null&&step==2){check(!menu.canCollect&&!menu.ownEmpty,"full result retains escrow");var button=(net.minecraft.client.gui.components.Button)mc.screen.children().get(0);check(!button.active,"collect disabled when full");capture("full-result");finish(scenario);}
  }else if(scenario.equals("pages")){
   if(step==0){command("tradeFixture pages");step++;}
   else if(step==1){command("rivet trade RivetTradeB");step++;}
   else if(menu!=null&&step>=2&&step<20){int n=step-2;click(menu,n<9?TradeMenu.HOTBAR+n:TradeMenu.INVENTORY+n-9,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==20){check(menu.ownPages==2,"full tray offers next page");click(menu,TradeMenu.INVENTORY+9,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==21){check(menu.ownPage==0&&mc.player.getInventory().countItem(Items.DIAMOND)==0,"shift inserts beyond full visible page");pressButton(4);step++;}
   else if(menu!=null&&step==22){check(menu.getSlot(0).getItem().getHoverName().getString().equals("Entry 18"),"nineteenth item on second page");capture("pages");pressButton(3);step++;}
   else if(menu!=null&&step==23){check(menu.ownPage==0,"previous page button works");id=menu.dealId;pressButton(2);step++;}
   else if(step==24){check(!(mc.screen instanceof TradeScreen),"cross closes without cancelling");command("rivet tradeopen "+id);step++;}
   else if(menu!=null&&step==25){check(menu.status.equals("draft")&&menu.ownPages==2,"cross preserves draft and all pages");action(menu,1);step++;}
   else if(menu!=null&&step==26){check(menu.frameHeight()==234&&menu.getSlot(TradeMenu.INVENTORY).y==152,"result keeps inventory geometry");action(menu,2);step++;}
   else if(menu!=null&&step==27){check(menu.ownEmpty&&mc.player.getInventory().countItem(Items.DIAMOND)==19,"all pages collected exactly once");check(mc.player.getInventory().items.stream().filter(t->t.is(Items.DIAMOND)).map(t->t.getHoverName().getString()).distinct().count()==19,"all named items preserved");capture("all-collected");finish(scenario);}
  }else if(scenario.equals("pageControls")){
   if(step==0){check(TradeScreen.compactCount(999).equals("999")&&TradeScreen.compactCount(1000).equals("1k")&&TradeScreen.compactCount(1250).equals("1.2k")&&TradeScreen.compactCount(999999).equals("999k")&&TradeScreen.compactCount(1000000).equals("1M")&&TradeScreen.compactCount(Integer.MAX_VALUE).equals("2.1B"),"compact count boundaries");command("tradeFixture pageControls");step++;}
   else if(step==1){command("rivet trade RivetTradeB");step++;}
   else if(menu!=null&&step>=2&&step<=20){int n=step-2;click(menu,n<9?TradeMenu.HOTBAR+n:TradeMenu.INVENTORY+n-9,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==21){check(menu.ownPage==0&&menu.ownPages==2,"overflow does not change viewport");click(menu,TradeMenu.INVENTORY+11,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==22){pressButton(4);step++;}
   else if(menu!=null&&step==23){check(menu.getSlot(0).getItem().getCount()==3,"shift merges into hidden page");click(menu,TradeMenu.INVENTORY+10,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==24){System.out.println("TRADE_PAGE_DIAGNOSTIC ownPage="+menu.ownPage+" ownPages="+menu.ownPages+" slot0="+menu.getSlot(0).getItem());capture("page-before-previous");pressButton(3);step++;}
   else if(menu!=null&&step==25){check(menu.getSlot(0).getItem().getCount()==33,"shift merges into previous page");click(menu,0,1,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==26){pressButton(4);step++;}
   else if(menu!=null&&step==27){click(menu,1,0,ClickType.PICKUP_ALL);step++;}
   else if(menu!=null&&step==28){check(menu.getCarried().getCount()==35,"double click gathers hidden page and inventory");click(menu,1,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==29){action(menu,0);step++;}
   else if(menu!=null&&step==30){check(menu.status.equals("open"),"published multipage tray");click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==31){check(menu.changes.values().stream().anyMatch(c->c.before().getCount()==35&&c.after().isEmpty()),"removed entry retains comparison");capture("removed-entry");click(menu,TradeMenu.HOTBAR,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==32){action(menu,1);step++;}
   else if(menu!=null&&step==33){action(menu,2);step++;}
   else if(menu!=null&&step==34){check(menu.ownEmpty&&mc.player.getInventory().countItem(Items.DIAMOND)==55,"all page operations conserve items");check(mc.player.getInventory().items.stream().filter(t->t.is(Items.DIAMOND)).map(t->t.getHoverName().getString()).distinct().count()==19,"components survive all page operations");finish(scenario);}
  }else if(scenario.equals("counts")){
   if(step==0){command("tradeFixture large");step++;}
   else if(step==1){command("rivet trade RivetTradeB");step++;}
   else if(menu!=null&&step==2){click(menu,TradeMenu.HOTBAR,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==3){action(menu,0);step++;}
   else if(menu!=null&&step>=4&&step<=38){int n=step-3;click(menu,n<9?TradeMenu.HOTBAR+n:TradeMenu.INVENTORY+n-9,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==39){check(menu.getSlot(0).getItem().getCount()==2304,"large aggregate exact");check(menu.changes.get(0).before().getCount()==64&&menu.changes.get(0).after().getCount()==2304,"large comparison exact");capture("large-count");hover(0);step++;}
   else if(menu!=null&&step==40){capture("large-count-tooltip");action(menu,1);step++;}
   else if(menu!=null&&step==41){action(menu,2);step++;}
   else if(menu!=null&&step==42){check(menu.ownEmpty&&mc.player.getInventory().countItem(Items.DIAMOND)==2304,"large aggregate returned as native stacks");finish(scenario);}
  }else if((scenario.equals("cursorResult")||scenario.equals("cursorDisconnect"))){
   if(step==0){command("tradeFixture seed");step++;}
   else if(step==1){command("rivet trade RivetTradeB");step++;}
   else if(menu!=null&&step==2){click(menu,TradeMenu.HOTBAR,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==3){id=menu.dealId;action(menu,1);step++;}
   else if(menu!=null&&step==4){command("tradeFixture packed");step++;}
   else if(menu!=null&&step==5){click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==6){check(menu.ownEmpty&&menu.getCarried().getCount()==64&&!menu.fullyCollected(),"carried result is not collected");check(!button(0).getMessage().getString().equals(Client.text("trade.done")),"Done not offered with carried item");if(scenario.equals("cursorDisconnect")){finish(scenario);return;}mc.player.closeContainer();step++;}
   else if(step==7){command("rivet tradeopen "+id);step++;}
   else if(menu!=null&&step==8){check(menu.getCarried().isEmpty()&&menu.getSlot(0).getItem().getCount()==64,"full inventory close restores collection tray");click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==9){click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==10){check(menu.getCarried().isEmpty()&&menu.getSlot(0).getItem().getCount()==64,"result cursor can be returned to tray");click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==11){command("tradeFixture space");step++;}
   else if(menu!=null&&step==12){check(mc.player.getInventory().getItem(8).isEmpty(),"space ready before close");mc.player.closeContainer();step++;}
   else if(step==13){command("rivet tradeopen "+id);step++;}
   else if(menu!=null&&step==14){check(menu.fullyCollected()&&mc.player.getInventory().countItem(Items.DIAMOND)==64,"closing delivers cursor when space exists");finish(scenario);}
  }else if(scenario.equals("cursorReconnect")){
   if(step==0){command("tradeFixture open");step++;}
   else if(menu!=null&&step==1){check(menu.getCarried().isEmpty()&&menu.getSlot(0).getItem().getCount()==64&&!menu.fullyCollected(),"result cursor remains accessible after reconnect and server restart");command("tradeFixture space");step++;}
   else if(menu!=null&&step==2){action(menu,2);step++;}
   else if(menu!=null&&step==3){check(menu.fullyCollected()&&mc.player.getInventory().countItem(Items.DIAMOND)==64,"recovered result can be collected once");finish(scenario);}
  }else if(scenario.equals("drops")){
   if(step==0){command("tradeFixture seed");command("tradeFixture dropClear");step++;}
   else if(step==1){command("rivet trade RivetTradeB");step++;}
   else if(menu!=null&&step>=2&&step<=5){click(menu,TradeMenu.HOTBAR+step-2,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==6){command("tradeFixture tossDeny");step++;}
   else if(menu!=null&&step==7){click(menu,0,0,ClickType.THROW);step++;}
   else if(menu!=null&&step==8){check(menu.getSlot(0).getItem().getCount()==256&&mc.player.getInventory().countItem(Items.DIAMOND)==0,"cancelled toss and mod refund cannot duplicate");command("tradeFixture joinDeny");step++;}
   else if(menu!=null&&step==9){click(menu,0,1,ClickType.THROW);step++;}
   else if(menu!=null&&step==10){check(menu.getSlot(0).getItem().getCount()==256,"cancelled entity spawn retains escrow");command("tradeFixture dropAllow");hover(0);step++;}
   else if(menu!=null&&step==11){check(mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_Q,0,0),"native Q handled");step++;}
   else if(menu!=null&&step==12){click(menu,0,1,ClickType.THROW);step++;}
   else if(menu!=null&&step==13){check(menu.getSlot(0).getItem().getCount()==191,"Q and Ctrl Q remove one and native stack");click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==14){click(menu,-999,1,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==15){check(menu.getCarried().getCount()==63,"right outside drops one");mc.screen.mouseClicked((mc.screen.width-256)/2+20,(mc.screen.height-234)/2+160,0);mc.screen.mouseReleased((mc.screen.width-256)/2+20,(mc.screen.height-234)/2+160,0);step++;}
   else if(menu!=null&&step==16){check(menu.getCarried().isEmpty()&&menu.getSlot(0).getItem().getCount()==127,"left outside drops cursor");int count=0;for(var entity:mc.level.entitiesForRendering())if(entity instanceof net.minecraft.world.entity.item.ItemEntity item&&item.getItem().is(Items.DIAMOND))count+=item.getItem().getCount();check(count==129,"world receives exact dropped quantity");action(menu,1);step++;}
   else if(menu!=null&&step==17){action(menu,2);step++;}
   else if(menu!=null&&step==18){check(menu.fullyCollected()&&mc.player.getInventory().countItem(Items.DIAMOND)==127,"remaining escrow returned exactly once");finish(scenario);}
  }else if(scenario.equals("rapid")){
   if(step==0){command("tradeFixture seed");step++;}
   else if(step==1){command("rivet trade RivetTradeB");step++;}
   else if(menu!=null&&step==2){for(int i=0;i<12;i++)click(menu,TradeMenu.HOTBAR,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==3){check(menu.getSlot(0).getItem().getCount()==64&&mc.player.getInventory().countItem(Items.DIAMOND)==192,"burst clicks transfer once");click(menu,TradeMenu.STRIDE,0,ClickType.PICKUP);click(menu,TradeMenu.PAGE,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==4){check(menu.getCarried().isEmpty()&&menu.getSlot(0).getItem().getCount()==64,"foreign and hidden slots reject clicks");click(menu,0,0,ClickType.PICKUP);step++;}
   else if(menu!=null&&step==5){check(menu.getCarried().getCount()==64,"cursor for drag");click(menu,-999,0,ClickType.QUICK_CRAFT);click(menu,0,1,ClickType.QUICK_CRAFT);click(menu,1,1,ClickType.QUICK_CRAFT);click(menu,-999,2,ClickType.QUICK_CRAFT);step++;}
   else if(menu!=null&&step==6){check(menu.getCarried().isEmpty()&&menu.getSlot(0).getItem().getCount()==64,"native drag merges identical items without loss");action(menu,1);step++;}
   else if(menu!=null&&step==7){action(menu,2);step++;}
   else if(menu!=null&&step==8){check(menu.ownEmpty&&mc.player.getInventory().countItem(Items.DIAMOND)==256,"burst and drag cancel returns exact inventory");finish(scenario);}
  }else if(scenario.equals("cancel")){
   if(step==0){command("tradeFixture seed");step++;}
   else if(step==1){command("rivet trade RivetTradeB");step++;}
   else if(menu!=null&&step==2){click(menu,TradeMenu.HOTBAR,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==3){action(menu,1);step++;}
   else if(menu!=null&&step==4){check(menu.status.equals("cancelled"),"cancel state");check(!menu.getSlot(TradeMenu.STRIDE).isActive(),"cancel result hides other tray");capture("cancel-result");click(menu,0,0,ClickType.QUICK_MOVE);step++;}
   else if(menu!=null&&step==5){check(mc.player.getInventory().countItem(Items.DIAMOND)==256,"cancel conserves all items");finish(scenario);}
  }
 }catch(Throwable failure){System.out.println("RIVET_TRADE_FAILED step="+step);failure.printStackTrace();done=true;mc.stop();}}
 private static net.minecraft.client.gui.components.Button button(int index){return (net.minecraft.client.gui.components.Button)Minecraft.getInstance().screen.children().get(index);}
 private static void pressButton(int index){var b=button(index);check(b.active,"button enabled "+index);Minecraft.getInstance().screen.mouseClicked(b.getX()+3,b.getY()+3,0);}
 private static void command(String s){Minecraft.getInstance().getConnection().sendCommand(s);}
 private static void click(TradeMenu menu,int slot,int button,ClickType type){var mc=Minecraft.getInstance();mc.gameMode.handleInventoryMouseClick(menu.containerId,slot,button,type,mc.player);}
 private static void action(TradeMenu menu,int action){var mc=Minecraft.getInstance();check(mc.screen instanceof TradeScreen,"trade screen active");var buttons=mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button).map(w->(net.minecraft.client.gui.components.Button)w).toList();var button=buttons.get(action==1?1:0);check(button.active,"action enabled "+action);mc.screen.mouseClicked(button.getX()+4,button.getY()+4,0);if(action==0&&!menu.ownEmpty&&menu.otherEmpty){check(button.getMessage().getString().equals(Client.text("trade.free.confirm")),"free transfer requires explicit warning confirmation");mc.screen.mouseClicked(button.getX()+4,button.getY()+4,0);}if(action==1){var confirm=buttons.get(0);check(confirm.getMessage().getString().equals(Client.text("trade.cancel.confirm")),"cancel requires confirmation");mc.screen.mouseClicked(confirm.getX()+4,confirm.getY()+4,0);}}
 private static int menuHeight(Minecraft mc){return ((TradeMenu)mc.player.containerMenu).frameHeight();}
 private static void check(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 private static void hover(int index){var mc=Minecraft.getInstance();var slot=((TradeMenu)mc.player.containerMenu).getSlot(index);double x=((mc.screen.width-256)/2+slot.x+8)*(double)mc.getWindow().getScreenWidth()/mc.screen.width,y=((mc.screen.height-234)/2+slot.y+8)*(double)mc.getWindow().getScreenHeight()/mc.screen.height;try{var move=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);move.setAccessible(true);move.invoke(mc.mouseHandler,mc.getWindow().getWindow(),x,y);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
 private static void capture(String name){var mc=Minecraft.getInstance();var dir=new java.io.File("trade-captures");dir.mkdirs();UiCaptureHarness.grab(dir,name+".png",mc.getMainRenderTarget(),m->{});}
 private static void finish(String scenario){System.out.println("RIVET_TRADE_OK "+scenario);done=true;Minecraft.getInstance().stop();}
}
