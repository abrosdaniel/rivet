package dev.abros.rivet.client;

import dev.abros.rivet.core.Json;
import dev.abros.rivet.core.Hashes;
import dev.abros.rivet.core.map.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.nio.file.*;
import java.util.*;

/** Opt-in local-server skin transport → PlayerInfo → both radar render paths; reconnect/restart persistence. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapIntegrationHarness {
 private static final String MODE=System.getenv("RIVET_MAP_INTEGRATION");
 private static int stage,variant,frames,checks;private static long deadline,next;private static String originalSkin,hash;private static final List<String> created=new ArrayList<>();private static RemotePlayer fixture;private static boolean done;
 private static Path settings(){return Minecraft.getInstance().gameDirectory.toPath().resolve("rivet/map-settings.json");}
 private static Path backup(){return settings().resolveSibling("map-integration-backup.json");}
 private static Path recovery(){return settings().resolveSibling("map-integration-recovery.json");}
 private static void saveRecovery()throws Exception{var j=new com.google.gson.JsonObject();j.addProperty("originalSkin",originalSkin);j.add("created",Json.GSON.toJsonTree(created));Json.write(recovery(),j);}
 private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);checks++;}
 private static void connect(){var mc=Minecraft.getInstance();ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25598"),new ServerData("Map integration","127.0.0.1:25598",ServerData.Type.OTHER),false,null);}
 private static String dim(){return Minecraft.getInstance().level.dimension().location().toString();}
 private static void upload()throws Exception{var image=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<32;y++)for(int x=0;x<64;x++)image.setRGB(x,y,0xff888888);for(int y=8;y<16;y++)for(int x=8;x<16;x++)image.setRGB(x,y,variant==0?0xffff2244:0xff22ccff);for(int y=8;y<16;y++)for(int x=32;x<64;x++)image.setRGB(x,y,0);for(int y=8;y<12;y++)for(int x=40;x<44;x++)image.setRGB(x,y,0xff55ee33);var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"PNG",out);hash=Hashes.sha256(dev.abros.rivet.core.skins.SkinImage.normalize(out.toByteArray(),1024*1024));SkinClient.upload("Map integration "+variant,variant==1,out.toByteArray());}
 private static void verifySettings(){var s=MapSettings.INSTANCE;check(s.size==144&&s.zoom==1.25f&&!s.rotate&&s.opacity==.7f,"Minimap preferences lost");check(s.hud.mapOffsetX==21&&s.hud.directionOffsetY==37&&!s.hud.directionCoordinates,"Map HUD preferences lost");check(MapRenderSettings.INSTANCE.slopes==3&&!MapRenderSettings.INSTANCE.depth,"Map display preferences lost");check(MapCaves.options().equals(new MapCaves.Options(3,1,1800,700,3,false,2)),"Cave options lost");check(MapCaves.view(dim()).equals(new MapCaves.View(2,false,24,42,true)),"World cave view lost");mcScreen();check(MapCaves.top(dim())==24,"Manual layer lost after reconnect");}
 private static void mcScreen(){Minecraft.getInstance().setScreen(new WorldMapScreen(null));}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){if(MODE==null||done)return;var mc=Minecraft.getInstance();long now=System.currentTimeMillis();try{
  if(deadline==0)deadline=now+150000;if(now>deadline)throw new IllegalStateException("Integration timeout stage="+stage+" skin="+SkinClient.status);if(now<next)return;
  if(stage==0){if(!(mc.screen instanceof TitleScreen))return;mc.options.guiScale().set(2);mc.options.pauseOnLostFocus=false;mc.resizeDisplay();connect();stage=1;return;}
  if(stage==10){connect();stage=11;return;}
  if(!WorldMapClient.ready()||!WorldMapClient.markersReady()||!SkinClient.available())return;
  if(stage==20){if(SkinClient.busy)return;check(SkinClient.status.isEmpty(),"Skin recovery failed");if(!created.isEmpty()){SkinClient.command("delete",created.remove(0),false);return;}Files.copy(backup(),settings(),StandardCopyOption.REPLACE_EXISTING);Files.delete(backup());Files.deleteIfExists(recovery());System.out.println("RIVET_MAP_TEST_RECOVERY_OK");done=true;mc.stop();return;}
  if(stage==1){if(MODE.equals("recover")){if(!SkinClient.library.has("profile")||SkinClient.busy)return;var saved=Json.read(recovery());originalSkin=Json.str(saved,"originalSkin");var ids=new HashSet<String>();for(var e:saved.getAsJsonArray("created"))ids.add(e.getAsString());for(var e:SkinClient.library.getAsJsonArray("entries")){var id=Json.str(e.getAsJsonObject(),"id");if(ids.contains(id))created.add(id);}SkinClient.command("select",originalSkin,false);stage=20;return;}if(MODE.equals("verify")){verifySettings();Files.copy(backup(),settings(),StandardCopyOption.REPLACE_EXISTING);Files.delete(backup());Files.deleteIfExists(recovery());System.out.println("RIVET_MAP_RESTART_OK checks="+checks);done=true;mc.stop();return;}if(!SkinClient.library.has("profile")||SkinClient.busy)return;check(!Files.exists(backup()),"Previous test backup needs recovery");Files.copy(settings(),backup());originalSkin=Json.opt(SkinClient.library.getAsJsonObject("profile"),"active","");saveRecovery();fixture=new RemotePlayer(mc.level,mc.player.getGameProfile());fixture.setPos(mc.player.position());fixture.xo=fixture.getX();fixture.yo=fixture.getY();fixture.zo=fixture.getZ();upload();stage=2;}
  else if(stage==2){if(SkinClient.busy)return;check(SkinClient.status.isEmpty(),"Skin upload failed: "+SkinClient.status);String active=Json.opt(SkinClient.library.getAsJsonObject("profile"),"active","");if(!active.isEmpty()&&!active.equals(originalSkin)&&!created.contains(active)){created.add(active);saveRecovery();}if(!fixture.getSkin().texture().getPath().equals("skins/"+hash))return;check(fixture.getSkin().model()==(variant==0?net.minecraft.client.resources.PlayerSkin.Model.WIDE:net.minecraft.client.resources.PlayerSkin.Model.SLIM),"Wrong skin model");frames=0;mc.setScreen(new RadarSheet());stage=3;next=now+4000;}
  else if(stage==3){if(frames<8||mc.getOverlay()!=null)return;checkPixels();if(variant++==0){upload();stage=2;}else{SkinClient.command("select",originalSkin,false);stage=4;}}
  else if(stage==4){if(SkinClient.busy)return;check(Json.opt(SkinClient.library.getAsJsonObject("profile"),"active","").equals(originalSkin),"Original skin not restored");if(!created.isEmpty()){SkinClient.command("delete",created.remove(0),false);return;}check(SkinClient.status.isEmpty(),"Skin cleanup failed");System.out.println("RIVET_MAP_SKINS_OK checks="+checks+" changes=2 world=true minimap=true overlay=true");var s=MapSettings.INSTANCE;s.size=144;s.zoom=1.25f;s.rotate=false;s.opacity=.7f;s.hud.mapOffsetX=21;s.hud.directionOffsetY=37;s.hud.directionCoordinates=false;MapRenderSettings.INSTANCE.slopes=3;MapRenderSettings.INSTANCE.depth=false;MapCaves.configure(new MapCaves.Options(3,1,1800,700,3,false,2));MapCaves.set(dim(),new MapCaves.View(2,false,24,42,true));s.save();verifySettings();mc.level.disconnect();mc.disconnect();stage=10;next=now+1500;}
  else if(stage==11){verifySettings();System.out.println("RIVET_MAP_REJOIN_OK checks="+checks);done=true;mc.stop();}
 }catch(Throwable failure){done=true;System.out.println("RIVET_MAP_INTEGRATION_FAILED stage="+stage+" created="+created);failure.printStackTrace();try{if(Files.exists(backup()))Files.copy(backup(),settings(),StandardCopyOption.REPLACE_EXISTING);}catch(Exception ignored){}mc.stop();}}
 private static void checkPixels()throws Exception{var mc=Minecraft.getInstance();try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){if(!"false".equalsIgnoreCase(System.getenv("RIVET_UI_SCREENSHOTS")))image.writeToFile(mc.gameDirectory.toPath().resolve("map-skin-check-"+variant+".png"));int wanted=variant==0?0xff4422ff:0xffffcc22;for(int cx:new int[]{150,450}){boolean base=false,hat=false;int scale=(int)mc.getWindow().getGuiScale();for(int y=94*scale;y<106*scale;y++)for(int x=(cx-6)*scale;x<(cx+6)*scale;x++){int pixel=image.getPixelRGBA(x,y);base|=pixel==wanted;hat|=pixel==0xff33ee55;}check(base,"Radar did not update face pixels variant="+variant+" x="+cx);check(hat,"Radar lost skin overlay x="+cx);}}}
 private static final class RadarSheet extends Screen {
  RadarSheet(){super(Component.literal("Map skin integration"));}
  @Override public void render(GuiGraphics g,int x,int y,float delta){g.fill(0,0,width,height,0xff222222);try{var owner=MapRadar.class.getDeclaredField("owner");var nearby=MapRadar.class.getDeclaredField("nearby");var next=MapRadar.class.getDeclaredField("next");for(var f:List.of(owner,nearby,next))f.setAccessible(true);Object oldOwner=owner.get(null),oldNearby=nearby.get(null);long oldNext=next.getLong(null);var r=MapRenderSettings.INSTANCE;boolean icons=r.radarIcons,world=r.radarWorld,mini=r.radar,players=r.players;r.radarIcons=r.radarWorld=r.radar=r.players=true;try{owner.set(null,Minecraft.getInstance().level);nearby.set(null,List.of(fixture));next.setLong(null,Long.MAX_VALUE);var view=new MapViewport();view.center(fixture.getX(),fixture.getZ());MapRadar.world(g,dim(),view,300,200);MapRadar.minimap(g,new MinimapProjection(fixture.getX(),fixture.getZ(),1,0,false),350,0,200,true);}finally{owner.set(null,oldOwner);nearby.set(null,oldNearby);next.setLong(null,oldNext);r.radarIcons=icons;r.radarWorld=world;r.radar=mini;r.players=players;}frames++;}catch(Exception e){throw new IllegalStateException(e);}}
 }
}
