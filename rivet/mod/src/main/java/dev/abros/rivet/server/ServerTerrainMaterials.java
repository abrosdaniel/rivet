package dev.abros.rivet.server;

import dev.abros.rivet.api.TerrainMaterialProvider;
import dev.abros.rivet.core.api.material.*;
import java.nio.file.*;
import java.util.*;

/** Assets from installed mod containers, then explicitly supplied local packs, in name order. */
final class ServerTerrainMaterials {
 static TerrainMaterials open()throws java.io.IOException{
  var sources=new ArrayList<Path>();
  for(var file:net.neoforged.fml.ModList.get().getModFiles())sources.add(file.getFile().findResource(""));
  Path local=net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("rivet/terrain-resources");
  if(Files.isSymbolicLink(local))throw new java.io.IOException("Unsafe terrain resources directory");Files.createDirectories(local);
  try(var entries=Files.list(local)){var packs=entries.sorted(Comparator.comparing(p->p.getFileName().toString())).toList();if(packs.size()>64)throw new java.io.IOException("Too many terrain resource packs");for(var p:packs){String name=p.getFileName().toString();if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)||name.endsWith(".zip")||name.endsWith(".jar"))sources.add(p);}}
  var resources=new TerrainResources(sources);try{
   var providers=ServiceLoader.load(TerrainMaterialProvider.class,TerrainMaterialProvider.class.getClassLoader()).stream().map(ServiceLoader.Provider::get).toList();
   if(resources.size()==0&&providers.isEmpty()){resources.close();return null;}
   return new TerrainMaterials(resources,providers);
  }catch(RuntimeException|ServiceConfigurationError failure){resources.close();throw new java.io.IOException("Invalid terrain material provider",failure);}
 }
 private ServerTerrainMaterials(){}
}
