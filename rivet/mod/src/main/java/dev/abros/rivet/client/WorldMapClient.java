package dev.abros.rivet.client;

import dev.abros.rivet.core.map.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.concurrent.*;

/** Only loaded client chunks are sampled. All disk work runs on a bounded queue. */
public final class WorldMapClient {
    record TileKey(String dimension,int x,int z,int band) {
        TileKey(String dimension,int x,int z){this(dimension,x,z,MapLayer.SURFACE);}
        TileKey(MapLayer layer,int x,int z){this(layer.dimension(),x,z,layer.band());}
        MapLayer layer(){return new MapLayer(dimension,band);}
    }
    static final KeyMapping OPEN=new KeyMapping("key.rivet.map",GLFW.GLFW_KEY_M,"key.categories.rivet");
    // One reserved slot accepts the final snapshot before reset/shutdown discards memory.
    // A new world cannot produce dirty tiles until its queued reads (after that snapshot) finish.
    private static final int IO_CAPACITY=256;
    private static final ThreadPoolExecutor IO=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(IO_CAPACITY+1),r->{var t=new Thread(r,"Rivet map files");t.setDaemon(true);return t;});
    private static final Map<TileKey,MapTile> tiles=new LinkedHashMap<>(256,.75f,true);
    private static final Map<TileKey,Long> retries=new HashMap<>();
    private record WriteKey(java.nio.file.Path owner,Object part){}
    private static final dev.abros.rivet.core.RetryingWrites<WriteKey,Object> writes=new dev.abros.rivet.core.RetryingWrites<>(16416);
    private static final java.util.concurrent.atomic.AtomicBoolean writing=new java.util.concurrent.atomic.AtomicBoolean();
    private static final Map<MapLayer,Long> indexRetries=new HashMap<>();
    private static long markerRetry;
    private static WriteKey writeKey(MapRepository repo,Object part){return new WriteKey(repo.storageDirectory(),part);}
    private static boolean write(MapRepository repo,Object part,Object snapshot,dev.abros.rivet.core.RetryingWrites.Writer<Object> writer){return writes.put(writeKey(repo,part),snapshot,writer);}
    private static void pumpWrites(boolean force){
        if(writes.size()==0||!writing.compareAndSet(false,true))return;
        if(!submit(()->{try{writes.drain(System.currentTimeMillis(),force?16416:32,force,(key,ex)->Minecraft.getInstance().execute(()->{if(repository!=null&&repository.storageDirectory().equals(key.owner()))failed(repository,ex);}));}finally{writing.set(false);}}))writing.set(false);
    }
    private static final Set<TileKey> loading=new HashSet<>(),dirty=new HashSet<>();
    private static final Map<MapLayer,Set<MapRepository.Chunk>> known=new HashMap<>();
    private record Window(MapLayer layer,int minX,int maxX,int minZ,int maxZ,long revision){}
    private static final Map<Window,List<TileKey>> windows=new LinkedHashMap<>(16,.75f,true);
    private static long knownRevision;
    private static final Set<MapLayer> indexing=new HashSet<>(),indexed=new HashSet<>();
    private static final Set<TileKey> changedChunks=new LinkedHashSet<>();
    private record Scanned(java.lang.ref.WeakReference<net.minecraft.world.level.chunk.LevelChunk> chunk,int tick){}
    private static final Map<TileKey,Scanned> scanned=new LinkedHashMap<>();
    private static MapTile scanTile;private static boolean scanChanged,preferUpdates;
    private static net.minecraft.world.level.chunk.LevelChunk scanChunk;
    private static int scanOriginX=Integer.MIN_VALUE,scanOriginZ=Integer.MIN_VALUE;
    private static final MapDeathTracker deaths=new MapDeathTracker();
    private static final Deque<MapMarker> pendingDeaths=new ArrayDeque<>();
    private static long deathRetry;
    private static List<MapMarker> markers=List.of();
    private static List<MapCategory> categories=List.of();
    private static Map<String,MapCategory> categoryIndex=Map.of();
    private static MapRepository repository,receivedRepository,importing;
    private static final Map<TileKey,MapTile> receivedTiles=new HashMap<>(),combinedTiles=new HashMap<>();
    private static Object connection;
    private static UUID world,player;
    private static int ticks,scan,scanRadius,scanPixel;
    private static int idleTick=Integer.MIN_VALUE;
    private static TileKey scanning;
    private static long lastFrame;private static MapLayer scanLayer;private static int scanTop=MapLayer.SURFACE;
    private static List<MapRepository.Chunk> scanOffsets=List.of();
    private static boolean markersReady,markersLoading;
    private static String error="";
    static void reload(){MapBlockColors.clear();resample();Minimap.reset();MapMobIcons.clear();}
    static void chunkLoaded(net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.world.level.ChunkPos pos){scanned.keySet().removeIf(k->k.dimension().equals(level.dimension().location().toString())&&k.x()==pos.x&&k.z()==pos.z);idleTick=Integer.MIN_VALUE;}
    static void shutdown(){flush(true);enqueue(()->writes.drain(System.currentTimeMillis(),16416,true,(key,ex)->com.mojang.logging.LogUtils.getLogger().warn("Map snapshot remains unsaved",ex)));IO.shutdown();try{if(!IO.awaitTermination(5,TimeUnit.SECONDS))com.mojang.logging.LogUtils.getLogger().warn("Rivet map saving did not finish before shutdown");}catch(InterruptedException ex){Thread.currentThread().interrupt();}}
    static boolean allowed() {
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return false;
        if(mc.hasSingleplayerServer())return dev.abros.rivet.server.ServerMap.worldId()!=null;
        var j=ServerMenuClient.state;
        return ServerMenuClient.supports("world-map")&&j.has("map")&&j.getAsJsonObject("map").has("world")&&j.getAsJsonObject("map").get("enabled").getAsBoolean();
    }
    private static UUID identity() {
        var mc=Minecraft.getInstance();return mc.hasSingleplayerServer()?dev.abros.rivet.server.ServerMap.worldId():UUID.fromString(ServerMenuClient.state.getAsJsonObject("map").get("world").getAsString());
    }
    static MapRepository ownRepository(){return repository;}
    static boolean io(Runnable work){return submit(work);}
    static UUID worldId(){return world;}
    static boolean cavesAllowed(){var mc=Minecraft.getInstance();if(!allowed())return false;if(mc.hasSingleplayerServer())return dev.abros.rivet.server.ServerMap.cavesAllowed();var policy=ServerMenuClient.state.getAsJsonObject("map");return !policy.has("caves")||policy.get("caves").getAsBoolean();}
    static MapExport.Source exportSource(MapLayer layer){if(repository==null||!allowed())return null;var snapshot=new HashMap<MapRepository.Chunk,MapTile>();for(var e:tiles.entrySet())if(e.getKey().layer().equals(layer))snapshot.put(new MapRepository.Chunk(e.getKey().x(),e.getKey().z()),e.getValue().copy());return new MapExport.Source(repository,receivedRepository,Map.copyOf(snapshot));}
    static boolean ready() { return repository!=null&&allowed(); }
    static String error() { return error; }
    static List<MapMarker> markers() { return markers; }
    static List<MapCategory> categories(){return categories;}
    static String categoryName(String id){var category=categoryIndex.get(id);return category==null?Client.tr("map.uncategorized").getString():category.name();}
    private static void categories(List<MapCategory> next){categories=List.copyOf(next);var index=new HashMap<String,MapCategory>();for(var category:categories)index.putIfAbsent(category.id().toString(),category);categoryIndex=Map.copyOf(index);}
    static MapMarkerVisibility markerVisibility(boolean minimap){return new MapMarkerVisibility(minimap,MapLayers.visible(MapLayers.Layer.MARKERS,minimap),MapLayers.visible(MapLayers.Layer.DEATHS,minimap),categoryIndex);}
    static boolean visible(MapMarker marker,boolean minimap){return markerVisibility(minimap).test(marker);}
    static void category(MapCategory category){if(!markersReady)throw new IllegalStateException(Client.tr("map.loading").getString());var next=new ArrayList<>(categories);next.removeIf(c->c.id().equals(category.id()));next.add(category);saveCategories(next);}
    static void deleteCategory(UUID id){if(!markersReady)throw new IllegalStateException(Client.tr("map.loading").getString());saveCategories(categories.stream().filter(c->!c.id().equals(id)).toList());}
    private static void saveCategories(List<MapCategory> next){if(next.size()>128||next.stream().map(c->c.name().toLowerCase(java.util.Locale.ROOT)).distinct().count()!=next.size())throw new IllegalArgumentException(Client.tr("map.categoryInvalid").getString());var snapshot=List.copyOf(next);var repo=repository;if(!write(repo,"categories",snapshot,v->repo.categories((List<MapCategory>)v)))throw new IllegalStateException(Client.tr("map.busy").getString());categories(snapshot);Minimap.invalidate();}

    static boolean markersReady() { return markersReady; }
    private static void failed(MapRepository owner,Exception ex) {
        if(repository==owner){error=Client.tr("map.filesError").getString();com.mojang.logging.LogUtils.getLogger().warn("Cannot access Rivet map files",ex);}
    }
    private static boolean submit(Runnable action) { if(IO.getQueue().size()>=IO_CAPACITY)return false;return enqueue(action); }
    private static boolean enqueue(Runnable action) { try{IO.execute(action);return true;}catch(RejectedExecutionException ex){return false;} }
    static void reset() {
        flush(true);MapCaves.reset();scanLayer=null;scanTop=MapLayer.SURFACE;deaths.reset();pendingDeaths.clear();deathRetry=0;repository=null;receivedRepository=null;receivedTiles.clear();combinedTiles.clear();world=player=null;tiles.clear();loading.clear();dirty.clear();known.clear();windows.clear();knownRevision++;indexing.clear();indexed.clear();changedChunks.clear();scanned.clear();scanChunk=null;scanTile=null;scanOriginX=scanOriginZ=Integer.MIN_VALUE;retries.clear();markers=List.of();categories(List.of());markersReady=markersLoading=false;markerRetry=0;indexRetries.clear();error="";scan=scanRadius=scanPixel=0;scanning=null;lastFrame=0;scanOffsets=List.of();MapTerrainCache.reset();
        MapBlockColors.clear();Minimap.reset();MapMobIcons.clear();
    }
    static void tick() {
        pumpWrites(false);var mc=Minecraft.getInstance();var current=mc.getConnection();
        if(connection!=current){reset();connection=current;}
        if(!allowed()) { if(repository!=null)reset();if(mc.screen instanceof WorldMapScreen)mc.setScreen(null);while(OPEN.consumeClick()){}return; }
        UUID id=identity(),owner=mc.player.getUUID();
        if(!id.equals(world)||!owner.equals(player)) {
            reset();deaths.observe(mc.player.isDeadOrDying());world=id;player=owner;repository=new MapRepository(mc.gameDirectory.toPath().resolve("rivet/maps"),id,owner);receivedRepository=new MapRepository(mc.gameDirectory.toPath().resolve("rivet/maps-received"),id,owner);
        }
        if(!markersReady&&!markersLoading&&System.currentTimeMillis()>=markerRetry){
            var repo=repository;markersLoading=submit(()->{try{var queuedMarkers=writes.pending(writeKey(repo,"markers"));var queuedCategories=writes.pending(writeKey(repo,"categories"));var read=queuedMarkers==null?repo.markers():(List<MapMarker>)queuedMarkers;var groups=queuedCategories==null?repo.categories():(List<MapCategory>)queuedCategories;mc.execute(()->{if(repository==repo){markers=read;categories(groups);markersReady=true;markersLoading=false;}});}catch(Exception ex){mc.execute(()->{if(repository==repo){markersLoading=false;markerRetry=System.currentTimeMillis()+5000;failed(repo,ex);}});}});
        }
        if(deaths.observe(mc.player.isDeadOrDying())){
            long at=System.currentTimeMillis();String name=Client.tr("map.death").getString()+" · "+java.time.Instant.ofEpochMilli(at).atZone(AccessibilityScreen.zone()).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm"));
            pendingDeaths.add(new MapMarker(UUID.randomUUID(),mc.level.dimension().location().toString(),name,mc.player.getBlockX(),Math.clamp(mc.player.getBlockY(),-2048,2048),mc.player.getBlockZ(),0xff606876,"skull",at));
        }
        pendingDeaths.removeIf(m->MapDeathLifecycle.expired(m,System.currentTimeMillis()));
        if(markersReady&&!pendingDeaths.isEmpty()&&System.currentTimeMillis()>=deathRetry){try{put(pendingDeaths.peek());pendingDeaths.remove();deathRetry=0;}catch(IllegalStateException ex){error=ex.getMessage();deathRetry=System.currentTimeMillis()+5000;}}
        if(markersReady){
            long now=System.currentTimeMillis();String dimension=mc.level.dimension().location().toString();
            var next=markers.stream().filter(m->!MapDeathLifecycle.remove(m,now,dimension,mc.player.getX(),mc.player.getY(),mc.player.getZ(),!mc.player.isDeadOrDying())).toList();
            if(next.size()!=markers.size())try{persist(next);}catch(IllegalStateException ex){error=ex.getMessage();}
        }
        MapCaves.tick();index(MapCaves.layer(mc.level.dimension().location().toString()));
        sample();
        ++ticks;
        if(ticks%100==0)flush();
        while(OPEN.consumeClick())if(mc.screen==null)mc.setScreen(new WorldMapScreen(null));
    }
    static MapTile tile(String dimension,int x,int z) {
        return tile(MapLayer.surface(dimension),x,z);
    }
    static MapTile tile(MapLayer layer,int x,int z) {var own=ownTile(layer,x,z);return own==null?null:combined(new TileKey(layer,x,z),own);}
    private static MapTile combined(TileKey key,MapTile own){if(!receivedTiles.containsKey(key))return own;return combinedTiles.computeIfAbsent(key,k->MapTileMerge.merge(own,receivedTiles.get(k)));}
    private static MapTile ownTile(MapLayer layer,int x,int z) {
        var key=new TileKey(layer,x,z);var tile=tiles.get(key);if(tile!=null)return tile;if(tiles.size()>=8192&&writes.size()>=8192)return null;
        if(repository!=null&&writes.pending(writeKey(repository,key))==null&&indexed.contains(layer)&&!known.getOrDefault(layer,Set.of()).contains(new MapRepository.Chunk(x,z))){tile=new MapTile();tiles.put(key,tile);evict();return tile;}
        if(repository==null||System.currentTimeMillis()<retries.getOrDefault(key,0L)||loading.size()>=64||!loading.add(key))return null;
        var repo=repository;var received=receivedRepository;var mc=Minecraft.getInstance();
        if(!submit(()->{try{var queued=writes.pending(writeKey(repo,key));var read=queued==null?repo.read(layer,x,z).orElseGet(MapTile::new):((MapTile)queued).copy();var shared=received.read(layer,x,z).orElse(null);mc.execute(()->{if(repository==repo){loading.remove(key);tiles.put(key,read);if(shared!=null)receivedTiles.put(key,shared);combinedTiles.remove(key);MapTerrainCache.changed(key);evict();}});}catch(Exception ex){mc.execute(()->{if(repository==repo){loading.remove(key);retries.put(key,System.currentTimeMillis()+30000);failed(repo,ex);}});}}))loading.remove(key);
        return null;
    }
    static boolean explored(TileKey key){return known.getOrDefault(key.layer(),Set.of()).contains(new MapRepository.Chunk(key.x(),key.z()));}
    static long terrainIndexRevision(){return knownRevision;}
    static MapTile exploredTile(String dimension,int x,int z){
        return exploredTile(new TileKey(dimension,x,z));
    }
    static MapTile exploredTile(TileKey key){
        var loaded=tiles.get(key);if(loaded!=null)return combined(key,loaded);
        // Load only previously explored neighbours: a render halo must never discover terrain.
        return known.getOrDefault(key.layer(),Set.of()).contains(new MapRepository.Chunk(key.x(),key.z()))?tile(key.layer(),key.x(),key.z()):null;
    }
    private static void evict() {
        while(tiles.size()>8192){var key=tiles.keySet().iterator().next();if(dirty.contains(key)&&!save(key,tiles.get(key)))return;tiles.remove(key);receivedTiles.remove(key);combinedTiles.remove(key);}
    }
    private static boolean save(TileKey key,MapTile tile) {
        var repo=repository;var snapshot=tile.copy();
        if(!write(repo,key,snapshot,v->repo.write(key.layer(),key.x(),key.z(),(MapTile)v)))return false;
        dirty.remove(key);return true;
    }
    private static void index(MapLayer dimension) {
        if(repository!=null&&!indexed.contains(dimension)&&System.currentTimeMillis()>=indexRetries.getOrDefault(dimension,0L)&&indexing.add(dimension)) {
            var repo=repository;var received=receivedRepository;if(!submit(()->{try{var chunks=new HashSet<>(repo.chunks(dimension));chunks.addAll(received.chunks(dimension));Minecraft.getInstance().execute(()->{if(repository==repo){indexing.remove(dimension);indexRetries.remove(dimension);indexed.add(dimension);if(known.computeIfAbsent(dimension,d->new HashSet<>()).addAll(chunks))knownRevision++;}});}catch(Exception ex){Minecraft.getInstance().execute(()->{if(repository==repo){indexing.remove(dimension);indexRetries.put(dimension,System.currentTimeMillis()+5000);failed(repo,ex);}});}}))indexing.remove(dimension);
        }
    }
    static List<TileKey> visibleTiles(String dimension,MapViewport view,int width,int height) {return visibleTiles(MapLayer.surface(dimension),view,width,height);}
    static List<TileKey> visibleTiles(MapLayer dimension,MapViewport view,int width,int height) {
        index(dimension);
        double dx=width/view.zoom()/2+16,dz=height/view.zoom()/2+16;
        var window=new Window(dimension,(int)Math.floor((view.x()-dx)/16),(int)Math.floor((view.x()+dx)/16),(int)Math.floor((view.z()-dz)/16),(int)Math.floor((view.z()+dz)/16),knownRevision);
        var result=windows.get(window);if(result!=null)return result;
        double centerX=(window.minX()+window.maxX())/2d,centerZ=(window.minZ()+window.maxZ())/2d;
        result=known.getOrDefault(dimension,Set.of()).stream().filter(c->c.x()>=window.minX()&&c.x()<=window.maxX()&&c.z()>=window.minZ()&&c.z()<=window.maxZ())
            .sorted(Comparator.comparingDouble(c->Math.hypot(c.x()-centerX,c.z()-centerZ))).map(c->new TileKey(dimension,c.x(),c.z())).toList();
        windows.put(window,result);while(windows.size()>32)windows.remove(windows.keySet().iterator().next());return result;
    }
    static void flush() {flush(false);}
    private static void flush(boolean finalSnapshot) {
        if(repository!=null)for(var key:List.copyOf(dirty)){var tile=tiles.get(key);if(tile!=null&&!save(key,tile))break;}
        pumpWrites(finalSnapshot);
    }
    static MapTile loaded(TileKey key){var own=tiles.get(key);return own==null?null:combined(key,own);}
    static Set<TileKey> ownKeys(){return Set.copyOf(tiles.keySet());}
    static MapTile ownSnapshot(TileKey key){var tile=tiles.get(key);return tile==null?null:tile.copy();}
    public static void changed(BlockPos position){if(!ready())return;var mc=Minecraft.getInstance();String dim=mc.level.dimension().location().toString();int x=position.getX()>>4,z=position.getZ()>>4;
        scanned.keySet().removeIf(k->k.dimension().equals(dim)&&k.x()==x&&k.z()==z);
        if(changedChunks.size()<4096)changedChunks.add(new TileKey(MapCaves.layer(dim),x,z));}
    static void frame(){long timing=dev.abros.rivet.core.PerformanceMetrics.start();try{MapTerrainCache.beginFrame();sample();}finally{dev.abros.rivet.core.PerformanceMetrics.end("client.map.sample",timing);}}
    private static void sample(){
        long now=System.nanoTime(),elapsed=lastFrame==0?16_000_000:now-lastFrame;lastFrame=now;
        if(!ready()||writes.size()>=8192||tiles.size()>8192)return;
        // Share the time budget across ticks and frames, including low-FPS windows.
        long budget=MapWorkBudget.samplingNanos(elapsed);
        long deadline=now+budget;
        do {if(!explore())break;}while(System.nanoTime()<deadline);
    }
    private static boolean explore() {
        var mc=Minecraft.getInstance();var level=mc.level;var pos=mc.player.blockPosition();String dim=level.dimension().location().toString();var active=MapCaves.layer(dim);int top=MapCaves.top(dim);
        if(!active.equals(scanLayer)||top!=scanTop){finishScan();if(scanLayer!=null&&scanLayer.equals(active)){scanned.keySet().removeIf(k->k.layer().equals(active));MapTerrainCache.relight(active);}scanLayer=active;scanTop=top;scan=0;idleTick=Integer.MIN_VALUE;}
        if(level.dimensionType().hasCeiling()&&!active.cave())return false;
        int radius=Math.clamp(mc.options.getEffectiveRenderDistance(),2,32),originX=pos.getX()>>4,originZ=pos.getZ()>>4;
        if(scanOffsets.isEmpty()||scanRadius!=radius||scanOriginX!=originX||scanOriginZ!=originZ){
            var offsets=new ArrayList<MapRepository.Chunk>();for(int z=-radius;z<=radius;z++)for(int x=-radius;x<=radius;x++)offsets.add(new MapRepository.Chunk(x,z));offsets.sort(Comparator.comparingInt(c->c.x()*c.x()+c.z()*c.z()));scanOffsets=List.copyOf(offsets);scanRadius=radius;scanOriginX=originX;scanOriginZ=originZ;scan=0;
        }
        if(scanning==null){
            if(idleTick==ticks&&changedChunks.isEmpty())return false;
            // Alternate edits and discovery so neither can starve the other while flying.
            if(preferUpdates)scanning=nextChanged(level);
            if(scanning==null)scanning=nextUnscanned(level);
            if(scanning==null)scanning=nextChanged(level);
            if(scanning==null)scanning=nextRefresh(level);
            if(scanning==null){idleTick=ticks;return false;}
            preferUpdates=!preferUpdates;scanPixel=0;scanChanged=false;scanTile=null;
            scanChunk=level.getChunk(scanning.x(),scanning.z());
        }
        var key=scanning;
        if(!key.dimension().equals(level.dimension().location().toString())||!level.hasChunk(key.x(),key.z())||level.getChunk(key.x(),key.z())!=scanChunk||scanChunk.isEmpty()){finishScan();return true;}
        if(scanTile==null){var loaded=ownTile(key.layer(),key.x(),key.z());if(loaded==null){changedChunks.add(key);finishScan();return true;}scanTile=loaded.copy();}
        var chunk=scanChunk;var at=new BlockPos.MutableBlockPos();int end=Math.min(256,scanPixel+16);
        for(;scanPixel<end;scanPixel++){int x=scanPixel%16,z=scanPixel/16;var sample=key.layer().cave()?MapSurfaceSampler.cave(level,chunk,key.x()*16+x,key.z()*16+z,at,scanTop,MapCaves.view(dim).depth()):MapSurfaceSampler.sample(level,chunk,key.x()*16+x,key.z()*16+z,at);if(sample!=null){scanChanged|=scanTile.set(x,z,sample.base(),sample.top(),sample.ground(),sample.overlay(),sample.glowing(),sample.layers());scanChanged|=scanTile.biome(x,z,sample.biome());scanChanged|=scanTile.blockLight(x,z,sample.blockLight());scanChanged|=scanTile.skyLight(x,z,sample.skyLight());}else scanChanged|=scanTile.set(x,z,0,0);}
        if(scanPixel==256){if(scanChanged){tiles.put(key,scanTile);combinedTiles.remove(key);MapTerrainCache.changed(key);dirty.add(key);if(known.computeIfAbsent(key.layer(),d->new HashSet<>()).add(new MapRepository.Chunk(key.x(),key.z())))knownRevision++;}scanned.put(key,new Scanned(new java.lang.ref.WeakReference<>(chunk),ticks));while(scanned.size()>8192)scanned.remove(scanned.keySet().iterator().next());finishScan();}
        return true;
    }
    private static TileKey nextChanged(net.minecraft.client.multiplayer.ClientLevel level){
        for(var iterator=changedChunks.iterator();iterator.hasNext();){
            var key=iterator.next();
            if(loading.contains(key)||System.currentTimeMillis()<retries.getOrDefault(key,0L))continue;
            iterator.remove();
            if(key.layer().equals(scanLayer)&&level.hasChunk(key.x(),key.z())&&!level.getChunk(key.x(),key.z()).isEmpty())return key;
        }
        return null;
    }
    private static TileKey nextUnscanned(net.minecraft.client.multiplayer.ClientLevel level){
        String dimension=level.dimension().location().toString();
        for(int attempts=0;attempts<scanOffsets.size();attempts++){
            var offset=scanOffsets.get(Math.floorMod(scan++,scanOffsets.size()));
            int x=scanOriginX+offset.x(),z=scanOriginZ+offset.z();if(!level.hasChunk(x,z)||level.getChunk(x,z).isEmpty())continue;
            var key=new TileKey(scanLayer,x,z);if(loading.contains(key)||System.currentTimeMillis()<retries.getOrDefault(key,0L))continue;var previous=scanned.get(key);
            // Chunk identity detects a fresh server snapshot at an already visited location.
            if(previous==null||previous.chunk().get()!=level.getChunk(x,z))return key;
        }
        return null;
    }
    private static TileKey nextRefresh(net.minecraft.client.multiplayer.ClientLevel level){
        String dimension=level.dimension().location().toString();
        for(var offset:scanOffsets){
            int x=scanOriginX+offset.x(),z=scanOriginZ+offset.z();var key=new TileKey(scanLayer,x,z);
            var previous=scanned.get(key);
            if(previous!=null&&ticks-previous.tick()>=600&&level.hasChunk(x,z)&&!level.getChunk(x,z).isEmpty())return key;
        }
        return null;
    }
    static void resample(){scanned.clear();idleTick=Integer.MIN_VALUE;finishScan();}
    private static void finishScan(){scanning=null;scanChunk=null;scanTile=null;scanPixel=0;}
    static void put(MapMarker marker) {
        if(!ready()||!markersReady)throw new IllegalStateException(Client.tr("map.loading").getString());
        if(marker.death()&&(MapDeathLifecycle.expired(marker,System.currentTimeMillis())))throw new IllegalStateException(Client.tr("map.saveError").getString());
        var existing=markers.stream().filter(m->m.id().equals(marker.id())).findFirst().orElse(null);
        if(existing!=null&&existing.death()&&!existing.equals(marker))throw new IllegalStateException(Client.text("message.death_not_editable"));
        var next=new ArrayList<>(markers);next.removeIf(m->m.id().equals(marker.id()));next.add(marker);persist(next);
    }
    static void delete(UUID id) { if(!ready()||!markersReady)throw new IllegalStateException(Client.tr("map.loading").getString());var next=new ArrayList<>(markers);next.removeIf(m->m.id().equals(id));persist(next); }
    private static void persist(List<MapMarker> next) {
        if(importing==repository)throw new IllegalStateException(Client.tr("map.busy").getString());
        if(next.size()>4096)throw new IllegalStateException(Client.tr("map.markerLimit").getString());
        var repo=repository;var snapshot=List.copyOf(next);
        if(!write(repo,"markers",snapshot,v->repo.markers((List<MapMarker>)v)))throw new IllegalStateException(Client.tr("map.busy").getString());
        markers=snapshot;
    }
    static void importMarkers(MapRepository owner,List<dev.abros.rivet.core.map.MapWaypointImport.Entry> selected,Map<String,String> dimensions,java.util.function.Consumer<String> done){
        if(owner!=repository||!ready()||!markersReady||importing==owner||writes.pending(writeKey(owner,"markers"))!=null)throw new IllegalStateException(Client.tr("map.busy").getString());
        var plan=dev.abros.rivet.core.map.MapWaypointImport.plan(selected,dimensions,markers);
        if(plan.additions().isEmpty()){done.accept(Client.text("ui.no_new_waypoints_duplicates_skipped_f3799597"));return;}
        var before=markers;var next=new ArrayList<>(before);next.addAll(plan.additions());importing=owner;
        if(!submit(()->{String result;boolean success;
            try{owner.importMarkers(before,plan.additions());success=true;result=Client.text("ui.imported_d16103fe")+plan.additions().size()+Client.text("ui.duplicates_538d265e")+plan.duplicates();}
            catch(Exception ex){success=false;result=Client.text("ui.could_not_save_waypoints_check_file_9fe21ebc");}
            final boolean saved=success;final String message=result;
            Minecraft.getInstance().execute(()->{if(importing==owner)importing=null;if(repository==owner){if(saved){markers=List.copyOf(next);Minimap.invalidate();}done.accept(message);}});
        })){importing=null;throw new IllegalStateException(Client.tr("map.busy").getString());}
    }
    private WorldMapClient() {}
}
