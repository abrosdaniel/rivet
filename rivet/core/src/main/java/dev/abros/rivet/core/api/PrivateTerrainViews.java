package dev.abros.rivet.core.api;
import dev.abros.rivet.api.*;
import dev.abros.rivet.core.api.material.TerrainMaterials;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded named caches. Each renderer masks before persistence, projection and overview generation. */
public final class PrivateTerrainViews implements AutoCloseable {
 private final Map<String,TerrainApi> views=new LinkedHashMap<>();
 public PrivateTerrainViews(Path root,UUID world,TerrainSettings base,List<TerrainProfiles.Spec> profiles,TerrainMaterials materials)throws java.io.IOException{
  if(Files.isSymbolicLink(root))throw new java.io.IOException("Unsafe profile directory");Files.createDirectories(root);
  try{for(var spec:profiles){var policy=TerrainProfiles.masked(base,spec.hidden());Path path=root.resolve(spec.id());String namespace=":private:"+spec.id();
   views.put(spec.id(),switch(spec.projection()){case "surface"->new IsometricRenderer(path,world,policy,materials,namespace,true);case "cave"->new CaveRenderer(path,world,policy,spec,materials);default->new IsometricRenderer(path,world,policy,materials,namespace);});
  }}catch(java.io.IOException|RuntimeException e){close();throw e;}
 }
 public List<String> names(){return List.copyOf(views.keySet());}
 public boolean isometric(){return views.values().stream().anyMatch(v->v.projection().profile().equals("isometric"));}
 public boolean caves(){return views.values().stream().anyMatch(v->v instanceof CaveRenderer);}
 public boolean voxels(){return views.values().stream().anyMatch(v->v instanceof IsometricRenderer||v instanceof CaveRenderer);}
 public TerrainApi profile(String name){var view=views.get(name);if(view==null)throw new ApiException(ApiException.Code.NOT_FOUND,"Terrain profile unavailable");return view;}
 public String generation(){return views.entrySet().stream().map(e->e.getKey()+":"+e.getValue().metadata().generation()).collect(java.util.stream.Collectors.joining(";"));}
 public CompletableFuture<Void> render(String dimension,int x,int z,int[] pixels,String[] states,int[] surfaceTints,int minY,int height,int[] voxels,List<String> palette,int[] models,int[] tints){
  return render(dimension,x,z,pixels,states,surfaceTints,minY,height,voxels,palette,models,tints,null);
 }
 public CompletableFuture<Void> render(String dimension,int x,int z,int[] pixels,String[] states,int[] surfaceTints,int minY,int height,int[] voxels,List<String> palette,int[] models,int[] tints,byte[] spaces){
  var writes=new ArrayList<CompletableFuture<Void>>();for(var view:views.values()){
   if(view instanceof TerrainRenderer surface)writes.add(surface.renderChunk(dimension,x,z,pixels,states,surfaceTints));
   else if(view instanceof CaveRenderer cave)writes.add(cave.render(dimension,x,z,minY,height,voxels,palette,models,tints,spaces));
   else writes.add(((IsometricRenderer)view).render(dimension,x,z,minY,height,voxels,palette,models,tints));
  }return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
 }
 @Override public void close(){for(var view:views.values())try{((AutoCloseable)view).close();}catch(Exception ignored){}views.clear();}
}
