package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import dev.abros.rivet.core.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static dev.abros.rivet.api.ApiException.Code.*;

/** Immutable surface snapshots in, atomic PNG pyramid out. Never accesses a Minecraft world. */
public final class TerrainRenderer implements TerrainApi,AutoCloseable {
 private final boolean conservativeOverview;
 private dev.abros.rivet.core.api.material.TerrainMaterials materials;
 private final int size;
 private final TerrainSettings settings;private final Metadata metadata;private final Path root;
 private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{var t=new Thread(r,"Rivet terrain renderer");t.setDaemon(true);return t;});
 private final ConcurrentHashMap<Tile,int[]> updates=new ConcurrentHashMap<>();
 private final Set<CompletableFuture<?>> pending=ConcurrentHashMap.newKeySet();
 private final LinkedHashMap<Path,Boolean> files=new LinkedHashMap<>();
 private final UUID stream=UUID.randomUUID();private final ArrayDeque<Change> changes=new ArrayDeque<>();private long sequence;private volatile boolean closed;
 public TerrainRenderer(Path root,UUID world,TerrainSettings settings)throws IOException{
  this(root,world,settings,16,"surface");
 }
 public TerrainRenderer(Path root,UUID world,TerrainSettings settings,dev.abros.rivet.core.api.material.TerrainMaterials materials)throws IOException{
  this(root,world,settings,materials,"");
 }
 public TerrainRenderer(Path root,UUID world,TerrainSettings settings,dev.abros.rivet.core.api.material.TerrainMaterials materials,String namespace)throws IOException{
  this(root,world,settings,16,(materials==null?"surface":"surface-materials:"+materials.fingerprint())+namespace);this.materials=materials;
 }
 TerrainRenderer(Path root,UUID world,TerrainSettings settings,int size,String profile)throws IOException{
  this.conservativeOverview=size==16||profile.startsWith("surface-geometry");
  this.size=size;this.root=root;this.settings=settings;
  String generation=Hashes.sha256((profile.equals("surface")?settings.profile():settings.profile()+":"+profile).getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0,16);
  metadata=new Metadata(world,generation,settings.dimensions(),size,settings.maxZoom(),settings.minX(),settings.minZ(),settings.maxX(),settings.maxZ());
  Files.createDirectories(root);if(Files.isSymbolicLink(root))throw new IOException("Unsafe terrain directory");
  try(var entries=Files.newDirectoryStream(root,"*.png")){for(var path:entries)if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)&&path.getFileName().toString().matches("[a-f0-9]{16}-[a-f0-9]{16}-[0-6]_-?[0-9]+_-?[0-9]+\\.png")){if(!path.getFileName().toString().startsWith(metadata.generation()+"-")){Files.delete(path);continue;}files.put(path,true);trim();}}
  worker.allowCoreThreadTimeOut(true);
 }
 private void open(){if(closed)throw new ApiException(UNAVAILABLE,"Renderer stopped");}
 public Materials materials(){metadata();return materials==null?TerrainApi.super.materials():new Materials(true,materials.resources(),materials.resolved(),materials.fallback(),materials.diagnostics());}
 public Metadata metadata(){open();return metadata;}
 public synchronized ApiCursor cursor(){open();return new ApiCursor(stream,sequence);}
 public synchronized Changes changes(ApiCursor after){open();if(after==null||!stream.equals(after.stream())||after.sequence()>sequence||after.sequence()<(changes.isEmpty()?sequence:changes.getFirst().cursor().sequence()-1))throw new ApiException(RESYNC_REQUIRED,"Refresh terrain metadata and cursor");return new Changes(changes.stream().filter(e->e.cursor().sequence()>after.sequence()).toList(),new ApiCursor(stream,sequence));}
 private void valid(Tile tile){if(tile==null||!settings.dimensions().contains(tile.dimension())||tile.zoom()<0||tile.zoom()>settings.maxZoom()||Math.abs((long)tile.x())>1875000||Math.abs((long)tile.z())>1875000)throw new ApiException(INVALID_ARGUMENT,"Invalid tile");}
 private Path path(Tile tile){valid(tile);String dim=Hashes.sha256(tile.dimension().getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0,16);return root.resolve(metadata.generation()+"-"+dim+"-"+tile.zoom()+"_"+tile.x()+"_"+tile.z()+".png");}
 private byte[] bytes(Path path)throws IOException{if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new ApiException(NOT_FOUND,"Tile not rendered");if(Files.size(path)>1048576)throw new IOException("Invalid tile size");return Files.readAllBytes(path);}
 public CompletableFuture<Image> tile(Tile tile){return task(()->{byte[] png=bytes(path(tile));return new Image(etag(png),png);});}
 private <T> CompletableFuture<T> task(Callable<T> work){
  var result=new CompletableFuture<T>();if(closed){result.completeExceptionally(new ApiException(UNAVAILABLE,"Renderer stopped"));return result;}
  pending.add(result);result.whenComplete((v,e)->pending.remove(result));
  try{worker.execute(()->{if(result.isDone())return;try{open();result.complete(work.call());}catch(Exception e){result.completeExceptionally(e instanceof ApiException?e:new ApiException(UNAVAILABLE,"Terrain IO failed"));}});}catch(RejectedExecutionException full){result.completeExceptionally(new ApiException(BUSY,"Terrain queue full"));}return result;
 }
 /** Already-captured ARGB pixels, one block per pixel. Hidden coordinates are masked again here. */
 public boolean offer(String dimension,int chunkX,int chunkZ,int[] pixels){
  open();var tile=new Tile(dimension,0,chunkX,chunkZ);valid(tile);if(pixels.length!=256)throw new IllegalArgumentException("Expected chunk pixels");
  int[] copy=pixels.clone();for(int z=0;z<16;z++)for(int x=0;x<16;x++)if(!settings.visible(dimension,chunkX*16+x,chunkZ*16+z))copy[z*16+x]=0;
  synchronized(updates){if(updates.containsKey(tile)){updates.put(tile,copy);return true;}if(updates.size()>=64)return false;updates.put(tile,copy);
   try{worker.execute(()->{int[] next; synchronized(updates){next=updates.remove(tile);}if(next==null||closed)return;try{var image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,16,16,next,0,16);write(tile,image);parents(tile);}catch(IOException ignored){/* Keep the previous complete tile; next capture retries. */}});return true;}catch(RejectedExecutionException full){updates.remove(tile);return false;}}
 }
 /** Explicit jobs receive completion only after the tile and every parent have been written. */
 public CompletableFuture<Void> renderChunk(String dimension,int x,int z,int[] pixels){
  return renderChunk(dimension,x,z,pixels,new String[256],new int[256]);
 }
 public CompletableFuture<Void> renderChunk(String dimension,int x,int z,int[] pixels,String[] states,int[] tints){return renderChunk(dimension,x,z,pixels,states,tints,new int[256]);}
 public CompletableFuture<Void> renderChunk(String dimension,int x,int z,int[] pixels,String[] states,int[] tints,int[] heights){
  if(heights.length!=256)throw new IllegalArgumentException("Invalid heights");int[] ys=heights.clone();
  if(states.length!=256||tints.length!=256)throw new IllegalArgumentException("Invalid surface materials");String[] names=states.clone();int[] tint=tints.clone();
  var tile=new Tile(dimension,0,x,z);valid(tile);if(pixels.length!=256)throw new IllegalArgumentException("Expected chunk pixels");int[] copy=pixels.clone();
  for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++)if(!settings.visible(dimension,x*16+dx,z*16+dz))copy[dz*16+dx]=0;
  return task(()->{if(materials!=null)for(int i=0;i<256;i++)if(copy[i]!=0&&names[i]!=null)copy[i]=materials.surface(names[i],tint[i],copy[i],x*16+i%16,ys[i],z*16+i/16);var image=new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,16,16,copy,0,16);write(tile,image);parents(tile);return null;});
 }
 CompletableFuture<Void> renderImage(Tile tile,BufferedImage image){return task(()->{write(tile,image);parents(tile);return null;});}
 private String etag(byte[] png){return Hashes.sha256((metadata.world()+":"+metadata.generation()+":"+Hashes.sha256(png)).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 private boolean write(Tile tile,BufferedImage image)throws IOException{
  var out=new ByteArrayOutputStream();if(!ImageIO.write(image,"png",out))throw new IOException("PNG encoder unavailable");byte[] data=out.toByteArray();Path target=path(tile);
  if(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)&&Arrays.equals(bytes(target),data))return false;
  Path temp=Files.createTempFile(root,"pending-",".tmp");try{Files.write(temp,data);Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
  files.remove(target);files.put(target,true);trim();
  synchronized(this){if(!closed){changes.addLast(new Change(new ApiCursor(stream,++sequence),tile,etag(data)));while(changes.size()>256)changes.removeFirst();}}return true;
 }
 private void parents(Tile child)throws IOException{
  for(int zoom=child.zoom()+1;zoom<=settings.maxZoom();zoom++){
   var parent=new Tile(child.dimension(),zoom,Math.floorDiv(child.x(),2),Math.floorDiv(child.z(),2));var image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
   for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++){
    Path p=path(new Tile(child.dimension(),zoom-1,parent.x()*2+dx,parent.z()*2+dz));if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))continue;
    BufferedImage source=ImageIO.read(new ByteArrayInputStream(bytes(p)));if(source==null||source.getWidth()!=size||source.getHeight()!=size)throw new IOException("Invalid tile");
    for(int z=0;z<size/2;z++)for(int x=0;x<size/2;x++){
     // Conservative coverage: a masked or unknown source pixel keeps the overview pixel hidden.
     boolean visible=true;
     for(int oz=0;oz<2;oz++)for(int ox=0;ox<2;ox++){int c=source.getRGB(x*2+ox,z*2+oz);visible&=(c>>>24)!=0;}
     if(!conservativeOverview||visible){int alpha=0,r=0,g=0,b=0;for(int oz=0;oz<2;oz++)for(int ox=0;ox<2;ox++){int c=source.getRGB(x*2+ox,z*2+oz),a=c>>>24;alpha+=a;r+=(c>>>16&255)*a;g+=(c>>>8&255)*a;b+=(c&255)*a;}if(alpha>0)image.setRGB(dx*(size/2)+x,dz*(size/2)+z,(alpha/4)<<24|(r/alpha)<<16|(g/alpha)<<8|b/alpha);}
    }
   }
   write(parent,image);child=parent;
  }
 }
 private void trim()throws IOException{while(files.size()>settings.maxTiles()){Path oldest=files.keySet().iterator().next();Files.deleteIfExists(oldest);files.remove(oldest);}}
 @Override public void close(){closed=true;updates.clear();for(var future:pending)future.completeExceptionally(new ApiException(UNAVAILABLE,"Renderer stopped"));worker.shutdownNow();WorkerShutdown.stop(java.time.Duration.ZERO,java.time.Duration.ofSeconds(3),worker);synchronized(this){changes.clear();}}
}
