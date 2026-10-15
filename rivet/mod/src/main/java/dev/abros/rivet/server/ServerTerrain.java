package dev.abros.rivet.server;

import dev.abros.rivet.api.*;
import dev.abros.rivet.core.api.*;
import dev.abros.rivet.core.map.MapColors;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;

/** Budgeted capture from live chunks or detached saved palettes, without chunk tickets. */
public final class ServerTerrain {
 private static dev.abros.rivet.core.api.material.TerrainMaterials materials;
 private static final Map<net.minecraft.world.level.block.state.BlockState,String> stateNames=new IdentityHashMap<>();
 private static volatile IsometricRenderer renderer;private static volatile IsometricRenderer isometric;private static TerrainSettings settings;
 private static java.util.concurrent.ThreadPoolExecutor reader;
 private static PrivateTerrainViews privateViews;
 private static Capture capture;private static long scan,lastFailure;private static volatile long ticks;
 private static TerrainJournal journal;private static volatile TerrainDirtyQueue dirty;private static TerrainDirtyQueue.Lease lease;
 private static java.util.concurrent.Future<SavedTerrainSnapshot> dirtyRead;private static java.util.concurrent.CompletableFuture<Void> dirtyWrite;
 private static int manualChunks;private static boolean journalFailed,journalLoaded;private static String generation;private static java.util.concurrent.CompletableFuture<Void> checkpoint;private static TerrainJournal.State lastCheckpoint;
 private static volatile TerrainApi service;private static final java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();private static final LinkedHashMap<UUID,RenderJob> jobs=new LinkedHashMap<>();private static RenderJob active;
 private static final class RenderJob {UUID id=UUID.randomUUID();final TerrainApi.Region region;int total;SavedTerrainSource saved;java.util.concurrent.Future<SavedTerrainSource.Result> reading;int offset,committedOffset,rendered,skipped,pendingSkipped;TerrainJournal.Cursor cursor,pendingCursor;String state="QUEUED";java.util.concurrent.CompletableFuture<Void> writing;
  RenderJob(TerrainApi.Region r){region=r;total=(r.maxChunkX()-r.minChunkX()+1)*(r.maxChunkZ()-r.minChunkZ()+1);}
  RenderJob(){region=null;total=-1;}
  RenderJob(TerrainJournal.Job record){region=record.region();id=record.id();total=record.total();offset=committedOffset=record.offset();rendered=record.rendered();skipped=record.skipped();cursor=record.cursor();state=terminal(record.state())?record.state():"QUEUED";}
  TerrainJournal.Job stored(){return new TerrainJournal.Job(id,region,cursor,committedOffset,total,rendered,skipped,state);}
  void closeSource(){if(saved==null)return;saved.cancel();if(reading!=null&&!reading.isDone()){reading.cancel(true);if(reader.remove((Runnable)reading))try{saved.close();}catch(java.io.IOException ignored){}}else try{saved.close();}catch(java.io.IOException ignored){}}
  TerrainApi.Job snapshot(){return new TerrainApi.Job(id,state,total,rendered,skipped);}
 }
 private static <T> java.util.concurrent.CompletableFuture<T> onServer(MinecraftServer server,IsometricRenderer owner,java.util.function.Supplier<T> work){
  if(renderer!=owner)return java.util.concurrent.CompletableFuture.failedFuture(new ApiException(ApiException.Code.UNAVAILABLE,"Renderer stopped"));
  if(requests.incrementAndGet()>128){requests.decrementAndGet();return java.util.concurrent.CompletableFuture.failedFuture(new ApiException(ApiException.Code.BUSY,"Render control queue full"));}
  var result=new java.util.concurrent.CompletableFuture<T>();result.whenComplete((v,e)->requests.decrementAndGet());result.orTimeout(5,java.util.concurrent.TimeUnit.SECONDS);
  server.execute(()->{if(result.isDone())return;try{if(renderer!=owner)throw new ApiException(ApiException.Code.UNAVAILABLE,"Renderer stopped");result.complete(work.get());}catch(Exception e){result.completeExceptionally(e);}});return result;
 }
 private static RenderJob find(UUID id){var job=jobs.get(id);if(job==null)throw new ApiException(ApiException.Code.NOT_FOUND,"Unknown render job");return job;}

 private static final class Capture {RenderJob owner;final ServerLevel level;final int x,z;final int[] pixels=new int[256],surfaceTints=new int[256];final String[] surfaceStates=new String[256];final int[] voxels,modelIds,tints;final byte[] caveSpaces;final List<String> palette=new ArrayList<>();final Map<String,Integer> paletteIds=new HashMap<>();final SavedTerrainSnapshot saved;int column;Capture(ServerLevel level,int x,int z){this(level,x,z,null);}Capture(ServerLevel level,int x,int z,SavedTerrainSnapshot saved){this.level=level;this.x=x;this.z=z;this.saved=saved;this.voxels=new int[level.getHeight()*256];this.caveSpaces=privateViews!=null&&privateViews.caves()?new byte[level.getHeight()*256]:null;this.modelIds=voxels==null?null:new int[voxels.length];this.tints=voxels==null?null:new int[voxels.length];}}
 static void install(){NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event)->{if(renderer!=null)try{tick(event.getServer());}catch(RuntimeException failure){
  if(active!=null&&lease==null){active.state="FAILED";active.closeSource();active=null;}if(lease!=null){dirty.retry(lease,ticks);lease=null;dirtyRead=null;dirtyWrite=null;}capture=null;persist();
  long now=System.nanoTime();if(now-lastFailure>java.util.concurrent.TimeUnit.MINUTES.toNanos(1)){lastFailure=now;com.mojang.logging.LogUtils.getLogger().warn("Terrain capture failed; previous completed images retained");}
 }});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Load event)->{if(event.getLevel() instanceof ServerLevel level)changed(level,event.getChunk().getPos().x,event.getChunk().getPos().z);});
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.level.ChunkEvent.Unload event)->{if(event.getLevel() instanceof ServerLevel level)changed(level,event.getChunk().getPos().x,event.getChunk().getPos().z);});
 }
 public static void changed(ServerLevel level,int x,int z){var queue=dirty;var policy=settings;if(queue==null||policy==null)return;String dimension=level.dimension().location().toString();if(!policy.dimensions().contains(dimension))return;for(int[] delta:new int[][]{{0,0},{-1,0},{1,0},{0,-1},{0,1}}){int cx=x+delta[0],cz=z+delta[1];if(cx>=Math.floorDiv(policy.minX(),16)&&cx<=Math.floorDiv(policy.maxX(),16)&&cz>=Math.floorDiv(policy.minZ(),16)&&cz<=Math.floorDiv(policy.maxZ(),16))queue.mark(new TerrainJournal.Chunk(dimension,cx,cz),ticks);}}
 private static boolean terminal(String state){return Set.of("COMPLETE","FAILED","CANCELLED").contains(state);}
 private static java.util.concurrent.CompletableFuture<Void> persist(){return persist(true);}
 private static java.util.concurrent.CompletableFuture<Void> persist(boolean running){if(journal==null)return java.util.concurrent.CompletableFuture.completedFuture(null);var state=new TerrainJournal.State(1,generation,jobs.values().stream().map(RenderJob::stored).toList(),dirty.snapshot(),dirty.sweep(),running);if(state.equals(lastCheckpoint)&&checkpoint!=null)return checkpoint;lastCheckpoint=state;checkpoint=journal.save(state);return checkpoint;}
 private static RenderJob enqueue(RenderJob job){if(journalFailed)throw new ApiException(ApiException.Code.UNAVAILABLE,"Terrain journal unavailable");if(jobs.values().stream().filter(j->!terminal(j.state)).count()>=16)throw new ApiException(ApiException.Code.BUSY,"Render queue full");jobs.put(job.id,job);while(jobs.size()>32){var old=jobs.values().stream().filter(j->terminal(j.state)).findFirst().orElseThrow();jobs.remove(old.id);}return job;}
 private static <T> java.util.concurrent.CompletableFuture<T> durable(java.util.concurrent.CompletableFuture<T> result){return result.thenCompose(value->{var saved=checkpoint;return saved==null?java.util.concurrent.CompletableFuture.completedFuture(value):saved.handle((v,error)->{if(error!=null)throw new java.util.concurrent.CompletionException(new ApiException(ApiException.Code.UNAVAILABLE,"Render journal unavailable"));return value;});});}

 static boolean available(){return service!=null;}
 static TerrainApi api(){var current=service;if(current==null)throw new ApiException(ApiException.Code.MODULE_DISABLED,"Server terrain disabled");return current;}
 static void start(MinecraftServer server)throws Exception{
  settings=TerrainSettings.from(ServerDatabase.settings());Path root=server.getWorldPath(LevelResource.ROOT).resolve("rivet-terrain");Files.createDirectories(root);
  if(Files.isSymbolicLink(root))throw new java.io.IOException("Unsafe terrain root");Path identity=root.resolve("world.id");UUID world;
  if(Files.exists(identity)){if(Files.isSymbolicLink(identity)||Files.size(identity)>64)throw new java.io.IOException("Invalid terrain identity");world=UUID.fromString(Files.readString(identity).strip());}
  else{world=UUID.randomUUID();Files.writeString(identity,world.toString(),StandardOpenOption.CREATE_NEW);}
  materials=ServerTerrainMaterials.open();stateNames.clear();renderer=new IsometricRenderer(root.resolve("surface"),world,TerrainProfiles.masked(settings,ServerDatabase.settings().strings("api.terrain.publicHidden")),materials,"",true);capture=null;scan=0;jobs.clear();active=null;
  if(ServerDatabase.settings().flag("api.terrain.isometric"))isometric=new IsometricRenderer(root.resolve("isometric"),world,TerrainProfiles.masked(settings,ServerDatabase.settings().strings("api.terrain.publicHidden")),materials);
  privateViews=new PrivateTerrainViews(root.resolve("profiles"),world,settings,TerrainProfiles.from(ServerDatabase.settings()),materials);
  reader=new java.util.concurrent.ThreadPoolExecutor(1,1,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(2),r->{var t=new Thread(r,"Rivet saved terrain reader");t.setDaemon(true);return t;});reader.allowCoreThreadTimeOut(true);
  generation=renderer.metadata().generation()+":"+(isometric==null?"off":isometric.metadata().generation())+":"+privateViews.generation();journal=new TerrainJournal(root);dirty=new TerrainDirtyQueue();ticks=0;lease=null;dirtyRead=null;dirtyWrite=null;manualChunks=0;journalFailed=false;journalLoaded=false;checkpoint=null;lastCheckpoint=null;
  var restored=journal.load();if(restored!=null){boolean same=generation.equals(restored.generation());for(var record:restored.jobs()){var job=new RenderJob(record);if(!same&&!terminal(job.state)){job.offset=job.committedOffset=job.rendered=job.skipped=0;job.cursor=null;if(job.region==null)job.total=-1;else if(!regionAllowed(job.region))job.state="CANCELLED";}if(!terminal(job.state)&&job.region!=null&&!regionAllowed(job.region))job.state="CANCELLED";jobs.put(job.id,job);}dirty.restore(restored.dirty().stream().filter(c->chunkAllowed(c)).toList(),restored.sweep()||restored.running()||!same);}journalLoaded=true;persist().get(5,java.util.concurrent.TimeUnit.SECONDS);
  final var owner=renderer;
  service=new TerrainApi(){
   public java.util.List<String> profiles(){owner.metadata();var names=new ArrayList<String>();names.add("surface");if(isometric!=null)names.add("isometric");names.addAll(privateViews.names());return java.util.List.copyOf(names);}
   public TerrainApi profile(String name){owner.metadata();if(name.equals("surface"))return this;TerrainApi iso;if(name.equals("isometric")){if(isometric==null)throw new ApiException(ApiException.Code.MODULE_DISABLED,"Terrain profile unavailable");iso=isometric;}else iso=privateViews.profile(name);var parent=this;return new TerrainApi(){
    public java.util.Optional<Slice> slice(){return iso.slice();}public Materials materials(){return iso.materials();}public Metadata metadata(){return iso.metadata();}public Projection projection(){return iso.projection();}public ApiCursor cursor(){return iso.cursor();}public Changes changes(ApiCursor after){return iso.changes(after);}public java.util.concurrent.CompletableFuture<Image> tile(Tile tile){return iso.tile(tile);}public java.util.concurrent.CompletableFuture<Job> render(Region r){return parent.render(r);}public java.util.concurrent.CompletableFuture<Job> renderSaved(){return parent.renderSaved();}public java.util.concurrent.CompletableFuture<java.util.List<Job>> jobs(){return parent.jobs();}public java.util.concurrent.CompletableFuture<Job> job(UUID id){return parent.job(id);}public java.util.concurrent.CompletableFuture<Job> cancel(UUID id){return parent.cancel(id);}
   };}
   public Projection projection(){return owner.projection();}public Materials materials(){return owner.materials();}public Metadata metadata(){return owner.metadata();}public ApiCursor cursor(){return owner.cursor();}public Changes changes(ApiCursor after){return owner.changes(after);}public java.util.concurrent.CompletableFuture<Image> tile(Tile tile){return owner.tile(tile);}
   public java.util.concurrent.CompletableFuture<Job> render(Region r){return durable(onServer(server,owner,()->{
    if(!regionAllowed(r))throw new ApiException(ApiException.Code.INVALID_ARGUMENT,"Invalid render region");var job=enqueue(new RenderJob(r));persist();return job.snapshot();
   }));}
   public java.util.concurrent.CompletableFuture<Job> renderSaved(){return durable(onServer(server,owner,()->{var job=enqueue(new RenderJob());persist();return job.snapshot();}));}
   public java.util.concurrent.CompletableFuture<java.util.List<Job>> jobs(){return onServer(server,owner,()->ServerTerrain.jobs.values().stream().map(RenderJob::snapshot).toList());}
   public java.util.concurrent.CompletableFuture<Job> job(UUID id){return onServer(server,owner,()->find(id).snapshot());}
   public java.util.concurrent.CompletableFuture<Job> cancel(UUID id){return durable(onServer(server,owner,()->{var job=find(id);if(!terminal(job.state)){job.state="CANCELLED";job.closeSource();if(job==active){if(capture!=null&&capture.owner==job)capture=null;active=null;}persist();}return job.snapshot();}));}
  };
 }
 static void stop(){if(journal!=null){if(journalLoaded&&!journalFailed)persist(false);journal.close();journal=null;}journalLoaded=false;dirty=null;lease=null;dirtyRead=null;dirtyWrite=null;var previous=renderer;renderer=null;service=null;capture=null;if(active!=null)active.closeSource();active=null;jobs.clear();if(reader!=null){reader.shutdownNow();dev.abros.rivet.core.WorkerShutdown.stop(java.time.Duration.ZERO,java.time.Duration.ofSeconds(3),reader);reader=null;}if(isometric!=null){isometric.close();isometric=null;}if(previous!=null)previous.close();if(privateViews!=null){privateViews.close();privateViews=null;}if(materials!=null){materials.close();materials=null;}stateNames.clear();}
 private static boolean regionAllowed(TerrainApi.Region r){return r!=null&&settings.dimensions().contains(r.dimension())&&r.minChunkX()<=r.maxChunkX()&&r.minChunkZ()<=r.maxChunkZ()&&r.minChunkX()>=Math.floorDiv(settings.minX(),16)&&r.minChunkZ()>=Math.floorDiv(settings.minZ(),16)&&r.maxChunkX()<=Math.floorDiv(settings.maxX(),16)&&r.maxChunkZ()<=Math.floorDiv(settings.maxZ(),16)&&((long)r.maxChunkX()-r.minChunkX()+1)*((long)r.maxChunkZ()-r.minChunkZ()+1)<=4096;}
 private static boolean chunkAllowed(TerrainJournal.Chunk c){return settings.dimensions().contains(c.dimension())&&c.x()>=Math.floorDiv(settings.minX(),16)&&c.x()<=Math.floorDiv(settings.maxX(),16)&&c.z()>=Math.floorDiv(settings.minZ(),16)&&c.z()<=Math.floorDiv(settings.maxZ(),16);}
 private static ServerLevel level(MinecraftServer server,String dimension){for(var l:server.getAllLevels())if(l.dimension().location().toString().equals(dimension))return l;return null;}
 private static void tick(MinecraftServer server){
  ticks++;if(checkpoint!=null&&checkpoint.isCompletedExceptionally()){journalFailed=true;for(var job:jobs.values())if(!terminal(job.state)){job.state="FAILED";job.closeSource();}active=null;capture=null;com.mojang.logging.LogUtils.getLogger().error("Terrain journal write failed; rendering stopped until restart");return;}if(ticks%20==0)persist();if(journalFailed)return;
  if(active==null){active=jobs.values().stream().filter(j->j.state.equals("QUEUED")).findFirst().orElse(null);if(active==null&&dirty.sweep()){active=enqueue(new RenderJob());dirty.sweep(false);persist();}if(active!=null){active.state=active.region==null?"READING":"CAPTURING";if(active.region==null)active.saved=new SavedTerrainSource(server,settings,active.cursor);}}
  if(lease==null&&capture==null&&(active==null||active.writing==null&&active.reading==null&&manualChunks>=8))lease=dirty.poll(ticks);
  if(lease!=null){
   if(dirtyWrite!=null){if(!dirtyWrite.isDone())return;try{dirtyWrite.join();dirty.complete(lease);}catch(Exception failed){dirty.retry(lease,ticks);}lease=null;dirtyWrite=null;manualChunks=0;persist();return;}
   if(capture==null){var c=lease.chunk();var level=level(server,c.dimension());if(level==null){dirty.complete(lease);lease=null;return;}
    if(dirtyRead==null){if(level.getChunkSource().getChunkNow(c.x(),c.z())!=null)capture=new Capture(level,c.x(),c.z());else{dirtyRead=reader.submit(()->SavedTerrainSource.read(level,c.x(),c.z()));return;}}
    else{if(!dirtyRead.isDone())return;try{var saved=dirtyRead.get();dirtyRead=null;if(saved==null){dirty.complete(lease);lease=null;return;}capture=new Capture(level,c.x(),c.z(),saved);}catch(Exception failure){dirty.retry(lease,ticks);lease=null;dirtyRead=null;return;}}
   }
  }
  if(active!=null&&lease==null){
   if(active.writing!=null){if(!active.writing.isDone())return;try{active.writing.join();active.rendered++;active.committedOffset=active.offset;active.skipped+=active.pendingSkipped;active.pendingSkipped=0;if(active.pendingCursor!=null){active.cursor=active.pendingCursor;active.pendingCursor=null;}manualChunks++;active.state="CAPTURING";persist();}catch(Exception failed){active.state="FAILED";active.closeSource();active=null;persist();return;}active.writing=null;if(manualChunks>=8&&dirty.size()>0)return;}
   if(active.saved!=null&&capture==null){
    if(active.reading==null){var source=active.saved;active.state="READING";try{active.reading=reader.submit(()->{try{return source.next();}finally{if(source.cancelled())source.close();}});}catch(java.util.concurrent.RejectedExecutionException busy){return;}return;}
    if(!active.reading.isDone())return;
    try{var result=active.reading.get();active.reading=null;active.pendingSkipped=result.skipped();active.pendingCursor=active.saved.position();
     if(result.snapshot()==null){manualChunks++;active.skipped+=active.pendingSkipped;active.pendingSkipped=0;active.cursor=active.pendingCursor;active.pendingCursor=null;persist();}
     if(result.done()){active.total=Math.addExact(active.rendered,active.skipped);active.state="COMPLETE";active.closeSource();active=null;persist();return;}
     if(result.snapshot()==null)return;capture=new Capture(result.level(),result.x(),result.z(),result.snapshot());capture.owner=active;active.state="CAPTURING";
    }catch(Exception failure){active.state="FAILED";active.closeSource();active=null;persist();return;}
   }
   if(active.saved==null&&active.offset==active.total&&capture==null){active.state="COMPLETE";active=null;persist();return;}
   if(active.saved==null&&capture==null){var r=active.region;int width=r.maxChunkX()-r.minChunkX()+1,index=active.offset++,x=r.minChunkX()+index%width,z=r.minChunkZ()+index/width;
    ServerLevel level=null;for(var candidate:server.getAllLevels())if(candidate.dimension().location().toString().equals(r.dimension())){level=candidate;break;}
    if(level==null||level.getChunkSource().getChunkNow(x,z)==null){active.skipped++;active.committedOffset=active.offset;manualChunks++;return;}capture=new Capture(level,x,z);capture.owner=active;
   }
  }
  if(capture==null){var players=server.getPlayerList().getPlayers();if(players.isEmpty())return;int side=settings.radius()*2+1,area=side*side;long index=scan++;var player=players.get((int)(Math.floorDiv(index,area)%players.size()));
   int offset=(int)(index%area),x=player.chunkPosition().x+offset%side-settings.radius(),z=player.chunkPosition().z+offset/side-settings.radius();ServerLevel level=player.serverLevel();
   if(!settings.dimensions().contains(level.dimension().location().toString())||level.getChunkSource().getChunkNow(x,z)==null)return;dirty.mark(new TerrainJournal.Chunk(level.dimension().location().toString(),x,z),ticks);return;
  }
  Capture current=capture;var chunk=current.level.getChunkSource().getChunkNow(current.x,current.z);if(chunk==null&&current.saved==null){capture=null;if(current.owner!=null){current.owner.skipped++;current.owner.committedOffset=current.owner.offset;}if(lease!=null){dirty.retry(lease,ticks);lease=null;}return;}
  long deadline=System.nanoTime()+settings.budgetMicros()*1000L;int done=0;
  while(current.column<256&&done++<settings.columnsPerTick()&&System.nanoTime()<deadline){int i=current.column++,lx=i%16,lz=i/16,x=current.x*16+lx,z=current.z*16+lz;String dimension=current.level.dimension().location().toString();
   if(!settings.visible(dimension,x,z))continue;
   net.minecraft.world.level.BlockGetter view=current.saved==null?current.level:current.saved;
   int y=current.saved==null?chunk.getHeight(Heightmap.Types.WORLD_SURFACE,lx,lz):current.saved.top(lx,lz);var pos=new BlockPos.MutableBlockPos(x,y,z);if(!current.level.getWorldBorder().isWithinBounds(pos))continue;
   int color=0;int depth=0;
   while(y>=current.level.getMinBuildHeight()&&depth++<32){pos.setY(y);var state=view.getBlockState(pos);var map=state.getMapColor(view,pos);if(map!=MapColor.NONE){
     int rgb=map.col;var biome=current.saved==null?current.level.getBiome(pos).value():current.saved.biome(pos);
     if(state.getFluidState().is(FluidTags.WATER)){rgb=biome.getWaterColor();int water=0;var below=pos.mutable();while(water<16&&below.getY()>current.level.getMinBuildHeight()){below.move(0,-1,0);if(!view.getFluidState(below).is(FluidTags.WATER))break;water++;}float shade=1f-Math.min(16,water)*.018f;rgb=shade(rgb,shade);}
     else if(state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.SHORT_GRASS)||state.is(Blocks.TALL_GRASS))rgb=biome.getGrassColor(x,z);
     else if(state.is(BlockTags.LEAVES))rgb=biome.getFoliageColor();
     int north=y;if(current.saved!=null){if(lz>0)north=current.saved.top(lx,lz-1);}else{var neighbor=current.level.getChunkSource().getChunkNow(Math.floorDiv(x,16),Math.floorDiv(z-1,16));if(neighbor!=null)north=neighbor.getHeight(Heightmap.Types.WORLD_SURFACE,Math.floorMod(x,16),Math.floorMod(z-1,16));}
     current.surfaceStates[i]=stateName(state);current.surfaceTints[i]=state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.SHORT_GRASS)||state.is(Blocks.TALL_GRASS)||state.is(BlockTags.LEAVES)||state.getFluidState().is(FluidTags.WATER)?rgb:0xffffff;
     current.surfaceTints[i]|=(MapColors.shade(0xffffffff,y,north,north,state.getLightEmission()>0)&255)<<24;
     color=MapColors.shade(0xff000000|rgb,y,north,north,state.getLightEmission()>0);break;
    }y--;}
   current.pixels[i]=color;
   if(current.voxels!=null){for(int vy=current.level.getMinBuildHeight();vy<current.level.getMaxBuildHeight();vy++){
    pos.setY(vy);var block=view.getBlockState(pos);if(current.caveSpaces!=null)current.caveSpaces[(vy-current.level.getMinBuildHeight())*256+i]=block.isAir()?CaveRenderer.AIR:block.canOcclude()?CaveRenderer.ROOF:0;if(block.isAir())continue;var material=block.getMapColor(view,pos);int rgb=material.col,alpha=255;
    if(block.is(Blocks.GLASS)||block.is(Blocks.GLASS_PANE)||block.is(Blocks.TINTED_GLASS)||block.getBlock() instanceof net.minecraft.world.level.block.StainedGlassBlock||block.getBlock() instanceof net.minecraft.world.level.block.StainedGlassPaneBlock){alpha=90;if(material==MapColor.NONE)rgb=0xd6edf1;}
    else if(material==MapColor.NONE){if(materials==null)continue;rgb=0x808080;}
    var biome=current.saved==null?current.level.getBiome(pos).value():current.saved.biome(pos);
    if(block.getFluidState().is(FluidTags.WATER)){rgb=biome.getWaterColor();alpha=180;}
    else if(block.is(Blocks.GRASS_BLOCK)||block.is(Blocks.SHORT_GRASS)||block.is(Blocks.TALL_GRASS))rgb=biome.getGrassColor(x,z);
    else if(block.is(BlockTags.LEAVES)){rgb=biome.getFoliageColor();alpha=220;}
    int vi=(vy-current.level.getMinBuildHeight())*256+i;current.voxels[vi]=alpha<<24|rgb;
    if(materials!=null||!block.getFluidState().isEmpty()){String name=stateName(block);Integer id=current.paletteIds.get(name);if(id==null){if(current.palette.size()>=4096)throw new IllegalStateException("Terrain state palette limit");current.palette.add(name);id=current.palette.size();current.paletteIds.put(name,id);}current.modelIds[vi]=id;current.tints[vi]=block.is(Blocks.GRASS_BLOCK)||block.is(Blocks.SHORT_GRASS)||block.is(Blocks.TALL_GRASS)||block.is(BlockTags.LEAVES)||block.getFluidState().is(FluidTags.WATER)?rgb:0xffffff;}
   }}
  }
  if(current.column==256){String dim=current.level.dimension().location().toString();
   if(current.owner!=null){var job=current.owner;job.state="WRITING";job.writing=renderer.render(dim,current.x,current.z,current.level.getMinBuildHeight(),current.level.getHeight(),current.voxels,current.palette,current.modelIds,current.tints);if(isometric!=null)job.writing=java.util.concurrent.CompletableFuture.allOf(job.writing,isometric.render(dim,current.x,current.z,current.level.getMinBuildHeight(),current.level.getHeight(),current.voxels,current.palette,current.modelIds,current.tints));}
   else{dirtyWrite=renderer.render(dim,current.x,current.z,current.level.getMinBuildHeight(),current.level.getHeight(),current.voxels,current.palette,current.modelIds,current.tints);if(isometric!=null)dirtyWrite=java.util.concurrent.CompletableFuture.allOf(dirtyWrite,isometric.render(dim,current.x,current.z,current.level.getMinBuildHeight(),current.level.getHeight(),current.voxels,current.palette,current.modelIds,current.tints));}
   var privateWrite=privateViews.render(dim,current.x,current.z,current.pixels,current.surfaceStates,current.surfaceTints,current.level.getMinBuildHeight(),current.level.getHeight(),current.voxels,current.palette,current.modelIds,current.tints,current.caveSpaces);
   if(current.owner!=null)current.owner.writing=java.util.concurrent.CompletableFuture.allOf(current.owner.writing,privateWrite);else dirtyWrite=java.util.concurrent.CompletableFuture.allOf(dirtyWrite,privateWrite);
   capture=null;}
 }
 private static String stateName(net.minecraft.world.level.block.state.BlockState state){return stateNames.computeIfAbsent(state,s->{String id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();var props=new TreeMap<String,String>();for(var entry:s.getValues().entrySet())props.put(entry.getKey().getName(),propertyName(entry.getKey(),entry.getValue()));return id+(props.isEmpty()?"":"["+props.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).collect(java.util.stream.Collectors.joining(","))+"]");});}
 @SuppressWarnings({"unchecked","rawtypes"}) private static String propertyName(net.minecraft.world.level.block.state.properties.Property property,Comparable value){return property.getName(value);}
 private static int shade(int rgb,float value){return ((int)((rgb>>>16&255)*value)<<16)|((int)((rgb>>>8&255)*value)<<8)|(int)((rgb&255)*value);}
}
