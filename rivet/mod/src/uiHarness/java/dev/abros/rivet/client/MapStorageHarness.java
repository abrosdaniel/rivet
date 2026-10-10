package dev.abros.rivet.client;
import dev.abros.rivet.core.map.*;
import dev.abros.rivet.core.Json;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
/** Isolated disk fixture, real client cache/GPU lifecycle, cold and warm large-map measurements. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapStorageHarness {
 private static final String MODE=System.getenv("RIVET_MAP_STORAGE");private static final String DIM="minecraft:overworld";private static final int X=10000,Z=-10000,SIDE=128;
 private static UUID a,b,original,target;private static int phase,ticks,checks,moves;private static boolean done;private static long deadline,start,first,renderStart;private static WorldMapScreen screen;private static CompletableFuture<Void> seed;private static final List<Long> timings=new ArrayList<>();private static boolean measuring;private static net.minecraft.world.phys.Vec3 position;private static int fps;
 private static Path meta(){return Minecraft.getInstance().gameDirectory.toPath().resolve("rivet/map-storage-test.json");}
 private static Object field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);checks++;}
 private static MapRepository repo(UUID id){var mc=Minecraft.getInstance();return new MapRepository(mc.gameDirectory.toPath().resolve("rivet/maps"),id,mc.player.getUUID());}
 private static MapTile tile(int color,int height){var t=new MapTile();for(int z=0;z<16;z++)for(int x=0;x<16;x++)t.set(x,z,color,height);return t;}
 private static void connect(){var mc=Minecraft.getInstance();ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString("127.0.0.1:25598"),new ServerData("Map storage check","127.0.0.1:25598",ServerData.Type.OTHER),false,null);}
 private static void select(UUID id){target=id;ServerMenuClient.state.getAsJsonObject("map").addProperty("world",id.toString());if(!id.equals(WorldMapClient.worldId()))WorldMapClient.tick();}
 private static void open(double zoom){var mc=Minecraft.getInstance();screen=new WorldMapScreen(null);mc.setScreen(screen);screen.view.center((X+SIDE/2)*16,(Z+SIDE/2)*16);screen.view.zoomAt(Math.log(zoom/screen.view.zoom())/Math.log(1.25),0,0,0,0);}
 private static boolean loaded(MapLayer layer,int color){var t=WorldMapClient.tile(layer,X,Z);return t!=null&&t.color(0,0)==color;}
 private static void cachesEmpty()throws Exception{for(var entry:Map.of(WorldMapClient.class,"tiles",MapTerrainCache.class,"images",MapRegionTextures.class,"textures").entrySet())check(((Map<?,?>)field(entry.getKey(),entry.getValue())).isEmpty(),"World change retained "+entry.getValue());}
 private static void limits()throws Exception{check(((Map<?,?>)field(WorldMapClient.class,"tiles")).size()<=8192,"Unbounded tile cache");check(((Map<?,?>)field(MapTerrainCache.class,"images")).size()<=8192,"Unbounded image cache");check(((Map<?,?>)field(MapRegionTextures.class,"textures")).size()<=2048,"Unbounded GPU cache");check(((Map<?,?>)field(MapRegionTextures.class,"prepared")).size()<=512,"Unbounded prepared images");}
 @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public static void identity(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){if(MODE!=null&&!done&&target!=null&&ServerMenuClient.state.has("map"))ServerMenuClient.state.getAsJsonObject("map").addProperty("world",target.toString());}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){if(MODE==null||done)return;var mc=Minecraft.getInstance();try{
  if(deadline==0)deadline=System.currentTimeMillis()+240000;if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Storage timeout phase="+phase);
  if(phase==0){if(!(mc.screen instanceof TitleScreen))return;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.resizeDisplay();fps=mc.options.framerateLimit().get();mc.options.framerateLimit().set(120);connect();phase=1;return;}
  if(phase==9){if(++ticks<25)return;connect();phase=10;return;}
  if(!WorldMapClient.ready()||!WorldMapClient.markersReady())return;
  if(phase==1){original=WorldMapClient.worldId();position=mc.player.position();if(MODE.equals("verify")){var j=Json.read(meta());a=UUID.fromString(Json.str(j,"a"));b=UUID.fromString(Json.str(j,"b"));select(a);phase=11;return;}if(MODE.equals("reuse")){var j=Json.read(meta());a=UUID.fromString(Json.str(j,"a"));b=UUID.fromString(Json.str(j,"b"));seed=CompletableFuture.completedFuture(null);phase=2;return;}a=UUID.randomUUID();b=UUID.randomUUID();var j=new com.google.gson.JsonObject();j.addProperty("a",a.toString());j.addProperty("b",b.toString());Json.write(meta(),j);var ra=repo(a);var rb=repo(b);seed=CompletableFuture.runAsync(()->{try{for(int z=0;z<SIDE;z++)for(int x=0;x<SIDE;x++)ra.write(DIM,X+x,Z+z,tile(0xff448844,64+(x+z)%8));ra.write("minecraft:the_nether",X,Z,tile(0xffaa3322,40));ra.write("minecraft:the_end",X,Z,tile(0xffdddd88,60));ra.write(MapLayer.cave(DIM,24),X,Z,tile(0xff555555,20));rb.write(DIM,X,Z,tile(0xff2244cc,80));}catch(Exception e){throw new CompletionException(e);}});phase=2;}
  else if(phase==2){if(!seed.isDone())return;seed.join();select(a);open(.125);start=System.nanoTime();phase=3;}
  else if(phase==3){select(a);var textures=(Map<MapRegionTextures.Key,MapRegionTextures.Texture>)field(MapRegionTextures.class,"textures");int detail=MapDetail.level(screen.view.zoom(),mc.getWindow().getGuiScale()),span=4<<detail;int expected=(Math.floorDiv(X+SIDE-1,span)-Math.floorDiv(X,span)+1)*(Math.floorDiv(Z+SIDE-1,span)-Math.floorDiv(Z,span)+1);long ready=textures.entrySet().stream().filter(e->e.getKey().level()==detail&&e.getValue().complete()).count();if(++ticks%100==0)System.out.println("RIVET_MAP_LARGE_PROGRESS ready="+ready+" expected="+expected+" detail="+detail+" overview="+((Map<?,?>)field(MapTerrainCache.class,"overview")).size()+" changes="+((Map<?,?>)field(MapTerrainCache.class,"changes")).size()+" pending="+((Set<?>)field(MapTerrainCache.class,"pending")).size()+" loading="+((Set<?>)field(WorldMapClient.class,"loading")).size()+" layer="+MapCaves.layer(DIM));if(first==0&&!textures.isEmpty())first=(System.nanoTime()-start)/1000000;if(ready<expected||!loaded(MapLayer.surface(DIM),0xff448844))return;check(true,"Seed terrain present");System.out.println("RIVET_MAP_LARGE_COLD chunks=16384 firstMs="+first+" completeMs="+(System.nanoTime()-start)/1000000+" gpuPages="+textures.size());measuring=true;phase=4;ticks=0;}
  else if(phase==4){select(a);if(++ticks<80)return;measuring=false;report("warm");limits();timings.clear();measuring=true;phase=5;ticks=0;}
  else if(phase==5){select(a);if(++ticks%8==0){double zoom=switch(moves++%3){case 0->.125;case 1->.5;default->2;};screen.view.center((X+32+(moves%3)*32)*16,(Z+32+(moves%2)*64)*16);screen.view.zoomAt(Math.log(zoom/screen.view.zoom())/Math.log(1.25),0,0,0,0);}if(ticks<160)return;measuring=false;report("panZoom");limits();select(b);cachesEmpty();phase=6;}
  else if(phase==6){select(b);if(!loaded(MapLayer.surface(DIM),0xff2244cc))return;check(repo(b).read("minecraft:the_nether",X,Z).isEmpty(),"Other world's dimension leaked");select(a);cachesEmpty();phase=7;}
  else if(phase==7){select(a);if(!loaded(MapLayer.surface(DIM),0xff448844)||!loaded(MapLayer.surface("minecraft:the_nether"),0xffaa3322)||!loaded(MapLayer.surface("minecraft:the_end"),0xffdddd88)||!loaded(MapLayer.cave(DIM,24),0xff555555))return;mc.player.connection.sendCommand("execute in minecraft:the_nether run tp @s 0 100 0");phase=8;}
  else if(phase==8){select(a);if(!mc.level.dimension().location().toString().equals("minecraft:the_nether"))return;check(loaded(MapLayer.surface(DIM),0xff448844),"Dimension switch lost surface");check(loaded(MapLayer.surface("minecraft:the_nether"),0xffaa3322),"Nether loaded other dimension");mc.player.connection.sendCommand("execute in minecraft:overworld run tp @s "+position.x+" "+position.y+" "+position.z);phase=12;}
  else if(phase==12){select(a);if(!mc.level.dimension().location().toString().equals(DIM))return;WorldMapClient.flush();mc.level.disconnect();mc.disconnect();phase=9;ticks=0;}
  else if(phase==10){select(a);if(!loaded(MapLayer.surface(DIM),0xff448844))return;check(repo(a).chunks(DIM).size()>=16384,"Reconnect lost explored index");select(original);System.out.println("RIVET_MAP_STORAGE_OK checks="+checks+" reconnect=true identities=true dimensions=true caves=true");finish();}
  else if(phase==11){select(a);if(!loaded(MapLayer.surface(DIM),0xff448844)||!loaded(MapLayer.cave(DIM,24),0xff555555))return;check(repo(a).chunks(DIM).size()>=16384,"Restart lost explored index");check(repo(b).read(DIM,X,Z).orElseThrow().color(0,0)==0xff2244cc,"Restart mixed worlds");select(original);System.out.println("RIVET_MAP_STORAGE_RESTART_OK checks="+checks);finish();}
 }catch(Throwable failure){System.out.println("RIVET_MAP_STORAGE_FAILED phase="+phase);failure.printStackTrace();if(original!=null&&WorldMapClient.allowed())select(original);finish();}}
 private static void finish(){done=true;measuring=false;Minecraft.getInstance().options.framerateLimit().set(fps);Minecraft.getInstance().stop();}
 private static void report(String name){var sorted=timings.stream().sorted().toList();System.out.println("RIVET_MAP_PERF mode="+name+" frames="+sorted.size()+" medianMs="+sorted.get(sorted.size()/2)/1e6+" p95Ms="+sorted.get((int)(sorted.size()*.95))/1e6+" maxMs="+sorted.getLast()/1e6+" heapUsedMiB="+java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()/1048576);timings.clear();}
 @SubscribeEvent public static void before(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre e){if(measuring)renderStart=System.nanoTime();}
 @SubscribeEvent public static void after(net.neoforged.neoforge.client.event.RenderFrameEvent.Post e){if(measuring&&renderStart!=0)timings.add(System.nanoTime()-renderStart);}
}
