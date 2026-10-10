package dev.abros.rivet.client;

import dev.abros.rivet.core.map.MapRepository;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.*;

/** Opt-in real-client cold loading and chunk-boundary movement regression. */
@EventBusSubscriber(modid="rivet", value=Dist.CLIENT)
public final class MapLoadingHarness {
    private static final String ADDRESS=System.getenv("RIVET_MAP_LOADING_ADDRESS");
    private static boolean connected,done;
    private static int ticks,phase,originX,originZ;
    private static long started,first;
    private static List<WorldMapClient.TileKey> targets;
    private static WorldMapScreen map;
    private static net.minecraft.core.BlockPos editPosition;
    private static net.minecraft.world.level.block.state.BlockState originalState;
    private static int originalHeight;
    private static Object field(String name)throws ReflectiveOperationException{
        var f=WorldMapClient.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);
    }
    private static void cold()throws Exception{
        var mc=Minecraft.getInstance();WorldMapClient.flush();
        var repo=WorldMapClient.class.getDeclaredField("repository");repo.setAccessible(true);
        repo.set(null,new MapRepository(java.nio.file.Files.createTempDirectory("rivet-map-loading-"),UUID.randomUUID(),mc.player.getUUID()));
        for(String name:List.of("tiles","loading","dirty","known","windows","indexing","indexed","retries","changedChunks")){
            var value=field(name);if(value instanceof Map<?,?> values)values.clear();else ((Set<?>)value).clear();
        }
        WorldMapClient.resample();MapTerrainCache.reset();MapRegionTextures.reset();Minimap.invalidate();
        String dimension=mc.level.dimension().location().toString();targets=new ArrayList<>();
        int radius=phase==2?3:6;
        for(int z=-radius;z<=radius;z++)for(int x=-radius;x<=radius;x++){
            int cx=originX+x,cz=originZ+z;if(!mc.level.hasChunk(cx,cz))continue;
            var chunk=mc.level.getChunk(cx,cz);
            if(!chunk.isEmpty())targets.add(new WorldMapClient.TileKey(dimension,cx,cz));
        }
        if(targets.size()<40)throw new IllegalStateException("Too few loaded test chunks: "+targets.size());
        map=new WorldMapScreen(null);mc.setScreen(map);map.view.center(originX*16+8,originZ*16+8);map.view.zoomAt(Math.log(1/map.view.zoom())/Math.log(1.25),0,0,0,0);
        started=System.nanoTime();first=0;ticks=0;
        System.out.println("RIVET_MAP_LOADING_START phase="+phase+" chunks="+targets.size());
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        if(ADDRESS==null||done)return;var mc=Minecraft.getInstance();
        try{
            if(!connected&&mc.screen instanceof TitleScreen){
                connected=true;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.renderDistance().set(6);mc.resizeDisplay();
                var data=new ServerData("Map loading check",ADDRESS,ServerData.Type.OTHER);
                ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(ADDRESS),data,false,null);
            }
            if(!WorldMapClient.ready()||!WorldMapClient.markersReady())return;
            if(phase==0){if(++ticks<100)return;originX=mc.player.getBlockX()>>4;originZ=mc.player.getBlockZ()>>4;phase=1;cold();return;}
            ticks++;
            if(phase>=3){
                var tile=WorldMapClient.loaded(new WorldMapClient.TileKey(mc.level.dimension().location().toString(),editPosition.getX()>>4,editPosition.getZ()>>4));
                int expected=phase==3?editPosition.getY():originalHeight;
                if(tile!=null&&tile.height(editPosition.getX()&15,editPosition.getZ()&15)==expected){
                    if(phase==3){mc.level.setBlock(editPosition,originalState,3);phase=4;ticks=0;}
                    else{done=true;System.out.println("RIVET_MAP_LOADING_OK cold=true moving=true rendered=true blockUpdates=true");mc.stop();}
                }else if(ticks>40)throw new IllegalStateException("Block update did not refresh map in phase "+phase);
                return;
            }
            if(phase==2&&ticks<=80&&ticks%4==0){
                // Cross the same boundary repeatedly; distant chunks must retain their turn.
                mc.player.setPos((originX+(ticks/4%2))*16+8,mc.player.getY(),originZ*16+8);
            }
            int complete=0,rendered=0;
            for(var key:targets){
                if(((Map<?,?>)field("scanned")).containsKey(key))complete++;
                var image=MapTerrainCache.image(key);
                if(image!=null&&Arrays.stream(image.levels()[0]).anyMatch(c->c!=0)){
                    var texture=MapRegionTextures.texture(new MapRegionTextures.Key(key.dimension(),Math.floorDiv(key.x(),4),Math.floorDiv(key.z(),4)),0);
                    if(texture!=null&&texture.level()==0){
                        var pixels=texture.texture().getPixels();boolean matches=pixels!=null;
                        for(int z=0;matches&&z<16;z++)for(int x=0;matches&&x<16;x++){
                            int c=image.levels()[0][z*16+x],rgba=c&0xff00ff00|(c&255)<<16|(c>>>16&255);
                            matches=pixels.getPixelRGBA(Math.floorMod(key.x(),4)*16+x,Math.floorMod(key.z(),4)*16+z)==rgba;
                        }
                        if(matches)rendered++;
                    }
                }
            }
            long elapsed=(System.nanoTime()-started)/1_000_000;
            if(first==0&&rendered>0){first=elapsed;System.out.println("RIVET_MAP_LOADING_FIRST phase="+phase+" ms="+first);}
            if(ticks%20==0)System.out.println("RIVET_MAP_LOADING_PROGRESS phase="+phase+" ms="+elapsed+" sampled="+complete+" rendered="+rendered);
            if(elapsed>15000)throw new IllegalStateException("Loading timed out: "+complete+"/"+targets.size()+", rendered="+rendered);
            if(complete==targets.size()&&rendered==targets.size()&&(phase==1||ticks>80)){
                System.out.println("RIVET_MAP_LOADING_RESULT phase="+phase+" ms="+elapsed+" first="+first+" chunks="+complete);
                var dir=System.getenv("RIVET_MAP_LOADING_SCREENSHOTS");if(dir!=null){var folder=new java.io.File(dir);folder.mkdirs();UiCaptureHarness.grab(folder,"loading-"+phase+".png",mc.getMainRenderTarget(),m->{});}
                if(phase==1){phase=2;cold();}else{
                    int x=originX*16+8,z=originZ*16+8;var tile=WorldMapClient.loaded(new WorldMapClient.TileKey(mc.level.dimension().location().toString(),originX,originZ));
                    originalHeight=tile.height(x&15,z&15);editPosition=new net.minecraft.core.BlockPos(x,originalHeight+4,z);originalState=mc.level.getBlockState(editPosition);
                    mc.level.setBlock(editPosition,net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK.defaultBlockState(),3);phase=3;ticks=0;
                }
            }
        }catch(Throwable ex){if(editPosition!=null&&mc.level!=null)mc.level.setBlock(editPosition,originalState,3);done=true;System.out.println("RIVET_MAP_LOADING_FAILED");ex.printStackTrace();mc.stop();}
    }
}
