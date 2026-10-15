package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import dev.abros.rivet.core.WorkerShutdown;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.zip.*;

/** Orthographic surface/isometric mesh renderer. Masked chunk inputs precede screen-tile clipping. */
public final class IsometricRenderer implements TerrainApi,AutoCloseable {
 private record Key(String dimension,int x,int z){}
 private record Bounds(int minY,int height){int maxY(){return minY+height-1;}}
 private record Snapshot(Bounds bounds,int[] data,int[] ids,int[] tints,List<String> names){String state(int x,int y,int z){int dy=y-bounds.minY();if(dy<0||dy>=bounds.height())return null;int i=dy*256+Math.floorMod(z,16)*16+Math.floorMod(x,16);return data[i]==0||ids[i]==0?null:names.get(ids[i]-1);}}
 private final dev.abros.rivet.core.api.material.TerrainMaterials materials;private final int scale;private final boolean topDown;
 private final TerrainSettings settings;private final TerrainRenderer tiles;private final Path root;
 private final Map<Key,Bounds> index=new LinkedHashMap<>();
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{var t=new Thread(r,"Rivet isometric renderer");t.setDaemon(true);return t;});
 private final Set<CompletableFuture<Void>> pending=ConcurrentHashMap.newKeySet();private volatile boolean closed;
 public IsometricRenderer(Path root,UUID world,TerrainSettings settings)throws IOException{
  this(root,world,settings,null);
 }
 public IsometricRenderer(Path root,UUID world,TerrainSettings settings,dev.abros.rivet.core.api.material.TerrainMaterials materials)throws IOException{
  this(root,world,settings,materials,"");
 }
 public IsometricRenderer(Path root,UUID world,TerrainSettings settings,dev.abros.rivet.core.api.material.TerrainMaterials materials,String namespace)throws IOException{
  this(root,world,settings,materials,namespace,false);
 }
 public IsometricRenderer(Path root,UUID world,TerrainSettings settings,dev.abros.rivet.core.api.material.TerrainMaterials materials,String namespace,boolean topDown)throws IOException{
  this.topDown=topDown;this.materials=materials;this.scale=materials==null?1:4;
  if(Files.isSymbolicLink(root))throw new IOException("Unsafe isometric root");
  this.settings=settings;tiles=new TerrainRenderer(root.resolve("tiles"),world,settings,256,(topDown?"surface-geometry-v1:":"isometric-snapshot-v3:")+(materials==null?"palette":materials.fingerprint())+namespace);this.root=root.resolve(tiles.metadata().generation());Files.createDirectories(this.root);pruneOldInputs(root,tiles.metadata().generation());if(Files.isSymbolicLink(this.root))throw new IOException("Unsafe isometric cache");
  for(String dimension:settings.dimensions()){
   Path dir=directory(dimension);if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))continue;
   try(var entries=Files.newDirectoryStream(dir,"*.vox")){for(var path:entries){if(index.size()>=settings.maxTiles()){if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&path.getFileName().toString().matches("-?[0-9]+_-?[0-9]+\\.vox"))Files.delete(path);continue;}var parts=path.getFileName().toString().replace(".vox","").split("_");try{int x=Integer.parseInt(parts[0]),z=Integer.parseInt(parts[1]);if(parts.length!=2||!allowed(x,z))continue;try(var in=input(path)){int min=in.readInt(),height=in.readInt();validate(min,height);index.put(new Key(dimension,x,z),new Bounds(min,height));}}catch(RuntimeException|IOException invalid){/* Invalid derived inputs are rebuilt by capture. */}}}
  }worker.allowCoreThreadTimeOut(true);
 }
 private static void pruneOldInputs(Path base,String current)throws IOException{
  try(var generations=Files.newDirectoryStream(base)){for(var gen:generations){if(gen.getFileName().toString().equals(current)||!gen.getFileName().toString().matches("[a-f0-9]{16}")||!Files.isDirectory(gen,LinkOption.NOFOLLOW_LINKS))continue;
   try(var dimensions=Files.newDirectoryStream(gen)){for(var dim:dimensions){if(!dim.getFileName().toString().matches("[a-f0-9]{16}")||!Files.isDirectory(dim,LinkOption.NOFOLLOW_LINKS))continue;
    try(var files=Files.newDirectoryStream(dim)){for(var f:files)if(Files.isRegularFile(f,LinkOption.NOFOLLOW_LINKS)&&(f.getFileName().toString().matches("-?[0-9]+_-?[0-9]+\\.vox")||f.getFileName().toString().matches("voxel-.*\\.tmp")))Files.delete(f);}
    try{Files.delete(dim);}catch(DirectoryNotEmptyException retained){}
   }}try{Files.delete(gen);}catch(DirectoryNotEmptyException retained){}
  }}
 }
 private Path directory(String dimension){return root.resolve(dev.abros.rivet.core.Hashes.sha256(dimension.getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0,16));}
 private Path path(Key key){return directory(key.dimension()).resolve(key.x()+"_"+key.z()+".vox");}
 private static DataInputStream input(Path path)throws IOException{if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing voxel cache");return new DataInputStream(new GZIPInputStream(Files.newInputStream(path)));}
 private static void validate(int min,int height){if(min< -2048||height<16||height>4096||min+height>2048)throw new IllegalArgumentException("Invalid vertical extent");}
 private boolean allowed(int x,int z){return x>=Math.floorDiv(settings.minX(),16)&&x<=Math.floorDiv(settings.maxX(),16)&&z>=Math.floorDiv(settings.minZ(),16)&&z<=Math.floorDiv(settings.maxZ(),16);}
 public Materials materials(){metadata();return materials==null?TerrainApi.super.materials():new Materials(true,materials.resources(),materials.resolved(),materials.fallback(),materials.diagnostics());}
 public Metadata metadata(){return tiles.metadata();}public Projection projection(){if(topDown)return new Projection("surface","16*x","16*z",256);return new Projection("isometric",scale==1?"2*(x-z)":"8*(x-z)",scale==1?"x+z-2*y":"4*(x+z)-8*y",256);}public ApiCursor cursor(){return tiles.cursor();}public Changes changes(ApiCursor after){return tiles.changes(after);}public CompletableFuture<Image> tile(Tile tile){return tiles.tile(tile);}
 /** Dense y-major ARGB cube; masked columns are discarded before persistence or projection. */
 public CompletableFuture<Void> render(String dimension,int x,int z,int minY,int height,int[] colors){
  return render(dimension,x,z,minY,height,colors,List.of(),new int[colors.length],new int[colors.length]);
 }
 public CompletableFuture<Void> render(String dimension,int x,int z,int minY,int height,int[] colors,List<String> palette,int[] modelIds,int[] tints){
  validate(minY,height);if(palette.size()>4096||palette.stream().anyMatch(v->v.length()>2048)||modelIds.length!=colors.length||tints.length!=colors.length)throw new IllegalArgumentException("Invalid material snapshot");
  for(int id:modelIds)if(id<0||id>palette.size())throw new IllegalArgumentException("Invalid model index");
  validate(minY,height);if(!settings.dimensions().contains(dimension)||!allowed(x,z)||colors.length!=height*256)throw new IllegalArgumentException("Invalid voxel snapshot");
  var future=new CompletableFuture<Void>();if(closed)return CompletableFuture.failedFuture(new ApiException(ApiException.Code.UNAVAILABLE,"Renderer stopped"));
  int[] copy=colors.clone(),ids=modelIds.clone(),tint=tints.clone();var names=new ArrayList<String>();for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++)if(!settings.visible(dimension,x*16+dx,z*16+dz))for(int y=0;y<height;y++){int i=y*256+dz*16+dx;copy[i]=0;ids[i]=0;tint[i]=0;}
  int[] remap=new int[palette.size()+1];for(int i=0;i<ids.length;i++){if((copy[i]>>>24)==0){ids[i]=0;continue;}int id=ids[i];if(id!=0){if(remap[id]==0){names.add(palette.get(id-1));remap[id]=names.size();}ids[i]=remap[id];}}
  pending.add(future);future.whenComplete((v,e)->pending.remove(future));
  try{worker.execute(()->{try{if(closed||future.isDone())return;var key=new Key(dimension,x,z);int first=0,last=height-1;while(first<height-16&&empty(copy,first))first++;while(last>first+15&&empty(copy,last))last--;var bounds=new Bounds(minY+first,last-first+1);var old=index.get(key);
   if(old==null&&index.size()>=settings.maxTiles())throw new ApiException(ApiException.Code.BUSY,"Isometric input cache limit");
   if(Files.isSymbolicLink(directory(dimension)))throw new IOException("Unsafe input directory");Files.createDirectories(directory(dimension));Path temp=Files.createTempFile(directory(dimension),"voxel-",".tmp");try{try(var out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temp)))){out.writeInt(bounds.minY());out.writeInt(bounds.height());out.writeInt(names.size());for(String name:names)out.writeUTF(name);for(int i=first*256;i<(last+1)*256;i++){out.writeInt(copy[i]);out.writeInt(ids[i]);out.writeInt(tint[i]);}}Files.move(temp,path(key),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
   index.put(key,bounds);var affected=new HashSet<Tile>();affected(key,bounds,affected);if(old!=null)affected(key,old,affected);
   for(var tile:affected){if(closed||Thread.currentThread().isInterrupted())throw new InterruptedException();tiles.renderImage(tile,raster(tile)).get(30,TimeUnit.SECONDS);}future.complete(null);
  }catch(Exception failure){future.completeExceptionally(failure instanceof ApiException?failure:new ApiException(ApiException.Code.UNAVAILABLE,"Isometric render failed"));}});}catch(RejectedExecutionException busy){future.completeExceptionally(new ApiException(ApiException.Code.BUSY,"Isometric queue full"));}return future;
 }
 private static boolean empty(int[] colors,int y){for(int i=y*256;i<(y+1)*256;i++)if((colors[i]>>>24)!=0)return false;return true;}
 private void affected(Key k,Bounds b,Set<Tile> out){if(topDown){for(int x=k.x()-1;x<=k.x()+1;x++)for(int z=k.z()-1;z<=k.z()+1;z++)if(allowed(x,z))out.add(new Tile(k.dimension(),0,x,z));return;}int u=scale*32*(k.x()-k.z()),v=scale*16*(k.x()+k.z()),pad=materials==null?0:16;for(int tx=Math.floorDiv(u-scale*32-pad,256);tx<=Math.floorDiv(u+scale*32+pad,256);tx++)for(int tz=Math.floorDiv(v-scale*(2*(b.maxY()+1)+2)-pad,256);tz<=Math.floorDiv(v+scale*(32-2*b.minY()+2)+pad,256);tz++)out.add(new Tile(k.dimension(),0,tx,tz));}
 private Snapshot snapshot(Key k,Bounds b)throws IOException{
   int[] data=new int[b.height()*256],ids=new int[data.length],tints=new int[data.length];var names=new ArrayList<String>();try(var in=input(path(k))){if(in.readInt()!=b.minY()||in.readInt()!=b.height())throw new IOException("Voxel header mismatch");int count=in.readInt();if(count<0||count>4096)throw new IOException("Palette limit");for(int n=0;n<count;n++){String name=in.readUTF();if(name.length()>2048)throw new IOException("State limit");names.add(name);}for(int i=0;i<data.length;i++){data[i]=in.readInt();ids[i]=in.readInt();tints[i]=in.readInt();if(ids[i]<0||ids[i]>count)throw new IOException("Invalid palette index");}if(in.read()!=-1)throw new IOException("Oversized voxel cache");}
  return new Snapshot(b,data,ids,tints,List.copyOf(names));
 }
 private Iterable<Map.Entry<Key,Bounds>> candidates(Tile tile){
  if(!topDown)return index.entrySet();var candidates=new ArrayList<Map.Entry<Key,Bounds>>(9);
  for(int z=tile.z()-1;z<=tile.z()+1;z++)for(int x=tile.x()-1;x<=tile.x()+1;x++){var key=new Key(tile.dimension(),x,z);var bounds=index.get(key);if(bounds!=null)candidates.add(Map.entry(key,bounds));}return candidates;
 }
 private BufferedImage raster(Tile tile)throws IOException{
  int left=tile.x()*256,top=tile.z()*256;var textured=new dev.abros.rivet.core.api.material.ModelRaster(256,left,top,topDown,scale/4.);int faceCount=0;var fallbackModels=new HashMap<Integer,dev.abros.rivet.api.TerrainMaterialProvider.Model>();var solidModels=new IdentityHashMap<dev.abros.rivet.api.TerrainMaterialProvider.Model,Boolean>();
  for(var entry:candidates(tile)){if(Thread.currentThread().isInterrupted())throw new IOException("Renderer stopped");var k=entry.getKey();var b=entry.getValue();if(!k.dimension().equals(tile.dimension()))continue;int u=scale*32*(k.x()-k.z()),v=scale*16*(k.x()+k.z());if(topDown?Math.abs(k.x()-tile.x())>1||Math.abs(k.z()-tile.z())>1:u+scale*32+16<left||u-scale*32-16>=left+256||v+scale*(32-2*b.minY()+2)+16<top||v-scale*(2*(b.maxY()+1)+2)-16>=top+256)continue;
   var snapshot=snapshot(k,b);int[] data=snapshot.data(),ids=snapshot.ids(),tints=snapshot.tints();var names=snapshot.names();var neighbors=new HashMap<Key,Snapshot>();neighbors.put(k,snapshot);
   for(int y=0;y<b.height();y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){int c=data[y*256+z*16+x];if((c>>>24)==0)continue;int wx=k.x()*16+x,wz=k.z()*16+z,wy=b.minY()+y,px=scale*2*(wx-wz),py=scale*(wx+wz-2*wy);if(topDown){px=16*wx;py=16*wz;}int pad=32;if(px+pad<left||px-pad>=left+256||py+pad<top||py-pad>=top+256)continue;
    {int i=y*256+z*16+x;String state=ids[i]==0?null:names.get(ids[i]-1);
     if(dev.abros.rivet.core.api.material.FluidGeometry.fluid(state)!=null){dev.abros.rivet.core.api.material.FluidGeometry.States sampler=(dx,dy,dz)->{
      int xx=wx+dx,zz=wz+dz;var key=new Key(k.dimension(),Math.floorDiv(xx,16),Math.floorDiv(zz,16));if(!index.containsKey(key))return null;
      var input=neighbors.get(key);if(input==null){try{input=snapshot(key,index.get(key));neighbors.put(key,input);}catch(IOException invalid){throw new java.io.UncheckedIOException(invalid);}}return input.state(xx,wy+dy,zz);
     };
     var fluid=materials==null?dev.abros.rivet.core.api.material.FluidGeometry.model(state,sampler,new dev.abros.rivet.api.TerrainMaterialProvider.Texture(1,1,new int[]{c}),new dev.abros.rivet.api.TerrainMaterialProvider.Texture(1,1,new int[]{c})):materials.fluid(state,sampler);
     textured.block(fluid,wx,wy,wz,materials==null?0xffffff:tints[i]);faceCount+=fluid.quads().size();if(faceCount>1500000)throw new IOException("Model scene limit");if(dev.abros.rivet.core.api.material.FluidGeometry.pure(state))continue;
     }
var model=materials==null||ids[i]==0?java.util.Optional.<dev.abros.rivet.api.TerrainMaterialProvider.Model>empty():materials.model(names.get(ids[i]-1),wx,wy,wz);if(solidModels.size()>4096)solidModels.clear();boolean solid=model.isEmpty()?(c>>>24)==255:solidModels.computeIfAbsent(model.get(),dev.abros.rivet.core.api.material.ModelRaster::opaqueCube);
     if(solid){boolean surrounded=topDown?y+1<b.height():x>0&&x<15&&z>0&&z<15&&y>0&&y+1<b.height();
      if(surrounded)for(int[] offset:topDown?new int[][]{{0,1,0}}:new int[][]{{-1,0,0},{1,0,0},{0,0,-1},{0,0,1},{0,-1,0},{0,1,0}}){int j=i+offset[0]+offset[1]*256+offset[2]*16;if((data[j]>>>24)!=255){surrounded=false;break;}var neighbor=materials==null||ids[j]==0?Optional.<dev.abros.rivet.api.TerrainMaterialProvider.Model>empty():materials.model(names.get(ids[j]-1),wx+offset[0],wy+offset[1],wz+offset[2]);if(neighbor.isPresent()&&!solidModels.computeIfAbsent(neighbor.get(),dev.abros.rivet.core.api.material.ModelRaster::opaqueCube)){surrounded=false;break;}}
      if(surrounded)continue;
     }
     if(fallbackModels.size()>256)fallbackModels.clear();var mesh=model.orElseGet(()->fallbackModels.computeIfAbsent(c,dev.abros.rivet.core.api.material.ModelRaster::cube));faceCount+=mesh.quads().size();if(faceCount>1500000)throw new IOException("Model scene limit");textured.block(mesh,wx,wy,wz,tints[i]);continue;}
   }
  }
  var result=textured.image();if(topDown)for(int z=0;z<256;z++)for(int x=0;x<256;x++)if(!settings.visible(tile.dimension(),tile.x()*16+x/16,tile.z()*16+z/16))result.setRGB(x,z,0);return result;
 }
 @Override public void close(){closed=true;for(var f:pending)f.completeExceptionally(new ApiException(ApiException.Code.UNAVAILABLE,"Renderer stopped"));worker.shutdownNow();WorkerShutdown.stop(java.time.Duration.ZERO,java.time.Duration.ofSeconds(3),worker);tiles.close();}
}
