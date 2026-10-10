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
import java.util.concurrent.*;
/** Disconnect while the bounded file queue is full must retain the last explored chunk. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapSaveQueueHarness {
 private static int phase,ticks;private static UUID original,id;private static MapRepository repository;private static CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);private static long deadline;
 private static Object field(String name)throws Exception{var f=WorldMapClient.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){if(System.getenv("RIVET_MAP_SAVE_QUEUE")==null||phase<0)return;var mc=Minecraft.getInstance();try{
  if(deadline==0)deadline=System.currentTimeMillis()+60000;if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Queue test timeout");
  if(phase==0){if(!(mc.screen instanceof TitleScreen))return;ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString("127.0.0.1:25598"),new ServerData("Map queue check","127.0.0.1:25598",ServerData.Type.OTHER),false,null);phase=1;return;}
  if(!WorldMapClient.ready()||!WorldMapClient.markersReady())return;
  if(phase==1){original=WorldMapClient.worldId();id=UUID.randomUUID();ServerMenuClient.state.getAsJsonObject("map").addProperty("world",id.toString());WorldMapClient.tick();repository=(MapRepository)field("repository");phase=2;return;}
  var io=(ThreadPoolExecutor)field("IO");
  if(phase==2){io.execute(()->{started.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}});phase=3;}
  else if(phase==3){if(started.getCount()!=0)return;var submit=WorldMapClient.class.getDeclaredMethod("submit",Runnable.class);submit.setAccessible(true);io.execute(()->{try{var old=new MapTile();old.set(0,0,0xff112233,12);repository.write("minecraft:overworld",123456,-123456,old);}catch(Exception ex){throw new RuntimeException(ex);}});while((boolean)submit.invoke(null,(Runnable)()->{})){};if(io.getQueue().size()!=256)throw new IllegalStateException("Normal queue limit changed");var key=new WorldMapClient.TileKey("minecraft:overworld",123456,-123456);var tile=new MapTile();tile.set(0,0,0xffddee11,77);((Map<WorldMapClient.TileKey,MapTile>)field("tiles")).put(key,tile);((Set<WorldMapClient.TileKey>)field("dirty")).add(key);WorldMapClient.flush();if(!((Set<?>)field("dirty")).contains(key))throw new IllegalStateException("Busy periodic flush dropped dirty state");WorldMapClient.reset();WorldMapClient.tick();if(WorldMapClient.markersReady())throw new IllegalStateException("Marker read was not queued behind save");release.countDown();phase=4;}
  else if(phase==4){if(++ticks<30)return;var tile=repository.read("minecraft:overworld",123456,-123456);if(tile.isEmpty()||tile.get().color(0,0)!=0xffddee11)throw new IllegalStateException("Last explored chunk lost on reset with full IO queue");started=new CountDownLatch(1);release=new CountDownLatch(1);io.execute(()->{started.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}});phase=5;}
  else if(phase==5){if(started.getCount()!=0)return;var submit=WorldMapClient.class.getDeclaredMethod("submit",Runnable.class);submit.setAccessible(true);while((boolean)submit.invoke(null,(Runnable)()->{})){};repository=(MapRepository)field("repository");var key=new WorldMapClient.TileKey("minecraft:overworld",123457,-123456);var last=new MapTile();last.set(0,0,0xffabcdef,88);((Map<WorldMapClient.TileKey,MapTile>)field("tiles")).put(key,last);((Set<WorldMapClient.TileKey>)field("dirty")).add(key);CompletableFuture.delayedExecutor(100,TimeUnit.MILLISECONDS).execute(release::countDown);WorldMapClient.shutdown();if(repository.read("minecraft:overworld",123457,-123456).orElseThrow().color(0,0)!=0xffabcdef)throw new IllegalStateException("Shutdown lost final snapshot");System.out.println("RIVET_MAP_SAVE_QUEUE_OK reset=true fifo=true markersRetry=true shutdown=true");finish();}
 }catch(Throwable failure){System.out.println("RIVET_MAP_SAVE_QUEUE_FAILED phase="+phase);failure.printStackTrace();finish();}}
 private static void finish(){release.countDown();phase=-1;var mc=Minecraft.getInstance();if(original!=null&&WorldMapClient.allowed()){ServerMenuClient.state.getAsJsonObject("map").addProperty("world",original.toString());WorldMapClient.tick();}mc.stop();}
}
