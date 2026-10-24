package dev.abros.rivet.client;

import dev.abros.rivet.core.map.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cold disk loading beyond the full-resolution cache, exact seams, retained LOD and reopen. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapTerrainHarness {
 private static final String ADDRESS=System.getenv("RIVET_MAP_TERRAIN_ADDRESS"),DIM="rivet:terrain-check";
 private static final int MIN=-32,MAX=64;
 private static int phase=-1,ticks,checks,deferredAttempts;private static long deadline,started;private static boolean done;
 private static final AtomicBoolean prepared=new AtomicBoolean();private static volatile Throwable preparationFailure;
 private static final Map<MapRegionTextures.Key,MapRegionTextures.Texture> retained=new HashMap<>();
 private static MapRegionTextures.Key discoveredRegion;
 private static WorldMapScreen map;private static boolean edited,profiled;private static long lastFrame,frameStart,lastRender,lastGc;
 private static MapRepository fixture;private static java.nio.file.Path fixtureRoot;private static UUID fixtureWorld;
 private static final String RESTART_FILE=System.getenv("RIVET_TERRAIN_RESTART_FILE");
 private static volatile boolean previewsSaved;private static long previewStart;
 private static final java.util.concurrent.CountDownLatch holdColours=new java.util.concurrent.CountDownLatch(1),coloursHeld=new java.util.concurrent.CountDownLatch(1);
 private static final java.util.concurrent.CountDownLatch oldPageHeld=new java.util.concurrent.CountDownLatch(1);
 private static final java.util.concurrent.CountDownLatch holdOldPage=new java.util.concurrent.CountDownLatch(1);
 private static final java.util.concurrent.CountDownLatch holdTerrainIo=new java.util.concurrent.CountDownLatch(1);
 private static Object field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 private static void set(Class<?> type,String name,Object value)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
 private static int height(int x,int z){return 80+Math.floorMod(x,32)/8+Math.floorMod(z,32)/8+(edited&&Math.floorDiv(x,16)==31&&Math.floorDiv(z,16)==31?9:0);}
 private static int color(int x,int z){return 0xff000000|(90+Math.floorMod(x>>4,32))<<16|(120+Math.floorMod(z>>4,32))<<8|70;}
 private static boolean inside(int x,int z){return x>=MIN*16&&x<MAX*16&&z>=MIN*16&&z<MAX*16;}
 private static int expected(int x,int z){int h=height(x,z);return MapColors.surface(color(x,z),0xff000000,h,inside(x,z-1)?height(x,z-1):h,inside(x-1,z-1)?height(x-1,z-1):h,false);}
 private static int[] expectedTile(int cx,int cz,int detail){int[] p=new int[256];for(int z=0;z<16;z++)for(int x=0;x<16;x++)p[z*16+x]=expected(cx*16+x,cz*16+z);return MapDetail.pyramid(p)[detail];}
 private static void check(boolean b,String message){if(!b)throw new IllegalStateException(message);checks++;}
 private static boolean settled(){
  for(int rz=-1;rz<=1;rz++)for(int rx=-1;rx<=1;rx++){
   var key=new MapRegionTextures.Key(DIM,rx,rz,3);var texture=MapRegionTextures.texture(key,3);if(texture==null||!texture.complete())return false;
   var pixels=texture.texture().getPixels();if(pixels==null||pixels.getWidth()!=64)return false;
   for(int cz=0;cz<32;cz++)for(int cx=0;cx<32;cx++){
    int[] expected=expectedTile(rx*32+cx,rz*32+cz,3);
    for(int z=0;z<2;z++)for(int x=0;x<2;x++){int c=expected[z*2+x],rgba=c&0xff00ff00|(c&255)<<16|(c>>>16&255);if(pixels.getPixelRGBA(cx*2+x,cz*2+z)!=rgba)return false;}
   }
  }
  return true;
 }
 /** Force a page sampled before an edit to finish after the edit, on every machine. */
 private static boolean queueOldPage()throws Exception{
  var key=new MapRegionTextures.Key(DIM,0,0,3);
  if(((Set<?>)field(MapRegionTextures.class,"pending")).contains(key))return true;
  var textures=(Map<MapRegionTextures.Key,MapRegionTextures.Texture>)field(MapRegionTextures.class,"textures");
  var old=textures.get(key);check(old!=null,"Missing page for delayed composition");
  // A previously prepared upload can replace the sentinel; consume it before requesting composition.
  if(((Map<?,?>)field(MapRegionTextures.class,"prepared")).containsKey(key)){
   set(MapRegionTextures.class,"scans",8);MapRegionTextures.texture(key,3);return false;
  }
  textures.put(key,new MapRegionTextures.Texture(old.id(),old.texture(),old.level(),Long.MIN_VALUE,false));
  ((Map<?,?>)field(MapRegionTextures.class,"checked")).remove(key);
  var queue=(Set<MapRegionTextures.Key>)field(MapRegionTextures.class,"scanQueue");queue.clear();queue.add(key);
  if(deferredAttempts<3){
   // Exercise a spent frame budget on every run, rather than relying on runner speed.
   set(MapRegionTextures.class,"scans",8);
   MapRegionTextures.texture(key,3);
   check(!((Set<?>)field(MapRegionTextures.class,"pending")).contains(key),"Exhausted scan budget was ignored");deferredAttempts++;
   return false;
  }
  MapRegionTextures.prepareView(Set.of(key));
  return ((Set<?>)field(MapRegionTextures.class,"pending")).contains(key);
 }
 @SubscribeEvent public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event){
  if(ADDRESS==null||done||phase!=10)return;long now=System.nanoTime();
  long gc=java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionTime())).sum();
  if(lastFrame!=0){
   dev.abros.rivet.core.PerformanceMetrics.record("client.frame.interval",now-lastFrame,false);
   if(now-lastFrame>35_000_000)System.out.println("RIVET_TERRAIN_SLOW_FRAME endEpochMs="+System.currentTimeMillis()+" intervalMs="+(now-lastFrame)/1e6+" renderMs="+lastRender/1e6+" gcMs="+(gc-lastGc));
  }
  lastFrame=frameStart=now;lastGc=gc;
 }
 @SubscribeEvent public static void afterFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event){
  if(ADDRESS!=null&&!done&&phase==10&&frameStart!=0){lastRender=System.nanoTime()-frameStart;dev.abros.rivet.core.PerformanceMetrics.record("client.frame.render",lastRender,false);}
 }
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){
  if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();
  try{
   if(phase==-1){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+180000;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.resizeDisplay();ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Map terrain test",ADDRESS,ServerData.Type.OTHER),false,null);phase=0;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Terrain timeout phase="+phase);
   if(!WorldMapClient.ready())return;ticks++;
   if(phase==0){if(ticks<100)return;WorldMapClient.flush();
    if(RESTART_FILE!=null&&java.nio.file.Files.exists(java.nio.file.Path.of(RESTART_FILE))){
     var lines=java.nio.file.Files.readAllLines(java.nio.file.Path.of(RESTART_FILE));fixtureRoot=java.nio.file.Path.of(lines.get(0));fixtureWorld=UUID.fromString(lines.get(1));
     fixture=new MapRepository(fixtureRoot,fixtureWorld,UUID.fromString(lines.get(2)));set(WorldMapClient.class,"repository",fixture);previewsSaved=true;edited=true;phase=8;return;
    }
    fixtureRoot=java.nio.file.Files.createTempDirectory("rivet-terrain-test-");fixtureWorld=UUID.randomUUID();var repo=new MapRepository(fixtureRoot,fixtureWorld,mc.player.getUUID());fixture=repo;set(WorldMapClient.class,"repository",repo);
    MapTerrainCache.reset();MapRegionTextures.reset();
    MapTerrainCache.work(()->{try{for(int cz=MIN;cz<MAX;cz++)for(int cx=MIN;cx<MAX;cx++){var tile=new MapTile();for(int z=0;z<16;z++)for(int x=0;x<16;x++)tile.set(x,z,color(cx*16+x,cz*16+z),height(cx*16+x,cz*16+z));repo.write(DIM,cx,cz,tile);}}catch(Throwable ex){preparationFailure=ex;}finally{prepared.set(true);}});phase=1;
   }else if(phase==1){if(!prepared.get())return;if(preparationFailure!=null)throw new IllegalStateException(preparationFailure);
    var known=new HashSet<MapRepository.Chunk>();for(int z=MIN;z<MAX;z++)for(int x=MIN;x<MAX;x++)known.add(new MapRepository.Chunk(x,z));
    ((Map<MapLayer,Set<MapRepository.Chunk>>)field(WorldMapClient.class,"known")).put(MapLayer.surface(DIM),known);((Set<MapLayer>)field(WorldMapClient.class,"indexed")).add(MapLayer.surface(DIM));((Set<MapLayer>)field(WorldMapClient.class,"indexing")).add(MapLayer.surface(DIM));
    map=new WorldMapScreen(null);mc.setScreen(map);var dim=WorldMapScreen.class.getDeclaredField("dimension");dim.setAccessible(true);dim.set(map,DIM);map.view.center(256,256);map.view.zoomAt(-30,0,0,0,0);started=System.nanoTime();phase=2;ticks=0;
    System.out.println("RIVET_TERRAIN_COLD_START chunks="+known.size());
   }else if(phase==2){if(ticks%20==0)System.out.println("RIVET_TERRAIN_PROGRESS overview="+((Map<?,?>)field(MapTerrainCache.class,"overview")).size()+" textures="+((Map<?,?>)field(MapRegionTextures.class,"textures")).size()+" pending="+((Set<?>)field(MapRegionTextures.class,"pending")).size()+" queued="+((Set<?>)field(MapRegionTextures.class,"scanQueue")).size()+" prepared="+((Map<?,?>)field(MapRegionTextures.class,"prepared")).size());
    if(!settled())return;check(((Map<?,?>)field(MapTerrainCache.class,"images")).size()<=8192,"Full-resolution cache unbounded");check(((Map<?,?>)field(MapTerrainCache.class,"overview")).size()>=9216,"Overview lost evicted tiles");
    var empty=(Map<MapRegionTextures.Key,Long>)field(MapRegionTextures.class,"empty");
    check(!empty.isEmpty()&&empty.size()<=8192,"Empty region cache absent or unbounded");
    var emptyKey=empty.entrySet().stream().filter(e2->e2.getKey().dimension().equals(DIM)&&e2.getValue()==WorldMapClient.terrainIndexRevision()).map(Map.Entry::getKey).findFirst().orElseThrow();
    int scansBefore=(int)field(MapRegionTextures.class,"scans");
    for(int i=0;i<20;i++)check(MapRegionTextures.texture(emptyKey,emptyKey.level())==null,"Empty region produced a texture");
    check((int)field(MapRegionTextures.class,"scans")==scansBefore,"Empty region was rescanned without index changes");
    for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++){var key=new MapRegionTextures.Key(DIM,x,z,3);retained.put(key,MapRegionTextures.texture(key,3));}
    System.out.println("RIVET_TERRAIN_COLD_OK ms="+(System.nanoTime()-started)/1000000+" chunks=9216 exactPixels=36864");
    discoveredRegion=empty.keySet().stream().filter(k->k.dimension().equals(DIM)).findFirst().orElseThrow();
    var discoveredKey=new WorldMapClient.TileKey(DIM,discoveredRegion.x()*discoveredRegion.chunks(),discoveredRegion.z()*discoveredRegion.chunks());var discoveredTile=new MapTile();
    for(int z=0;z<16;z++)for(int x=0;x<16;x++)discoveredTile.set(x,z,0xffaa7755,80);
    fixture.write(DIM,discoveredKey.x(),discoveredKey.z(),discoveredTile);
    ((Map<WorldMapClient.TileKey,MapTile>)field(WorldMapClient.class,"tiles")).put(discoveredKey,discoveredTile);
    ((Map<MapLayer,Set<MapRepository.Chunk>>)field(WorldMapClient.class,"known")).get(MapLayer.surface(DIM)).add(new MapRepository.Chunk(discoveredKey.x(),discoveredKey.z()));
    set(WorldMapClient.class,"knownRevision",WorldMapClient.terrainIndexRevision()+1);phase=11;ticks=0;
   }else if(phase==11){
    var discovered=MapRegionTextures.texture(discoveredRegion,discoveredRegion.level());if(discovered==null||!discovered.complete())return;
    check((discovered.texture().getPixels().getPixelRGBA(0,0)>>>24)!=0,"Exploring a cached empty region did not reveal terrain");
    System.out.println("RIVET_TERRAIN_EMPTY_INVALIDATION_OK");phase=3;ticks=0;
   }else if(phase==10){
    map.view.center(256+Math.sin(ticks*.06)*384,256+Math.cos(ticks*.04)*384);
    if(ticks%8==0)map.view.zoomAt(ticks%80<40?2:-2,0,0,0,0);
    if(ticks<Integer.parseInt(System.getenv().getOrDefault("RIVET_TERRAIN_MOTION_TICKS","240")))return;
    var metrics=dev.abros.rivet.core.PerformanceMetrics.snapshot();
    dev.abros.rivet.core.PerformanceMetrics.distributions().forEach((name,d)->{
     var total=metrics.get(name);System.out.println("RIVET_TERRAIN_MOTION metric="+name+" samples="+d.samples()+" p50Ms="+d.p50Nanos()/1e6+" p95Ms="+d.p95Nanos()/1e6+" p99Ms="+d.p99Nanos()/1e6+" maxMs="+total.maxNanos()/1e6);
    });
    check(metrics.containsKey("client.worldmap.terrain"),"Motion did not render terrain");
    dev.abros.rivet.core.PerformanceMetrics.detailed(false);profiled=true;map.view.center(256,256);map.view.zoomAt(-30,0,0,0,0);phase=3;ticks=0;
   }else if(phase==3){if(ticks<20)return;
    if(!profiled){dev.abros.rivet.core.PerformanceMetrics.clear();dev.abros.rivet.core.PerformanceMetrics.detailed(true);lastFrame=0;phase=10;ticks=0;return;}
    check(settled(),"Overview changed after settling");for(var entry:retained.entrySet())check(MapRegionTextures.texture(entry.getKey(),3).texture()==entry.getValue().texture(),"Stationary view rebuilt texture");mc.setScreen(null);map=new WorldMapScreen(null);mc.setScreen(map);var dim=WorldMapScreen.class.getDeclaredField("dimension");dim.setAccessible(true);dim.set(map,DIM);map.view.center(256,256);phase=4;ticks=0;
   }else if(phase==4){check(settled(),"Reopening map lost terrain");for(var entry:retained.entrySet())check(MapRegionTextures.texture(entry.getKey(),3).texture()==entry.getValue().texture(),"Reopening discarded GPU cache");map.view.zoomAt(30,0,0,0,0);phase=5;ticks=0;
   }else if(phase==5){var key=new MapRegionTextures.Key(DIM,0,0);var texture=MapRegionTextures.texture(key,0);if(texture==null)return;var pixels=texture.texture().getPixels();for(int z=0;z<64;z++)for(int x=0;x<64;x++){int c=expected(x,z),rgba=c&0xff00ff00|(c&255)<<16|(c>>>16&255);if(pixels.getPixelRGBA(x,z)!=rgba)return;}check(pixels.getWidth()==64,"Detailed texture size wrong");map.view.zoomAt(-30,0,0,0,0);phase=6;}
   else if(phase==6){check(settled(),"Returning to overview lost pixels");
    // Stop map rendering from adding work while the test establishes its worker barrier.
    mc.setScreen(null);
    if(!MapTerrainCache.work(()->{mc.execute(oldPageHeld::countDown);try{holdOldPage.await();}catch(InterruptedException ex){Thread.currentThread().interrupt();}}))return;
    phase=12;ticks=0;
   }else if(phase==12){
    if(oldPageHeld.getCount()!=0)return;
    if(!queueOldPage()){if(ticks>100)throw new IllegalStateException("Old page did not queue within the existing worker barrier");return;}
    check(deferredAttempts==3,"Delayed-page regression did not exercise deferred scheduling");
    System.out.println("RIVET_TERRAIN_DEFERRED_PAGE_OK ticks="+ticks);
    edited=true;var tile=new MapTile();for(int z=0;z<16;z++)for(int x=0;x<16;x++)tile.set(x,z,color(496+x,496+z),height(496+x,496+z));var key=new WorldMapClient.TileKey(DIM,31,31);((Map<WorldMapClient.TileKey,MapTile>)field(WorldMapClient.class,"tiles")).put(key,tile);MapTerrainCache.changed(key);check(((Map<?,?>)field(MapRegionTextures.class,"invalidated")).containsKey(new MapRegionTextures.Key(DIM,0,0,3)),"In-flight page was not invalidated by edit");holdOldPage.countDown();mc.setScreen(map);phase=7;ticks=0;}
   else if(phase==7){if(!settled())return;check(true,"Overview update");if(!WorldMapClient.previewIo(()->previewsSaved=true))return;phase=8;}
   else if(phase==8){if(!previewsSaved)return;
    if(RESTART_FILE!=null)java.nio.file.Files.writeString(java.nio.file.Path.of(RESTART_FILE),fixtureRoot+"\n"+fixtureWorld+"\n"+mc.player.getUUID()+"\n");
    if(!MapTerrainCache.work(()->{coloursHeld.countDown();try{holdColours.await();}catch(InterruptedException ex){Thread.currentThread().interrupt();}}))return;
    if(!WorldMapClient.io(()->{try{holdTerrainIo.await();}catch(InterruptedException ex){Thread.currentThread().interrupt();}}))return;
    MapTerrainCache.reset();MapRegionTextures.reset();((Map<?,?>)field(WorldMapClient.class,"tiles")).clear();
    ((Map<?,?>)field(WorldMapClient.class,"known")).remove(MapLayer.surface(DIM));((Set<?>)field(WorldMapClient.class,"indexed")).remove(MapLayer.surface(DIM));
    mc.setScreen(null);map=new WorldMapScreen(null);mc.setScreen(map);var dim=WorldMapScreen.class.getDeclaredField("dimension");dim.setAccessible(true);dim.set(map,DIM);map.view.center(256,256);map.view.zoomAt(-30,0,0,0,0);phase=9;ticks=0;previewStart=System.nanoTime();
   }else if(phase==9){if(coloursHeld.getCount()!=0)return;MapTerrainCache.relightAll();boolean all=true;
    for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++){var key=new MapRegionTextures.Key(DIM,x,z,3);var texture=MapRegionTextures.texture(key,3);if(texture==null){all=false;continue;}check(texture.signature()==Long.MIN_VALUE,"Expected persisted preview before any terrain composition");
     var pixels=texture.texture().getPixels();for(int cz=0;cz<32;cz++)for(int cx=0;cx<32;cx++){int[] expected=expectedTile(x*32+cx,z*32+cz,3);for(int pz=0;pz<2;pz++)for(int px=0;px<2;px++){int c=expected[pz*2+px],rgba=c&0xff00ff00|(c&255)<<16|(c>>>16&255);check(pixels.getPixelRGBA(cx*2+px,cz*2+pz)==rgba,"Persisted preview pixel changed region="+x+","+z+" pixel="+(cx*2+px)+","+(cz*2+pz)+" expected="+Integer.toHexString(rgba)+" actual="+Integer.toHexString(pixels.getPixelRGBA(cx*2+px,cz*2+pz)));}}}
    if(!all){if(ticks>100)throw new IllegalStateException("Persisted previews did not restore");return;}
    var visible=MapRegionTextures.viewKeys(MapLayer.surface(DIM),map.view,map.width,map.height,mc.getWindow().getGuiScale());
    MapRegionTextures.prepareView(visible);if(!MapRegionTextures.restored(visible)){if(ticks>100)throw new IllegalStateException("Whole saved view did not become ready");return;}
    check(((Map<?,?>)field(MapRegionTextures.class,"prepared")).size()<=512,"Preview preparation exceeded bound");
    check(((Map<?,?>)field(MapTerrainCache.class,"images")).isEmpty(),"Preview restoration depended on recomputing terrain");
    holdColours.countDown();holdTerrainIo.countDown();System.out.println("RIVET_TERRAIN_PREVIEW_MS="+(System.nanoTime()-previewStart)/1000000);System.out.println("RIVET_TERRAIN_OK checks="+checks+" disk=true eviction=true seams=true reopen=true lod=true borderUpdates=true sessionPreview=true indexIndependent=true");done=true;mc.stop();
   }
  }catch(Throwable ex){holdOldPage.countDown();holdColours.countDown();holdTerrainIo.countDown();done=true;System.out.println("RIVET_TERRAIN_FAILED phase="+phase);ex.printStackTrace();mc.stop();}
 }
}
