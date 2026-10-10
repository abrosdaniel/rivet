package dev.abros.rivet.client;

import dev.abros.rivet.core.map.*;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.concurrent.*;

/** Immutable colour snapshots are shaded off-thread; world access stays on the client thread. */
final class MapTerrainCache {
    record Image(long revision,int[][] levels){}
    private record Edge(int[] south,int[] east){
        int height(int x,int z,int fallback){int h=z==15?south[x]:east[z];return h==Integer.MIN_VALUE?fallback:h;}
        boolean same(Edge other){return other!=null&&Arrays.equals(south,other.south)&&Arrays.equals(east,other.east);}
    }
    private record Source(MapTile tile,long revision,Edge north,Edge west,Edge corner,int top,int depth,boolean legible,MapColors.Style style){}
    private static final Map<WorldMapClient.TileKey,Edge> edges=new LinkedHashMap<>(256,.75f,true);
    private static final ThreadPoolExecutor WORKER=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(64),r->{var t=new Thread(r,"Rivet map colours");t.setDaemon(true);return t;});
    private static final Map<WorldMapClient.TileKey,Image> images=new LinkedHashMap<>(256,.75f,true);
    // Keep compact overview levels independently of the expensive full-resolution LRU.
    private static final Map<WorldMapClient.TileKey,Image> overview=new LinkedHashMap<>(256,.75f,true);
    private static final Map<WorldMapClient.TileKey,Long> changes=new HashMap<>();
    private static long change;
    private static final Map<WorldMapClient.TileKey,Source> sources=new HashMap<>();
    private static final Set<WorldMapClient.TileKey> pending=new HashSet<>();
    private static long generation,revision,uploadStart,styleEpoch;
    static long styleEpoch(){return styleEpoch;}
    private static int uploads;
    static void beginFrame(){uploadStart=0;uploads=0;}
    static boolean uploadAllowed(){return uploads<8&&(uploadStart==0||System.nanoTime()-uploadStart<2_000_000);}
    static void uploaded(){if(uploadStart==0)uploadStart=System.nanoTime();uploads++;}
    static boolean canWork(){return WORKER.getQueue().remainingCapacity()>0;}
    static boolean waiting(WorldMapClient.TileKey key){return pending.contains(key)||changes.containsKey(key);}
    static long stateRevision(){return revision+change;}
    static boolean work(Runnable action){try{WORKER.execute(action);return true;}catch(RejectedExecutionException ex){return false;}}
    static void reset(){generation++;styleEpoch++;images.clear();edges.clear();overview.clear();changes.clear();sources.clear();pending.clear();}
    static void relightAll(){styleEpoch++;var keys=new HashSet<>(images.keySet());keys.addAll(overview.keySet());keys.addAll(pending);long stamp=++change;for(var key:keys)changes.put(key,stamp);}
    static void relight(MapLayer layer){
        styleEpoch++;
        long stamp=++change;var keys=new HashSet<>(images.keySet());keys.addAll(overview.keySet());keys.addAll(pending);
        for(var key:keys)if(key.layer().equals(layer))changes.put(key,stamp);
    }
    static void changed(WorldMapClient.TileKey key){
        change++;
        var tile=WorldMapClient.loaded(key);var previous=edges.get(key);
        boolean edgeChanged=tile!=null&&!edge(tile).same(previous);
        if(tile!=null)rememberEdge(key,tile);
        // Retain only boundary heights for overviews: shading must not reload whole neighbours.
        for(int z=0;z<=1;z++)for(int x=0;x<=1;x++){
            if((x!=0||z!=0)&&!edgeChanged)continue;
            var affected=new WorldMapClient.TileKey(key.layer(),key.x()+x,key.z()+z);
            if(images.containsKey(affected)||overview.containsKey(affected)||pending.contains(affected))changes.put(affected,++change);
        }
    }
    static Image image(WorldMapClient.TileKey key){return image(key,0);}
    static Image image(WorldMapClient.TileKey key,int detail){
        var coarse=detail==0?null:overview.get(key);
        if(coarse!=null&&!changes.containsKey(key))return coarse;
        var cached=images.get(key);if(cached==null)cached=coarse;var tile=WorldMapClient.loaded(key);
        if(tile==null&&(cached==null||changes.containsKey(key)))tile=WorldMapClient.exploredTile(key);
        if(tile==null||pending.contains(key))return cached;
        var north=neighbour(new WorldMapClient.TileKey(key.layer(),key.x(),key.z()-1));
        var west=neighbour(new WorldMapClient.TileKey(key.layer(),key.x()-1,key.z()));
        var corner=neighbour(new WorldMapClient.TileKey(key.layer(),key.x()-1,key.z()-1));
        boolean cave=key.layer().cave(),legible=MapCaves.view(key.dimension()).legible();int activeTop=MapCaves.top(key.dimension());
        int ceiling=key.band()==MapLayer.FULL?MapLayer.FULL:key.band()==MapLayer.SURFACE?MapLayer.SURFACE:MapCaves.layer(key.dimension()).equals(key.layer())?activeTop:key.band()*16+15;
        int depth=MapCaves.view(key.dimension()).depth(),worldHeight=Minecraft.getInstance().level==null?384:Minecraft.getInstance().level.dimensionType().logicalHeight();
        var style=MapRenderSettings.INSTANCE.style(key.dimension());
        var source=new Source(tile,tile.revision(),north,west,corner,ceiling,depth,legible,style);
        if(cached!=null&&source.equals(sources.get(key))&&!changes.containsKey(key))return cached;
        if(!canWork())return cached;
        var snapshot=tile.copy();rememberEdge(key,tile);long owner=generation,stamp=changes.getOrDefault(key,0L);
        pending.add(key);
        try{WORKER.execute(()->{
            int[] pixels=new int[256];
            for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                if(snapshot.color(x,z)==0)continue;int h=snapshot.groundHeight(x,z);
                int n=z==0?height(north,x,15,h):height(snapshot,x,z-1,h),nw=x==0?height(z==0?corner:west,15,z==0?15:z-1,h):z==0?height(north,x-1,15,h):height(snapshot,x-1,z-1,h);
                pixels[z*16+x]=MapColors.render(snapshot,x,z,n,nw,ceiling,depth,worldHeight,legible,style);
            }
            var levels=MapDetail.pyramid(pixels);
            Minecraft.getInstance().execute(()->{
                if(generation!=owner)return;pending.remove(key);
                // Retain the last complete image while a newer environment or sample is pending.
                if(changes.getOrDefault(key,0L)!=stamp)return;
                sources.put(key,source);
                if(changes.getOrDefault(key,0L)==stamp)changes.remove(key);
                var previous=images.get(key);
                long version=previous!=null&&Arrays.deepEquals(previous.levels(),levels)?previous.revision():++revision;
                images.put(key,new Image(version,levels));
                var reduced=levels.clone();reduced[0]=null;overview.put(key,new Image(version,reduced));
                while(images.size()>8192){var oldest=images.keySet().iterator().next();images.remove(oldest);sources.remove(oldest);}
                while(overview.size()>100000){var oldest=overview.keySet().iterator().next();overview.remove(oldest);if(!images.containsKey(oldest))changes.remove(oldest);}
            });
        });}catch(RejectedExecutionException ex){pending.remove(key);}
        return cached;
    }
    private static Edge edge(MapTile tile){
        int[] south=new int[16],east=new int[16];
        for(int i=0;i<16;i++){south[i]=tile.color(i,15)==0?Integer.MIN_VALUE:tile.groundHeight(i,15);east[i]=tile.color(15,i)==0?Integer.MIN_VALUE:tile.groundHeight(15,i);}
        return new Edge(south,east);
    }
    private static Edge rememberEdge(WorldMapClient.TileKey key,MapTile tile){
        var candidate=edge(tile);var previous=edges.get(key);if(candidate.same(previous))return previous;
        edges.put(key,candidate);while(edges.size()>100000)edges.remove(edges.keySet().iterator().next());return candidate;
    }
    private static Edge neighbour(WorldMapClient.TileKey key){
        var cached=edges.get(key);if(cached!=null)return cached;
        var tile=WorldMapClient.exploredTile(key);return tile==null?null:rememberEdge(key,tile);
    }
    private static int height(Edge edge,int x,int z,int fallback){return edge==null?fallback:edge.height(x,z,fallback);}
    private static int height(MapTile tile,int x,int z,int fallback){return tile==null||tile.color(x,z)==0?fallback:tile.groundHeight(x,z);}
    private MapTerrainCache(){}
}
