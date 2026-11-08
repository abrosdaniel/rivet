package dev.abros.rivet.core.map;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.CRC32;

/** Rebuildable accelerator over authoritative tile files. Never reads or changes tile contents. */
public final class MapRegionIndex {
 public static final int SIDE=64;
 private static final Map<Path,java.lang.ref.WeakReference<MapRegionIndex>> OPEN=new WeakHashMap<>();
 public static synchronized MapRegionIndex open(Path source){Path key=source.toAbsolutePath().normalize();var ref=OPEN.get(key);var index=ref==null?null:ref.get();if(index==null){index=new MapRegionIndex(key);OPEN.put(key,new java.lang.ref.WeakReference<>(index));}return index;}
 /** Drop inactive index handles after explicit local terrain cleanup. */
 public static synchronized void forgetTree(Path directory){Path root=directory.toAbsolutePath().normalize();OPEN.keySet().removeIf(path->path.startsWith(root));}
 private final Set<Path> activeMarkers=new HashSet<>();
 private static final int MAGIC=0x52495831,WORDS=64;
 private record Region(int x,int z){}
 private final Path source,root;
 private Path current,building;
 private final LinkedHashMap<Region,BitSet> buildCache=new LinkedHashMap<>(64,.75f,true);
 public MapRegionIndex(Path source){this.source=source;root=source.resolve(".index-v1");}
 private Path pointer(){return root.resolve("current");}
 private static Path page(Path folder,Region region){return folder.resolve(region.x()+"_"+region.z()+".idx");}
 private void loadPointer()throws IOException{
  if(current!=null||!Files.exists(pointer()))return;
  try{if(Files.size(pointer())>40)throw new IOException("Invalid index pointer size");String name=Files.readString(pointer());if(!UUID.fromString(name).toString().equals(name))throw new IllegalArgumentException();
   Path folder=root.resolve(name);if(!Files.isDirectory(folder,LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing index generation");current=folder;
  }catch(IllegalArgumentException bad){throw new IOException("Invalid index generation",bad);}
 }
 public synchronized Path beginningTile()throws IOException{
  try{Files.createDirectories(root);var marker=Files.createTempFile(root,"dirty-",".tmp");activeMarkers.add(marker);return marker;}
  catch(IOException unavailable){if(Files.isRegularFile(root,LinkOption.NOFOLLOW_LINKS)){current=null;return null;}invalidate();return null;}
 }
 public synchronized void finishedTile(Path marker)throws IOException{Files.deleteIfExists(marker);activeMarkers.remove(marker);}
 private boolean dirty()throws IOException{if(!Files.isDirectory(root))return false;try(var files=Files.newDirectoryStream(root,"dirty-*.tmp")){return files.iterator().hasNext();}}
 public synchronized void invalidate()throws IOException{current=null;Files.deleteIfExists(pointer());}
 /** Only one builder per repository/layer. Cancellation leaves source data and prior generation untouched. */
 public void prepare(BooleanSupplier cancelled)throws IOException{
  synchronized(this){if(current==null&&dirty())invalidate();try{loadPointer();}catch(IOException corrupt){invalidate();}if(current!=null)return;if(building!=null)throw new IOException("Index rebuild already running");
   Files.createDirectories(root);building=root.resolve(UUID.randomUUID().toString());Files.createDirectory(building);
  }
  boolean complete=false;
  try{
   try(var files=Files.newDirectoryStream(source,"*.tile")){
    for(Path path:files){if(cancelled.getAsBoolean())throw new java.io.InterruptedIOException("Map index rebuild cancelled");
     var chunk=parse(path.getFileName().toString());if(chunk==null||!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))continue;
     synchronized(this){addBuilding(chunk.x(),chunk.z());}
    }
   }
   synchronized(this){if(cancelled.getAsBoolean())throw new java.io.InterruptedIOException("Map index rebuild cancelled");
    for(var entry:buildCache.entrySet())write(page(building,entry.getKey()),entry.getValue());buildCache.clear();
    Path pending=Files.createTempFile(root,".current-",".tmp");try{Files.writeString(pending,building.getFileName().toString());replace(pending,pointer());}finally{Files.deleteIfExists(pending);}
    current=building;complete=true;
    try(var markers=Files.newDirectoryStream(root,"dirty-*.tmp")){for(var marker:markers)if(!activeMarkers.contains(marker))Files.deleteIfExists(marker);}
   }
  }finally{Path abandoned;synchronized(this){abandoned=building;building=null;buildCache.clear();}if(!complete)removeGeneration(abandoned);}
  // Old index generations are disposable; incomplete rebuilds never become authoritative.
  synchronized(this){try(var folders=Files.newDirectoryStream(root)){for(var folder:folders){if(folder.equals(current))continue;try{UUID.fromString(folder.getFileName().toString());removeGeneration(folder);}catch(IllegalArgumentException ignored){}}}}
 }
 private void addBuilding(int x,int z)throws IOException{
  var region=new Region(Math.floorDiv(x,SIDE),Math.floorDiv(z,SIDE));var bits=buildCache.get(region);
  if(bits==null){bits=read(page(building,region));buildCache.put(region,bits);}
  bits.set(Math.floorMod(z,SIDE)*SIDE+Math.floorMod(x,SIDE));
  if(buildCache.size()>64){var oldest=buildCache.entrySet().iterator().next();write(page(building,oldest.getKey()),oldest.getValue());buildCache.remove(oldest.getKey());}
 }
 /** Call after an authoritative tile commit. Concurrent rebuilds include newly written tiles. */
 public synchronized void added(int x,int z)throws IOException{
  loadPointer();if(building!=null)addBuilding(x,z);
  if(current!=null){var region=new Region(Math.floorDiv(x,SIDE),Math.floorDiv(z,SIDE));var file=page(current,region);var bits=read(file);int bit=Math.floorMod(z,SIDE)*SIDE+Math.floorMod(x,SIDE);if(!bits.get(bit)){bits.set(bit);write(file,bits);}}
 }
 public synchronized BitSet region(int x,int z)throws IOException{
  loadPointer();if(current==null)throw new IOException("Map index not ready");
  if(!Files.isDirectory(current,LinkOption.NOFOLLOW_LINKS)){invalidate();throw new IOException("Missing map index generation");}
  try{return read(page(current,new Region(x,z)));}catch(IOException corrupt){invalidate();throw corrupt;}
 }
 static MapRepository.Chunk parse(String name){
  if(!name.endsWith(".tile"))return null;int split=name.indexOf('_');if(split<1)return null;
  try{return new MapRepository.Chunk(Integer.parseInt(name.substring(0,split)),Integer.parseInt(name.substring(split+1,name.length()-5)));}catch(NumberFormatException bad){return null;}
 }
 private static BitSet read(Path file)throws IOException{
  if(!Files.exists(file)){if(Files.exists(file.resolveSibling(file.getFileName()+".present")))throw new IOException("Missing map index page");return new BitSet(SIDE*SIDE);}if(Files.isSymbolicLink(file)||Files.size(file)!=4+WORDS*8+8)throw new IOException("Invalid map index page");
  byte[] data=Files.readAllBytes(file);var crc=new CRC32();crc.update(data,0,4+WORDS*8);
  try(var in=new DataInputStream(new ByteArrayInputStream(data))){if(in.readInt()!=MAGIC)throw new IOException("Invalid map index version");long[] words=new long[WORDS];for(int n=0;n<WORDS;n++)words[n]=in.readLong();if(in.readLong()!=crc.getValue())throw new IOException("Map index checksum mismatch");return BitSet.valueOf(words);}
 }
 private static void write(Path file,BitSet bits)throws IOException{
  var bytes=new ByteArrayOutputStream();try(var out=new DataOutputStream(bytes)){out.writeInt(MAGIC);long[] words=bits.toLongArray();for(int n=0;n<WORDS;n++)out.writeLong(n<words.length?words[n]:0);out.flush();var crc=new CRC32();crc.update(bytes.toByteArray());out.writeLong(crc.getValue());}
  Path present=file.resolveSibling(file.getFileName()+".present");if(!Files.exists(present))Files.createFile(present);
  Path temp=Files.createTempFile(file.getParent(),".page-",".tmp");try{Files.write(temp,bytes.toByteArray());replace(temp,file);}finally{Files.deleteIfExists(temp);}
 }
 private static void replace(Path from,Path to)throws IOException{try{Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException unsupported){Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);}}
 private static void removeGeneration(Path folder)throws IOException{if(folder==null||!Files.isDirectory(folder,LinkOption.NOFOLLOW_LINKS))return;try(var files=Files.newDirectoryStream(folder)){for(var file:files)Files.deleteIfExists(file);}Files.deleteIfExists(folder);}
}
