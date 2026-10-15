package dev.abros.rivet.server;

import dev.abros.rivet.core.api.TerrainSettings;
import dev.abros.rivet.core.api.TerrainJournal.Cursor;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/** Streaming directory discovery; chunk bytes are read by the world's own serialized IO worker. */
final class SavedTerrainSource implements AutoCloseable {
 record Result(ServerLevel level,int x,int z,SavedTerrainSnapshot snapshot,int skipped,boolean done){}
 private static final Pattern REGION=Pattern.compile("r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca");
 private final List<ServerLevel> levels;private final Path world;private final TerrainSettings settings;
 private int dimension=-1,rx,rz,slot=1024;private DirectoryStream<Path> directory;private Iterator<Path> entries;private ServerLevel level;private Path region;
 private volatile boolean closed;private Cursor resume;private Cursor position;
 SavedTerrainSource(MinecraftServer server,TerrainSettings settings){this(server,settings,null);}
 SavedTerrainSource(MinecraftServer server,TerrainSettings settings,Cursor resume){this.resume=resume;this.position=resume;this.settings=settings;world=server.getWorldPath(LevelResource.ROOT);levels=new ArrayList<>();for(var l:server.getAllLevels())if(settings.dimensions().contains(l.dimension().location().toString()))levels.add(l);levels.sort(Comparator.comparing(l->l.dimension().location().toString()));}
 /** Called only by the single dedicated reader. At most 64 empty slots per batch. */
 Result next()throws Exception{
  int skipped=0;
  while(!closed&&!Thread.currentThread().isInterrupted()&&skipped<64){
   if(slot==1024&&!nextRegion())return new Result(null,0,0,null,skipped,true);
   int current=slot++,x=rx*32+current%32,z=rz*32+current/32;
   if(x<Math.floorDiv(settings.minX(),16)||x>Math.floorDiv(settings.maxX(),16)||z<Math.floorDiv(settings.minZ(),16)||z>Math.floorDiv(settings.maxZ(),16))continue;
   if(!Files.isRegularFile(region,LinkOption.NOFOLLOW_LINKS))throw new java.io.IOException("Region disappeared during scan");
   var snapshot=read(level,x,z);if(closed)break;position=new Cursor(level.dimension().location().toString(),rx,rz,slot);
   if(snapshot==null){skipped++;continue;}
   return new Result(level,x,z,snapshot,skipped,false);
  }
  return new Result(null,0,0,null,skipped,closed);
 }
 private boolean nextRegion()throws Exception{
  while(!closed){
   if(entries==null||!entries.hasNext()){
    if(directory!=null){directory.close();directory=null;}if(++dimension>=levels.size())return false;level=levels.get(dimension);
    Path path=DimensionType.getStorageFolder(level.dimension(),world).resolve("region");if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS)){entries=null;continue;}
    var candidates=new ArrayList<Path>();try(var stream=Files.newDirectoryStream(path,"r.*.*.mca")){for(var p:stream){if(candidates.size()>=65536)throw new java.io.IOException("Region discovery limit");if(REGION.matcher(p.getFileName().toString()).matches())candidates.add(p);}}candidates.sort(Comparator.comparingLong((Path p)->coordinate(p,1)).thenComparingLong(p->coordinate(p,2)));entries=candidates.iterator();continue;
   }
   Path candidate=entries.next();if(!Files.isRegularFile(candidate,LinkOption.NOFOLLOW_LINKS))continue;var match=REGION.matcher(candidate.getFileName().toString());if(!match.matches())continue;
   long a,b;try{a=Long.parseLong(match.group(1));b=Long.parseLong(match.group(2));}catch(NumberFormatException invalid){continue;}
   if(a<Math.floorDiv(settings.minX(),512)||a>Math.floorDiv(settings.maxX(),512)||b<Math.floorDiv(settings.minZ(),512)||b>Math.floorDiv(settings.maxZ(),512))continue;
   int start=0;if(resume!=null){int compare=level.dimension().location().toString().compareTo(resume.dimension());if(compare<0||compare==0&&(a<resume.regionX()||a==resume.regionX()&&b<resume.regionZ()))continue;if(compare==0&&a==resume.regionX()&&b==resume.regionZ())start=resume.slot();resume=null;}
   rx=(int)a;rz=(int)b;region=candidate;slot=start;if(slot==1024)continue;return true;
  }return false;
 }
 private static long coordinate(Path p,int group){var m=REGION.matcher(p.getFileName().toString());m.matches();try{return Long.parseLong(m.group(group));}catch(NumberFormatException invalid){return Long.MAX_VALUE;}}
 Cursor position(){return position;}
 static SavedTerrainSnapshot read(ServerLevel level,int x,int z)throws Exception{
  var raw=level.getChunkSource().chunkMap.read(new ChunkPos(x,z)).get(10,TimeUnit.SECONDS);if(raw.isEmpty())return null;
  CompoundTag tag=raw.get().copy();tag=level.getChunkSource().chunkMap.upgradeChunkTag(level.dimension(),()->level.getServer().overworld().getDataStorage(),tag,level.getChunkSource().getGenerator().getTypeNameForDataFixer());if(!tag.getString("Status").equals("minecraft:full"))return null;
  return new SavedTerrainSnapshot(tag,x,z,level.getMinBuildHeight(),level.getHeight(),level.registryAccess().registryOrThrow(Registries.BIOME));
 }
 boolean cancelled(){return closed;}
 void cancel(){closed=true;}
 @Override public synchronized void close()throws java.io.IOException{closed=true;if(directory!=null){directory.close();directory=null;}}
}
