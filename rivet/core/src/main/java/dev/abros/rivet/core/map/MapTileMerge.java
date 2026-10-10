package dev.abros.rivet.core.map;
/** Read-only composition of personal terrain and previously received local files. */
public final class MapTileMerge {
 public static void copy(MapTile target,MapTile source,int x,int z){target.set(x,z,source.color(x,z),source.height(x,z),source.groundHeight(x,z),source.overlay(x,z),source.glowing(x,z),source.layers(x,z));target.biome(x,z,source.biome(x,z));target.blockLight(x,z,source.blockLight(x,z));target.skyLight(x,z,source.skyLight(x,z));}
 /** Personal observation wins; receiving unknown pixels must never erase existing exploration. */
 public static MapTile merge(MapTile own,MapTile received){var result=own==null?new MapTile():own.copy();if(received!=null)for(int z=0;z<16;z++)for(int x=0;x<16;x++)if(result.color(x,z)==0&&received.color(x,z)!=0)copy(result,received,x,z);return result;}
 private MapTileMerge(){}
}
