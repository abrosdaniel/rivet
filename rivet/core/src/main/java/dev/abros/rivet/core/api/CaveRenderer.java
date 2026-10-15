package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import dev.abros.rivet.core.WorkerShutdown;
import dev.abros.rivet.core.api.material.TerrainMaterials;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Topmost floor under an opaque roof within an explicit band. Never persists full underground snapshots. */
public final class CaveRenderer implements TerrainApi,AutoCloseable {
 public static final byte AIR=1,ROOF=2;
 private final TerrainRenderer tiles;private final TerrainSettings settings;private final Slice slice;
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),r->{var t=new Thread(r,"Rivet cave renderer");t.setDaemon(true);return t;});
 private final Set<CompletableFuture<Void>> pending=ConcurrentHashMap.newKeySet();private volatile boolean closed;
 public CaveRenderer(Path root,UUID world,TerrainSettings settings,TerrainProfiles.Spec spec,TerrainMaterials materials)throws java.io.IOException{
  if(!spec.projection().equals("cave"))throw new IllegalArgumentException("Cave profile required");this.settings=settings;slice=new Slice(spec.minY(),spec.maxY());
  tiles=new TerrainRenderer(root,world,settings,materials,":private:"+spec.id()+":cave-v1:"+slice.minY()+":"+slice.maxY());worker.allowCoreThreadTimeOut(true);
 }
 public Metadata metadata(){return tiles.metadata();}public Materials materials(){return tiles.materials();}public Optional<Slice> slice(){return Optional.of(slice);}public Projection projection(){return new Projection("cave","x","z",16);}
 public ApiCursor cursor(){return tiles.cursor();}public Changes changes(ApiCursor c){return tiles.changes(c);}public CompletableFuture<Image> tile(Tile t){return tiles.tile(t);}
 public CompletableFuture<Void> render(String dimension,int x,int z,int minY,int height,int[] colors,List<String> palette,int[] models,int[] tints,byte[] spaces){
  if(height<16||height>4096||minY< -2048||(long)minY+height>2048||colors==null||colors.length!=height*256||models.length!=colors.length||tints.length!=colors.length||spaces==null||spaces.length!=colors.length||palette.size()>4096)throw new IllegalArgumentException("Invalid cave snapshot");
  int low=Math.max(minY,slice.minY()),high=Math.min(minY+height-1,slice.maxY());int first=Math.max(0,(low-minY)*256),end=Math.max(first,(high-minY+1)*256);if(high<low){first=0;end=0;}
  int[] pixels=Arrays.copyOfRange(colors,first,end),ids=Arrays.copyOfRange(models,first,end),tint=Arrays.copyOfRange(tints,first,end);byte[] flags=Arrays.copyOfRange(spaces,first,end);var names=List.copyOf(palette);
  var result=new CompletableFuture<Void>();if(closed)return CompletableFuture.failedFuture(new ApiException(ApiException.Code.UNAVAILABLE,"Cave renderer stopped"));pending.add(result);result.whenComplete((v,e)->pending.remove(result));
  try{worker.execute(()->{try{
   if(closed)throw new ApiException(ApiException.Code.UNAVAILABLE,"Cave renderer stopped");int[] floor=new int[256],floorTints=new int[256],heights=new int[256];String[] states=new String[256];
   for(int column=0;column<256;column++){
    if(!settings.visible(dimension,x*16+column%16,z*16+column/16))continue;
    boolean roof=false;int air=0;
    for(int y=flags.length/256-1;y>=0;y--){int i=y*256+column;byte f=flags[i];
     if(f==AIR){if(roof)air++;continue;}
     if(roof&&air>=2){if(pixels[i]!=0){heights[column]=low+y;floor[column]=pixels[i]|0xff000000;int id=ids[i];if(id<0||id>names.size())throw new IllegalArgumentException("Invalid cave material");if(id>0)states[column]=names.get(id-1);floorTints[column]=0xff000000|(tint[i]&0xffffff);}break;}
     air=0;if(f==ROOF)roof=true;
    }
   }
   tiles.renderChunk(dimension,x,z,floor,states,floorTints,heights).get(30,TimeUnit.SECONDS);result.complete(null);
  }catch(Exception failure){result.completeExceptionally(failure instanceof ApiException?failure:new ApiException(ApiException.Code.UNAVAILABLE,"Cave render failed"));}});}catch(RejectedExecutionException busy){result.completeExceptionally(new ApiException(ApiException.Code.BUSY,"Cave queue full"));}return result;
 }
 public void close(){closed=true;for(var future:pending)future.completeExceptionally(new ApiException(ApiException.Code.UNAVAILABLE,"Cave renderer stopped"));worker.shutdownNow();tiles.close();WorkerShutdown.stop(java.time.Duration.ZERO,java.time.Duration.ofSeconds(3),worker);}
}
