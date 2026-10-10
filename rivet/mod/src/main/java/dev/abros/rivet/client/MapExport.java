package dev.abros.rivet.client;

import dev.abros.rivet.core.map.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.mojang.blaze3d.platform.NativeImage;

/** Bounded export of explored terrain. World access and mutable options are captured before dispatch. */
final class MapExport {
 record Source(MapRepository repository,MapRepository received,Map<MapRepository.Chunk,MapTile> recent){}
 record Area(int x,int z,int width,int height){Area{if(width<1||height<1||width>65536||height>65536)throw new IllegalArgumentException(Client.text("message.invalid_export_area"));}int step(){int step=1;while((Math.max(width,height)+step-1)/step>4096)step*=2;return step;}int pixelsX(){return (width+step()-1)/step();}int pixelsZ(){return (height+step()-1)/step();}}
 static final class Job {volatile int progress;volatile Path file;volatile String error="";volatile boolean done;}
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Rivet map export");t.setDaemon(true);return t;});
 private static Job running;
 static Job export(MapLayer layer,Area area){
  if(running!=null&&!running.done)return running;
  var source=WorldMapClient.exportSource(layer);if(source==null)throw new IllegalStateException(Client.text("ui.the_map_is_still_loading_1cf70aae"));
  var mc=net.minecraft.client.Minecraft.getInstance();var style=MapRenderSettings.INSTANCE.style(layer.dimension());boolean legible=MapCaves.view(layer.dimension()).legible();int top=layer.band()==MapLayer.FULL?MapLayer.FULL:!layer.cave()?MapLayer.SURFACE:layer.equals(MapCaves.layer(layer.dimension()))?MapCaves.top(layer.dimension()):layer.band()*16+15,depth=MapCaves.view(layer.dimension()).depth(),worldHeight=mc.level==null?384:mc.level.dimensionType().logicalHeight();
  var folder=mc.gameDirectory.toPath().resolve("rivet/exports");var job=new Job();running=job;
  IO.execute(()->{Path pending=null;try{
   var known=new HashSet<>(source.repository().chunks(layer));known.addAll(source.received().chunks(layer));known.addAll(source.recent().keySet());
   var tiles=new LinkedHashMap<MapRepository.Chunk,MapTile>(128,.75f,true);var images=new LinkedHashMap<MapRepository.Chunk,int[][]>(128,.75f,true);
   java.util.function.Function<MapRepository.Chunk,MapTile> load=k->{if(!known.contains(k))return null;var recent=source.recent().get(k);if(tiles.containsKey(k))return tiles.get(k);try{var tile=MapTileMerge.merge(recent!=null?recent:source.repository().read(layer,k.x(),k.z()).orElse(null),source.received().read(layer,k.x(),k.z()).orElse(null));tiles.put(k,tile);if(tiles.size()>512)tiles.remove(tiles.keySet().iterator().next());return tile;}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}};
   int step=area.step(),detail=Math.min(4,Integer.numberOfTrailingZeros(step));
   try(var output=new NativeImage(area.pixelsX(),area.pixelsZ(),false)){
    for(int py=0;py<output.getHeight();py++){
     int wz=area.z()+py*step;
     for(int px=0;px<output.getWidth();px++){
      int wx=area.x()+px*step;var key=new MapRepository.Chunk(Math.floorDiv(wx,16),Math.floorDiv(wz,16));var tile=load.apply(key);int color=0;
      if(tile!=null){var levels=images.get(key);if(levels==null){int[] full=new int[256];var north=load.apply(new MapRepository.Chunk(key.x(),key.z()-1));var west=load.apply(new MapRepository.Chunk(key.x()-1,key.z()));var corner=load.apply(new MapRepository.Chunk(key.x()-1,key.z()-1));for(int z=0;z<16;z++)for(int x=0;x<16;x++){int h=tile.groundHeight(x,z);var nt=z==0?north:tile;var nw=x==0?z==0?corner:west:z==0?north:tile;int nh=height(nt,x,z==0?15:z-1,h),nwh=height(nw,x==0?15:x-1,z==0?15:z-1,h);full[z*16+x]=MapColors.render(tile,x,z,nh,nwh,top,depth,worldHeight,legible,style);}levels=MapDetail.pyramid(full);images.put(key,levels);if(images.size()>512)images.remove(images.keySet().iterator().next());}
       int side=16>>detail;color=levels[detail][(Math.floorMod(wz,16)>>detail)*side+(Math.floorMod(wx,16)>>detail)];
      }
      output.setPixelRGBA(px,py,color&0xff00ff00|(color&255)<<16|(color>>>16&255));
     }
     job.progress=(py+1)*100/output.getHeight();
    }
    Files.createDirectories(folder);pending=Files.createTempFile(folder,".map-",".png");output.writeToFile(pending);
    var file=folder.resolve("map-"+java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))+"-"+UUID.randomUUID().toString().substring(0,8)+".png");Files.move(pending,file);job.file=file;
   }
  }catch(Exception e){job.error=Client.text("ui.could_not_export_the_map_38eb0714");com.mojang.logging.LogUtils.getLogger().warn("Map export failed",e);}finally{if(pending!=null)try{Files.deleteIfExists(pending);}catch(java.io.IOException ignored){}job.done=true;}});
  return job;
 }
 private static int height(MapTile tile,int x,int z,int fallback){return tile==null||tile.color(x,z)==0?fallback:tile.groundHeight(x,z);}
 private MapExport(){}
}
