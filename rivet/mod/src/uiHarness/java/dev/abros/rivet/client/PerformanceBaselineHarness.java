package dev.abros.rivet.client;

import dev.abros.rivet.core.*;
import dev.abros.rivet.core.map.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.nio.file.*;
import java.util.*;
import java.lang.management.ManagementFactory;

/** Controlled client baseline. Synthetic markers are never written to the user's repository. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class PerformanceBaselineHarness {
 private static final String ADDRESS=System.getenv("RIVET_PERF_ADDRESS"),OUTPUT=System.getenv("RIVET_PERF_OUTPUT");
 private static final String[] SCENES={"minimap-0","minimap-100","minimap-1000","minimap-4096","rotate-4096","worldmap-4096"};
 private static final int FIRST_SCENE=Boolean.parseBoolean(System.getenv("RIVET_PERF_WORLD_ONLY"))?5:0;
 private static int phase=-1,ticks,scene=FIRST_SCENE,repeat;private static boolean done;private static long deadline,lastFrame,gcStart,heapStart;
 private static WorldMapScreen map;private static Object originalRepo,originalReceived,originalMarkers;private static boolean enabled;private static float yaw;private static final JsonArray results=new JsonArray();
 private static Object get(String name)throws Exception{var f=WorldMapClient.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 private static void set(String name,Object v)throws Exception{var f=WorldMapClient.class.getDeclaredField(name);f.setAccessible(true);f.set(null,v);}
 private static long gc(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->Math.max(0,b.getCollectionTime())).sum();}
 private static long heap(){return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();}
 @SubscribeEvent public static void frame(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre event){if(ADDRESS==null||done||phase!=2)return;long now=System.nanoTime();if(lastFrame!=0)PerformanceMetrics.record("client.frame.interval",now-lastFrame,false);lastFrame=now;}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();try{
  if(phase==-1){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+240000;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.resizeDisplay();ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Performance",ADDRESS,ServerData.Type.OTHER),false,null);phase=0;return;}
  if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Performance timeout phase="+phase);
  if(!WorldMapClient.ready()||!WorldMapClient.markersReady())return;
  if(phase==0){if(++ticks<100)return;originalRepo=get("repository");originalReceived=get("receivedRepository");originalMarkers=get("markers");enabled=MapSettings.INSTANCE.enabled;yaw=mc.player.getYRot();WorldMapClient.flush();set("repository",new MapRepository(Files.createTempDirectory("rivet-perf-"),UUID.randomUUID(),mc.player.getUUID()));set("receivedRepository",new MapRepository(Files.createTempDirectory("rivet-perf-received-"),UUID.randomUUID(),mc.player.getUUID()));MapSettings.INSTANCE.enabled=true;prepare();return;}
  if(scene==4)mc.player.setYRot(yaw+ticks*3);
  if(phase==1){if(++ticks<40)return;PerformanceMetrics.clear();PerformanceMetrics.detailed(true);lastFrame=0;heapStart=heap();gcStart=gc();phase=2;ticks=0;return;}
  if(phase==2){if(++ticks<120)return;capture();PerformanceMetrics.detailed(false);if(++scene==SCENES.length){scene=FIRST_SCENE;repeat++;}if(repeat<3){prepare();return;}done=true;restore();var root=new JsonObject();root.addProperty("java",System.getProperty("java.version"));root.addProperty("os",System.getProperty("os.name"));root.addProperty("arch",System.getProperty("os.arch"));root.addProperty("processors",Runtime.getRuntime().availableProcessors());root.addProperty("heapMax",Runtime.getRuntime().maxMemory());root.addProperty("windowWidth",mc.getWindow().getWidth());root.addProperty("windowHeight",mc.getWindow().getHeight());root.addProperty("guiScale",mc.options.guiScale().get());root.addProperty("renderDistance",mc.options.renderDistance().get());root.addProperty("simulationDistance",mc.options.simulationDistance().get());root.addProperty("fpsLimit",mc.options.framerateLimit().get());root.addProperty("vsync",mc.options.enableVsync().get());root.addProperty("syntheticMarkers",true);root.addProperty("phaseSeconds",6);root.add("results",results);Json.write(Path.of(OUTPUT),root);System.out.println("RIVET_PERFORMANCE_OK scenes="+results.size());mc.stop();}
 }catch(Throwable ex){done=true;PerformanceMetrics.detailed(false);System.out.println("RIVET_PERFORMANCE_FAILED");ex.printStackTrace();try{restore();}catch(Exception ignored){}mc.stop();}}
 private static void prepare()throws Exception {var mc=Minecraft.getInstance();int count=scene==0?0:scene==1?100:scene==2?1000:4096;var markers=new ArrayList<MapMarker>();var rng=new Random(1847);for(int i=0;i<count;i++)markers.add(new MapMarker(new UUID(0,i+1),mc.level.dimension().location().toString(),"M"+i,mc.player.getBlockX()+rng.nextInt(1024)-512,mc.player.getBlockY(),mc.player.getBlockZ()+rng.nextInt(1024)-512,0xffe1bf73,"pin"));set("markers",List.copyOf(markers));if(scene==5){map=new WorldMapScreen(null);mc.setScreen(map);}else mc.setScreen(null);mc.player.setYRot(yaw);phase=1;ticks=0;}
 private static void capture(){var row=new JsonObject();row.addProperty("scene",SCENES[scene]);row.addProperty("repeat",repeat);row.addProperty("heapStartBytes",heapStart);row.addProperty("heapEndBytes",heap());row.addProperty("gcMillis",gc()-gcStart);var metrics=new JsonObject();var windows=PerformanceMetrics.distributions();PerformanceMetrics.snapshot().forEach((name,s)->{var j=new JsonObject();j.addProperty("count",s.count());j.addProperty("meanMs",s.meanMillis());j.addProperty("maxMs",s.maxNanos()/1e6);var d=windows.get(name);if(d!=null){j.addProperty("windowSamples",d.samples());j.addProperty("p50Ms",d.p50Nanos()/1e6);j.addProperty("p95Ms",d.p95Nanos()/1e6);j.addProperty("p99Ms",d.p99Nanos()/1e6);}metrics.add(name,j);});row.add("metrics",metrics);results.add(row);System.out.println("RIVET_PERFORMANCE_SAMPLE scene="+SCENES[scene]+" repeat="+repeat);}
 private static void restore()throws Exception{PerformanceMetrics.detailed(false);if(originalRepo!=null){set("repository",originalRepo);set("receivedRepository",originalReceived);set("markers",originalMarkers);MapSettings.INSTANCE.enabled=enabled;Minecraft.getInstance().player.setYRot(yaw);}}
}
