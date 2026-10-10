package dev.abros.rivet.client;
import dev.abros.rivet.core.map.*;
import dev.abros.rivet.core.RetryingWrites;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.nio.file.*;
import java.util.*;
/** Fault injection uses a temporary repository, never the player's map files. */
@EventBusSubscriber(modid="rivet",value=Dist.CLIENT)
public final class MapPersistenceHarness {
 private static int stage;private static long at;private static MapRepository repo;private static Path badIndex,markerFile,tileFile;
 private static MapLayer layer;private static WorldMapClient.TileKey key;private static MapMarker marker;
 private static Object get(String name)throws Exception{var f=WorldMapClient.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 private static void set(String name,Object value)throws Exception{var f=WorldMapClient.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
 private static void check(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){
  if(System.getenv("RIVET_MAP_PERSISTENCE")==null||stage==5)return;var mc=Minecraft.getInstance();
  try{
   if(stage==0){if(!WorldMapClient.ready()||!WorldMapClient.markersReady())return;
    Object world=get("world"),player=get("player");WorldMapClient.reset();Path temp=Files.createTempDirectory("rivet-map-persistence-");repo=new MapRepository(temp,(UUID)world,(UUID)player);set("world",world);set("player",player);set("repository",repo);set("receivedRepository",new MapRepository(temp.resolve("received"),(UUID)world,(UUID)player));
    markerFile=repo.storageDirectory().resolve("markers.json");Files.createDirectories(markerFile.getParent());Files.writeString(markerFile,"{");
    layer=MapLayer.surface(mc.level.dimension().location().toString());var method=MapRepository.class.getDeclaredMethod("tile",MapLayer.class,int.class,int.class);method.setAccessible(true);tileFile=(Path)method.invoke(repo,layer,10000,10000);Files.createDirectories(tileFile.getParent());badIndex=tileFile.resolveSibling("99999999999999999999_0.tile");Files.writeString(badIndex,"");var index=WorldMapClient.class.getDeclaredMethod("index",MapLayer.class);index.setAccessible(true);index.invoke(null,layer);at=System.currentTimeMillis();stage=1;return;
   }
   if(System.currentTimeMillis()-at>30000)throw new IllegalStateException("Persistence scenario timed out at "+stage);
   if(stage==1){if(WorldMapClient.error().isEmpty()||System.currentTimeMillis()-at<1000)return;
    check(!(boolean)get("markersLoading"),"Failed marker read left loading flag set");check(!((Set<?>)get("indexing")).contains(layer),"Failed index left loading flag set");
    Files.delete(badIndex);Files.delete(markerFile);repo.markers(List.of());stage=2;at=System.currentTimeMillis();return;
   }
   if(stage==2){if(!WorldMapClient.markersReady()||!((Set<?>)get("indexed")).contains(layer))return;
    Files.delete(markerFile);Files.createDirectory(markerFile);Files.writeString(markerFile.resolve("block"),"blocked");
    marker=new MapMarker(UUID.randomUUID(),layer.dimension(),"Retry marker",0,64,0,0xff55aa55,"flag",0);WorldMapClient.put(marker);
    Files.createDirectory(tileFile);Files.writeString(tileFile.resolve("block"),"blocked");
    var tiles=(Map<WorldMapClient.TileKey,MapTile>)get("tiles");var dirty=(Set<WorldMapClient.TileKey>)get("dirty");tiles.clear();dirty.clear();key=new WorldMapClient.TileKey(layer,10000,10000);var tile=new MapTile();tile.set(0,0,0xff123456,64);tiles.put(key,tile);dirty.add(key);for(int n=1;n<=8192;n++)tiles.put(new WorldMapClient.TileKey(layer,10000+n,10000),tile);
    var evict=WorldMapClient.class.getDeclaredMethod("evict");evict.setAccessible(true);evict.invoke(null);check(!tiles.containsKey(key),"Test tile was not evicted");tiles.clear();WorldMapClient.flush();stage=3;at=System.currentTimeMillis();return;
   }
   if(stage==3){if(System.currentTimeMillis()-at<1000)return;var keyMethod=WorldMapClient.class.getDeclaredMethod("writeKey",MapRepository.class,Object.class);keyMethod.setAccessible(true);var writes=(RetryingWrites<Object,Object>)get("writes");
    var retryField=RetryingWrites.class.getDeclaredField("retryAt");retryField.setAccessible(true);var retryAt=(Map<?,?>)retryField.get(writes);if(!retryAt.containsKey(keyMethod.invoke(null,repo,"markers"))||!retryAt.containsKey(keyMethod.invoke(null,repo,key)))return;
    check(writes.pending(keyMethod.invoke(null,repo,"markers"))!=null,"Failed marker snapshot discarded");check(writes.pending(keyMethod.invoke(null,repo,key))!=null,"Failed evicted tile discarded");
    Files.delete(markerFile.resolve("block"));Files.delete(markerFile);Files.delete(tileFile.resolve("block"));Files.delete(tileFile);stage=4;at=System.currentTimeMillis();return;
   }
   if(stage==4){if(!Files.isRegularFile(markerFile)||!Files.isRegularFile(tileFile))return;
    check(repo.markers().contains(marker),"Marker did not recover after disk failure");check(repo.read(layer,10000,10000).orElseThrow().color(0,0)==0xff123456,"Evicted terrain did not recover");
    System.out.println("RIVET_MAP_PERSISTENCE_OK marker-read index-read marker-write evicted-tile-write recovery");stage=5;WorldMapClient.reset();mc.stop();
   }
  }catch(Throwable failure){System.out.println("RIVET_MAP_PERSISTENCE_FAILED stage="+stage);failure.printStackTrace();stage=5;WorldMapClient.reset();mc.stop();}
 }
}
