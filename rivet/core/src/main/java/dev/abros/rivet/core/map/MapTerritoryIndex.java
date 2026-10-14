package dev.abros.rivet.core.map;
import java.util.*;

/** Immutable bounding-volume tree. Built when territory snapshots change, never per frame. */
public final class MapTerritoryIndex {
 private record Entry(MapTerritory territory,int order,double minX,double minZ,double maxX,double maxZ){}
 private static final class Node {
  double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;Node left,right;List<Entry> leaves;
  Node(List<Entry> entries,int depth){for(var e:entries){minX=Math.min(minX,e.minX);minZ=Math.min(minZ,e.minZ);maxX=Math.max(maxX,e.maxX);maxZ=Math.max(maxZ,e.maxZ);}
   if(entries.size()<=8){leaves=List.copyOf(entries);return;}
   var sorted=new ArrayList<>(entries);sorted.sort(Comparator.comparingDouble(e->depth%2==0?e.minX+e.maxX:e.minZ+e.maxZ));int middle=sorted.size()/2;left=new Node(sorted.subList(0,middle),depth+1);right=new Node(sorted.subList(middle,sorted.size()),depth+1);
  }
  void query(double x0,double z0,double x1,double z1,List<Entry> result){if(maxX<x0||maxZ<z0||minX>x1||minZ>z1)return;if(leaves!=null){for(var e:leaves)if(e.maxX>=x0&&e.maxZ>=z0&&e.minX<=x1&&e.minZ<=z1)result.add(e);}else{left.query(x0,z0,x1,z1,result);right.query(x0,z0,x1,z1,result);}}
 }
 private final Map<String,Node> dimensions=new HashMap<>();
 public MapTerritoryIndex(List<MapTerritory> source){var groups=new HashMap<String,List<Entry>>();for(int i=0;i<source.size();i++){var t=source.get(i);double x0=Double.POSITIVE_INFINITY,z0=x0,x1=Double.NEGATIVE_INFINITY,z1=x1;for(var p:t.points()){x0=Math.min(x0,p.x());z0=Math.min(z0,p.z());x1=Math.max(x1,p.x());z1=Math.max(z1,p.z());}groups.computeIfAbsent(t.dimension(),k->new ArrayList<>()).add(new Entry(t,i,x0,z0,x1,z1));}groups.forEach((dimension,entries)->dimensions.put(dimension,new Node(entries,0)));}
 public List<MapTerritory> visible(String dimension,double x0,double z0,double x1,double z1){var root=dimensions.get(dimension);if(root==null)return List.of();var out=new ArrayList<Entry>();root.query(Math.min(x0,x1),Math.min(z0,z1),Math.max(x0,x1),Math.max(z0,z1),out);out.sort(Comparator.comparingInt(Entry::order));return out.stream().map(Entry::territory).toList();}
}
