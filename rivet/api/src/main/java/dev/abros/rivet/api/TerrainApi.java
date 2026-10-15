package dev.abros.rivet.api;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Server-owned top-down terrain, independent of client exploration. Metadata bounds are blocks; tile coordinates index 16 × 2^zoom block squares. */
public interface TerrainApi {
 record Metadata(UUID world,String generation,List<String> dimensions,int tilePixels,int maxZoom,int minX,int minZ,int maxX,int maxZ){public Metadata{dimensions=List.copyOf(dimensions);}}
 record Tile(String dimension,int zoom,int x,int z){}
 record Image(String etag,byte[] png){public Image{png=png.clone();}public byte[] png(){return png.clone();}}
 record Change(ApiCursor cursor,Tile tile,String etag){}
 record Changes(List<Change> items,ApiCursor nextCursor){public Changes{items=List.copyOf(items);}}
 /** Inclusive chunk coordinates; at most 4096 chunks per request. Unloaded chunks are skipped. */
 record Region(String dimension,int minChunkX,int minChunkZ,int maxChunkX,int maxChunkZ){}
 record Job(UUID id,String state,int total,int rendered,int skipped){}
 default CompletableFuture<Job> render(Region region){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Render control unavailable"));}
 /** Stream all saved regions in configured dimensions/bounds. Total is -1 until discovery completes. */
 default CompletableFuture<Job> renderSaved(){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Saved-world render unavailable"));}
 /** Retained jobs in submission order, including queued/restored work (at most 32). */
 default CompletableFuture<List<Job>> jobs(){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Render queue unavailable"));}
 default CompletableFuture<Job> job(UUID id){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Render control unavailable"));}
 default CompletableFuture<Job> cancel(UUID id){return CompletableFuture.failedFuture(new ApiException(ApiException.Code.MODULE_DISABLED,"Render control unavailable"));}
 /** Inclusive floor/ceiling search band; absent for surface/isometric. */
 record Slice(int minY,int maxY){}
 default Optional<Slice> slice(){return Optional.empty();}
 record Projection(String profile,String horizontal,String vertical,int tilePixels){}
 default Projection projection(){return new Projection("surface","x","z",16);}
 /** Available startup profiles; Java integrations are trusted in-process callers. */
 default List<String> profiles(){return List.of(projection().profile());}
 default TerrainApi profile(String name){if(name.equals(projection().profile()))return this;throw new ApiException(ApiException.Code.MODULE_DISABLED,"Terrain profile unavailable");}
 record Materials(boolean active,int resources,long resolved,long fallback,Map<String,String> diagnostics){public Materials{diagnostics=Map.copyOf(diagnostics);}}
 default Materials materials(){return new Materials(false,0,0,0,Map.of());}
 Metadata metadata();
 CompletableFuture<Image> tile(Tile tile);
 /** Current incarnation cursor. Restart/overflow requires metadata and a fresh cursor. */
 ApiCursor cursor();
 Changes changes(ApiCursor after);
}
