package dev.abros.rivet.client;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Opt-in camera, cursor and theme regression review. Not included in released JARs. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class RivetUiFixesHarness {
 private static boolean connecting;private static int stage,frame,theme;private static long next;private static InputConstants.Key oldKey;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(System.getenv("RIVET_UI_FIXES")==null)return;var mc=Minecraft.getInstance();long now=System.currentTimeMillis();
  if(!connecting&&mc.screen instanceof TitleScreen){connecting=true;mc.options.pauseOnLostFocus=false;var data=new net.minecraft.client.multiplayer.ServerData("Rivet test","127.0.0.1:25569",net.minecraft.client.multiplayer.ServerData.Type.OTHER);ConnectScreen.startConnecting(mc.screen,mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(data.ip),data,false,null);}
  if(mc.player==null||!SocialClient.available()||SocialClient.players.isEmpty())return;
  if(stage==0){theme=UiPalette.selected();stage=System.getenv("RIVET_HOLOGRAM_ONLY")==null?1:5;next=now+2000;mc.player.connection.sendCommand("time set day");}
  if(now<next)return;
  if(stage==1){if(frame>=UiPalette.names().size()){UiPalette.preview(theme);frame=0;stage=3;return;}UiPalette.preview(frame);mc.setScreen(new NavigationSettingsScreen(null));stage=2;next=now+250;return;}
  if(stage==2){UiGeometryHarness.verify(mc.screen);capture("theme-"+UiPalette.id()+".png");check(contrast(UiKit.text(),UiKit.surface())>=4.5,"Low theme contrast: "+UiPalette.id());frame++;stage=1;next=now+100;return;}
  if(stage==3){if(frame>=4){frame=0;stage=5;return;}mc.options.guiScale().set(frame+1);mc.resizeDisplay();mc.setScreen(new ChatScreen(""));stage=4;next=now+300;return;}
  if(stage==4){capture("chat-lines-gui-"+(frame+1)+".png");mc.setScreen(new ServerMenuScreen(null,"admin"));stage=41;next=now+900;return;}
  if(stage==41){UiGeometryHarness.verify(mc.screen);mc.screen.mouseScrolled(mc.screen.width-30,100,0,-3);stage=42;next=now+250;return;}
  if(stage==42){capture("admin-gui-"+(frame+1)+".png");frame++;stage=3;next=now+100;return;}
  if(stage==5){mc.options.guiScale().set(3);mc.resizeDisplay();mc.setScreen(null);oldKey=RivetHud.INTERACT.getKey();RivetHud.INTERACT.setKey(InputConstants.Type.KEYSYM.getOrCreate(80));net.minecraft.client.KeyMapping.resetMapping();mc.options.keyPlayerList.setDown(true);net.minecraft.client.KeyMapping.click(RivetHud.INTERACT.getKey());RivetHud.tick();check(mc.screen instanceof HudInteractionScreen c&&c.tabVisible(),"Cursor did not capture open TAB");mc.options.keyPlayerList.setDown(false);stage=6;next=now+400;return;}
  if(stage==6){check(!RivetTab.hits.isEmpty(),"Cursor TAB has no targets");capture("cursor-tab-and-widget.png");mc.screen.keyPressed(80,0,0);check(mc.screen==null,"Same key did not exit cursor");net.minecraft.client.KeyMapping.click(RivetHud.INTERACT.getKey());RivetHud.tick();check(mc.screen instanceof HudInteractionScreen c&&!c.tabVisible(),"Cursor incorrectly opens TAB without holding TAB");mc.screen.keyPressed(80,0,0);RivetHud.INTERACT.setKey(oldKey);net.minecraft.client.KeyMapping.resetMapping();System.out.println("RIVET_UNIFIED_CURSOR_OK TAB + HUD + toggle");mc.player.connection.sendCommand("setblock 0 -60 0 minecraft:chest");mc.player.connection.sendCommand("tp @s 0.5 -60 -5 0 5");stage=7;frame=0;next=now+1000;return;}
  if(stage==7){StockHologramRenderer.receive(snapshot());capture("hologram-view-"+frame+".png");if(frame==0)mc.player.connection.sendCommand("tp @s 5 -60 0.5 90 5");else if(frame==1)mc.player.connection.sendCommand("tp @s 0.5 -60 5 180 5");else if(frame==2)mc.player.connection.sendCommand("tp @s -5 -60 0.5 -90 5");else{StockHologramRenderer.clear();mc.setScreen(null);System.out.println("RIVET_UI_FIXES_OK 20 themes + GUI 1-4 + hologram 4 camera angles");stage=8;return;}frame++;next=now+700;return;}
 }
 @SubscribeEvent public static void render(net.neoforged.neoforge.client.event.RenderLevelStageEvent event){if(System.getenv("RIVET_UI_FIXES")!=null&&stage==7)StockHologramRenderer.receive(snapshot());}
 private static JsonObject snapshot(){var row=new JsonObject();row.addProperty("dimension","minecraft:overworld");row.addProperty("x",.5);row.addProperty("y",-58.5);row.addProperty("z",.5);row.addProperty("title","Склад · Проверка закрепления");row.addProperty("remainingTypes",0);var res=new JsonObject();res.addProperty("item","minecraft:stone");res.addProperty("have",32);res.addProperty("amount",64);res.addProperty("missing",32);var resources=new JsonArray();resources.add(res);row.add("resources",resources);var panels=new JsonArray();panels.add(row);var value=new JsonObject();value.add("holograms",panels);return value;}
 private static java.io.File output(){var output=new java.io.File("/private/tmp/rivet-five-preview");output.mkdirs();return output;}
 private static void capture(String name){var mc=Minecraft.getInstance();UiCaptureHarness.grab(output(),name,mc.getMainRenderTarget(),msg->{});}
 private static double luminance(int color){double value=0;double[] weights={.0722,.7152,.2126};for(int n=0;n<3;n++){double c=((color>>(n*8))&255)/255d;value+=weights[n]*(c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4));}return value;}
 private static double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
 private static void check(boolean ok,String text){if(!ok)throw new IllegalStateException(text);}
}
