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
 private static int phase=-1,ticks,checks;private static long deadline,started;private static boolean done;
 private static final AtomicBoolean prepared=new AtomicBoolean();private static volatile Throwable preparationFailure;
 private static final Map<MapRegionTextures.Key,MapRegionTextures.Texture> retained=new HashMap<>();
 private static WorldMapScreen map;private static boolean edited;
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
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){
  if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();
  try{
   if(phase==-1){if(!(mc.screen instanceof TitleScreen))return;deadline=System.currentTimeMillis()+180000;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.resizeDisplay();ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),new ServerData("Map terrain test",ADDRESS,ServerData.Type.OTHER),false,null);phase=0;return;}
   if(System.currentTimeMillis()>deadline)throw new IllegalStateException("Terrain timeout phase="+phase);
   if(!WorldMapClient.ready())return;ticks++;
   if(phase==0){if(ticks<100)return;WorldMapClient.flush();var repo=new MapRepository(java.nio.file.Files.createTempDirectory("rivet-terrain-test-"),UUID.randomUUID(),mc.player.getUUID());set(WorldMapClient.class,"repository",repo);
    MapTerrainCache.reset();MapRegionTextures.reset();
    MapTerrainCache.work(()->{try{for(int cz=MIN;cz<MAX;cz++)for(int cx=MIN;cx<MAX;cx++){var tile=new MapTile();for(int z=0;z<16;z++)for(int x=0;x<16;x++)tile.set(x,z,color(cx*16+x,cz*16+z),height(cx*16+x,cz*16+z));repo.write(DIM,cx,cz,tile);}}catch(Throwable ex){preparationFailure=ex;}finally{prepared.set(true);}});phase=1;
   }else if(phase==1){if(!prepared.get())return;if(preparationFailure!=null)throw new IllegalStateException(preparationFailure);
    var known=new HashSet<MapRepository.Chunk>();for(int z=MIN;z<MAX;z++)for(int x=MIN;x<MAX;x++)known.add(new MapRepository.Chunk(x,z));
    ((Map<MapLayer,Set<MapRepository.Chunk>>)field(WorldMapClient.class,"known")).put(MapLayer.surface(DIM),known);((Set<MapLayer>)field(WorldMapClient.class,"indexed")).add(MapLayer.surface(DIM));((Set<MapLayer>)field(WorldMapClient.class,"indexing")).add(MapLayer.surface(DIM));
    map=new WorldMapScreen(null);mc.setScreen(map);var dim=WorldMapScreen.class.getDeclaredField("dimension");dim.setAccessible(true);dim.set(map,DIM);map.view.center(256,256);map.view.zoomAt(-30,0,0,0,0);started=System.nanoTime();phase=2;ticks=0;
    System.out.println("RIVET_TERRAIN_COLD_START chunks="+known.size());
   }else if(phase==2){if(ticks%20==0)System.out.println("RIVET_TERRAIN_PROGRESS overview="+((Map<?,?>)field(MapTerrainCache.class,"overview")).size()+" textures="+((Map<?,?>)field(MapRegionTextures.class,"textures")).size());
    if(!settled())return;check(((Map<?,?>)field(MapTerrainCache.class,"images")).size()<=8192,"Full-resolution cache unbounded");check(((Map<?,?>)field(MapTerrainCache.class,"overview")).size()>=9216,"Overview lost evicted tiles");
    for(int z=-1;z<=1;z++)for(int x=-1;x<=1;x++){var key=new MapRegionTextures.Key(DIM,x,z,3);retained.put(key,MapRegionTextures.texture(key,3));}
    System.out.println("RIVET_TERRAIN_COLD_OK ms="+(System.nanoTime()-started)/1000000+" chunks=9216 exactPixels=36864");phase=3;ticks=0;
   }else if(phase==3){if(ticks<20)return;check(settled(),"Overview changed after settling");for(var entry:retained.entrySet())check(MapRegionTextures.texture(entry.getKey(),3).texture()==entry.getValue().texture(),"Stationary view rebuilt texture");mc.setScreen(null);mc.setScreen(map);phase=4;ticks=0;
   }else if(phase==4){check(settled(),"Reopening map lost terrain");for(var entry:retained.entrySet())check(MapRegionTextures.texture(entry.getKey(),3).texture()==entry.getValue().texture(),"Reopening discarded GPU cache");map.view.zoomAt(30,0,0,0,0);phase=5;ticks=0;
   }else if(phase==5){var key=new MapRegionTextures.Key(DIM,0,0);var texture=MapRegionTextures.texture(key,0);if(texture==null)return;var pixels=texture.texture().getPixels();for(int z=0;z<64;z++)for(int x=0;x<64;x++){int c=expected(x,z),rgba=c&0xff00ff00|(c&255)<<16|(c>>>16&255);if(pixels.getPixelRGBA(x,z)!=rgba)return;}check(pixels.getWidth()==64,"Detailed texture size wrong");map.view.zoomAt(-30,0,0,0,0);phase=6;}
   else if(phase==6){check(settled(),"Returning to overview lost pixels");edited=true;var tile=new MapTile();for(int z=0;z<16;z++)for(int x=0;x<16;x++)tile.set(x,z,color(496+x,496+z),height(496+x,496+z));var key=new WorldMapClient.TileKey(DIM,31,31);((Map<WorldMapClient.TileKey,MapTile>)field(WorldMapClient.class,"tiles")).put(key,tile);MapTerrainCache.changed(key);phase=7;ticks=0;}
   else if(phase==7){if(!settled())return;check(true,"Overview update");System.out.println("RIVET_TERRAIN_OK checks="+checks+" disk=true eviction=true seams=true reopen=true lod=true borderUpdates=true");done=true;mc.stop();}
  }catch(Throwable ex){done=true;System.out.println("RIVET_TERRAIN_FAILED phase="+phase);ex.printStackTrace();mc.stop();}
 }
}
