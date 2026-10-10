package dev.abros.rivet.client;
import dev.abros.rivet.core.map.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
/** Opt-in real-server recolouring, GPU upload, minimap refresh and PNG export. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapViewsHarness {
 private static final String ADDRESS=System.getenv("RIVET_MAP_VIEWS_ADDRESS");
 private static int exportPixel,racePixel;private static long originalTime;private static float originalRain,originalThunder;
 private static int phase=-1,mode,ticks,checks;private static long deadline,signature;private static boolean done,enabled;
 private static com.google.gson.JsonObject saved,caves;private static WorldMapClient.TileKey key;private static MapExport.Job export;
 private static Object field(String name)throws Exception{var f=Minimap.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 private static void check(boolean ok,String why){if(!ok)throw new IllegalStateException(why);checks++;}
 private static int rgba(int c){return c&0xff00ff00|(c&255)<<16|(c>>>16&255);}
 private static void restore(){if(saved!=null){var mc=Minecraft.getInstance();if(mc.player!=null){mc.player.connection.sendCommand("time set "+Math.floorMod(originalTime,24000));mc.player.connection.sendCommand(originalRain>.5f?originalThunder>.5f?"weather thunder":"weather rain":"weather clear");}MapCaves.load(caves);MapRenderSettings.INSTANCE.load(saved);MapSettings.INSTANCE.enabled=enabled;MapRenderSettings.INSTANCE.changed();}}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
  if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();try{
   if(phase==-1){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+150000;mc.options.pauseOnLostFocus=false;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Map views",ADDRESS,ServerData.Type.OTHER),false,null);phase=0;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Timeout mode="+mode+" phase="+phase);
   if(!WorldMapClient.ready()||mc.player==null)return;ticks++;if(ticks%100==0)System.out.println("RIVET_MAP_VIEWS_PROGRESS phase="+phase+" mode="+mode+" tile="+(key==null?null:WorldMapClient.loaded(key)));
   if(phase==0){if(ticks<100)return;originalTime=mc.level.getDayTime();originalRain=mc.level.getRainLevel(1);originalThunder=mc.level.getThunderLevel(1);saved=MapRenderSettings.INSTANCE.json();caves=MapCaves.json();MapCaves.set(mc.level.dimension().location().toString(),new MapCaves.View(0,false,64,30,false));enabled=MapSettings.INSTANCE.enabled;MapSettings.INSTANCE.enabled=true;
    var r=MapRenderSettings.INSTANCE;r.load(new com.google.gson.JsonObject());check(r.lighting&&r.depth&&r.slopes==2&&r.blockColors==0&&r.biomeBlend&&r.flowers&&r.stainedGlass&&r.shortBlocks&&!r.biomesVanilla,"Reference render defaults");var legacy=new com.google.gson.JsonObject();legacy.addProperty("view",1);r.load(legacy);check(r.style().view()==0,"Obsolete night view must not lock automatic lighting");
    var state=net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();int precise=MapBlockColors.color(mc.level,mc.player.blockPosition(),state);r.blockColors=1;int vanilla=MapBlockColors.color(mc.level,mc.player.blockPosition(),state);check((vanilla&0xffffff)==state.getMapColor(mc.level,mc.player.blockPosition()).col,"Vanilla palette mismatch");check(precise!=vanilla,"Colour selector has no effect");r.blockColors=0;

    key=new WorldMapClient.TileKey(mc.level.dimension().location().toString(),mc.player.chunkPosition().x,mc.player.chunkPosition().z);phase=1;
   }else if(phase==1){mc.player.connection.sendCommand("time set "+(mode==1?18000:6000));mc.player.connection.sendCommand(mode==2?"weather thunder":"weather clear");MapRenderSettings.INSTANCE.lighting=mode!=3;MapRenderSettings.INSTANCE.changed();mc.setScreen(new WorldMapScreen(null));phase=2;ticks=0;
   }else if(phase==2){var tile=WorldMapClient.loaded(key);if(ticks<80||Math.abs(Math.floorMod(mc.level.getDayTime(),24000)-(mode==1?18000:6000))>1000||tile==null||tile.color(8,8)==0||tile.skyLight(8,8)<0)return;var image=MapTerrainCache.image(key);if(image==null||MapTerrainCache.waiting(key))return;
    if(mode==1)check(MapRenderSettings.INSTANCE.style().skyDarken()>=9,"Server night did not reach lighting");
    int expected=MapColors.render(tile,8,8,tile.groundHeight(8,7),tile.groundHeight(7,7),MapLayer.SURFACE,16,384,false,MapRenderSettings.INSTANCE.style());check(image.levels()[0][136]==expected,"CPU recolouring mismatch");
    var page=MapRegionTextures.texture(new MapRegionTextures.Key(key.dimension(),Math.floorDiv(key.x(),4),Math.floorDiv(key.z(),4)),0);if(page==null||page.texture().getPixels().getPixelRGBA(Math.floorMod(key.x(),4)*16+8,Math.floorMod(key.z(),4)*16+8)!=rgba(expected))return;check(true,"GPU recolouring");var folder=new java.io.File("/private/tmp/rivet-auto-light-preview");folder.mkdirs();UiCaptureHarness.grab(folder,"condition-"+mode+".png",mc.getMainRenderTarget(),m->{});
    exportPixel=expected;export=MapExport.export(key.layer(),new MapExport.Area(key.x()*16,key.z()*16,16,16));signature=(long)field("terrainSignature");mc.setScreen(null);phase=3;ticks=0;
   }else if(phase==3){if(ticks<60||!export.done)return;check(export.error.isEmpty(),"Export failed");var image=MapTerrainCache.image(key);if(MapTerrainCache.waiting(key))return;
    try(var png=com.mojang.blaze3d.platform.NativeImage.read(java.nio.file.Files.newInputStream(export.file))){check(png.getPixelRGBA(8,8)==rgba(exportPixel),"PNG did not preserve export-time lighting");}
    if(field("texture")==null||(mode>0&&(long)field("terrainSignature")==signature))return;check(true,"Minimap refresh");System.out.println("RIVET_MAP_VIEW_OK mode="+mode+" export="+export.file);
    if(++mode==4){racePixel=image.levels()[0][136];mc.setScreen(new Screen(net.minecraft.network.chat.Component.literal("Проверка смены настроек")){});MapRenderSettings.INSTANCE.lighting=true;MapRenderSettings.INSTANCE.changed();MapTerrainCache.image(key);MapRenderSettings.INSTANCE.lighting=false;MapRenderSettings.INSTANCE.changed();phase=4;ticks=0;}else phase=1;
   }else if(phase==4){if(ticks<20)return;var f=MapTerrainCache.class.getDeclaredField("images");f.setAccessible(true);var cached=((java.util.Map<WorldMapClient.TileKey,MapTerrainCache.Image>)f.get(null)).get(key);check(cached!=null&&cached.levels()[0][136]==racePixel,"Obsolete worker result overwrote the complete image");restore();done=true;System.out.println("RIVET_MAP_AUTO_LIGHT_OK checks="+checks+" serverTime=true staleWorker=true");mc.stop();}
  }catch(Throwable ex){restore();done=true;System.out.println("RIVET_MAP_AUTO_LIGHT_FAILED");ex.printStackTrace();mc.stop();}
 }
}
