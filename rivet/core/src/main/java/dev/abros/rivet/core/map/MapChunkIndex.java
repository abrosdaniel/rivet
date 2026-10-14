package dev.abros.rivet.core.map;

import java.util.*;

/** Sparse two-axis range index: panning visits visible columns, not the entire explored world. */
public final class MapChunkIndex {
 private final NavigableMap<Integer,NavigableSet<Integer>> columns=new TreeMap<>();
 public void add(MapRepository.Chunk chunk){columns.computeIfAbsent(chunk.x(),x->new TreeSet<>()).add(chunk.z());}
 public void addAll(Collection<MapRepository.Chunk> chunks){chunks.forEach(this::add);}
 public List<MapRepository.Chunk> within(int minX,int maxX,int minZ,int maxZ){
  if(minX>maxX||minZ>maxZ)return List.of();var result=new ArrayList<MapRepository.Chunk>();
  for(var column:columns.subMap(minX,true,maxX,true).entrySet())for(int z:column.getValue().subSet(minZ,true,maxZ,true))result.add(new MapRepository.Chunk(column.getKey(),z));
  return result;
 }
}
