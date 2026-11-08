package dev.abros.rivet.core.map;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Local map maintenance. Call only off-thread while map writers are suspended. */
public final class MapStorage {
 public record Entry(UUID world,UUID player,String name,String playerName,long bytes,long terrainBytes,long tiles){}
 private record Identity(UUID world,UUID player){}
 private static final java.util.regex.Pattern DIMENSION=java.util.regex.Pattern.compile("[0-9a-f]{64}");
 private final Path rivet;
 public MapStorage(Path rivet){this.rivet=rivet.toAbsolutePath().normalize();}
 private Path base(String name)throws IOException{
  Path base=rivet.resolve(name);
  if(Files.isSymbolicLink(rivet)||Files.isSymbolicLink(base))throw new IOException("Linked map directory");
  return base;
 }
 private Path owner(String name,UUID world,UUID player)throws IOException{
  Path base=base(name),worldPath=base.resolve(world.toString()),owner=worldPath.resolve(player.toString());
  if(Files.isSymbolicLink(worldPath)||Files.isSymbolicLink(owner))throw new IOException("Linked map owner");
  return owner;
 }
 private static UUID id(Path path){try{var id=UUID.fromString(path.getFileName().toString());return id.toString().equals(path.getFileName().toString())?id:null;}catch(IllegalArgumentException ex){return null;}}
 private static boolean terrain(Path relative){String first=relative.getName(0).toString();return first.equals("previews-v1")||DIMENSION.matcher(first).matches();}
 public void remember(UUID world,UUID player,String name,String playerName)throws IOException{
  var data=new com.google.gson.JsonObject();data.addProperty("name",label(name));data.addProperty("player",label(playerName));
  dev.abros.rivet.core.Json.write(owner("maps",world,player).resolve("label.json"),data);
 }
 private static String label(String value){return Objects.toString(value,"").codePoints().filter(c->!Character.isISOControl(c)).limit(80).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();}
 private static String[] labels(Path directory){
  try{Path file=directory.resolve("label.json");if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>4096)return new String[]{"",""};var data=dev.abros.rivet.core.Json.read(file);return new String[]{label(dev.abros.rivet.core.Json.opt(data,"name","")),label(dev.abros.rivet.core.Json.opt(data,"player",""))};}catch(Exception ex){return new String[]{"",""};}
 }
 public List<Entry> scan()throws IOException{
  var totals=new LinkedHashMap<Identity,long[]>();var names=new HashMap<Identity,String[]>();
  for(String name:List.of("maps","maps-received")){
   Path base=base(name);if(!Files.isDirectory(base,LinkOption.NOFOLLOW_LINKS))continue;
   try(var worlds=Files.newDirectoryStream(base)){for(Path world:worlds){UUID worldId=id(world);if(worldId==null||!Files.isDirectory(world,LinkOption.NOFOLLOW_LINKS))continue;
    try(var players=Files.newDirectoryStream(world)){for(Path player:players){UUID playerId=id(player);if(playerId==null||!Files.isDirectory(player,LinkOption.NOFOLLOW_LINKS))continue;
     var identity=new Identity(worldId,playerId);var counts=totals.computeIfAbsent(identity,k->new long[3]);names.putIfAbsent(identity,labels(player));
     Files.walkFileTree(player,new SimpleFileVisitor<>(){
      @Override public FileVisitResult visitFileFailed(Path file,IOException failure)throws IOException{if(failure instanceof NoSuchFileException)return FileVisitResult.CONTINUE;throw failure;}
      @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs){if(attrs.isRegularFile()){counts[0]+=attrs.size();if(terrain(player.relativize(file))){counts[1]+=attrs.size();if(file.getFileName().toString().endsWith(".tile"))counts[2]++;}}return FileVisitResult.CONTINUE;}
     });
    }}
   }}
  }
  return totals.entrySet().stream().map(e->new Entry(e.getKey().world,e.getKey().player,names.get(e.getKey())[0],names.get(e.getKey())[1],e.getValue()[0],e.getValue()[1],e.getValue()[2])).sorted(Comparator.comparing(e->e.world().toString()+e.player())).toList();
 }
 /** Clear exploration and disposable previews for one identity; preserve all personal JSON and backups. */
 public void clearTerrain(UUID world,UUID player)throws IOException{
  for(String name:List.of("maps","maps-received")){
   Path root=owner(name,world,player);if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))continue;
   MapRegionIndex.forgetTree(root);
   try(var children=Files.newDirectoryStream(root)){for(Path child:children){
    if(!terrain(root.relativize(child))||!Files.isDirectory(child,LinkOption.NOFOLLOW_LINKS))continue;
    Files.walkFileTree(child,new SimpleFileVisitor<>(){
     @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs)throws IOException{Files.delete(file);return FileVisitResult.CONTINUE;}
     @Override public FileVisitResult postVisitDirectory(Path dir,IOException failure)throws IOException{if(failure!=null)throw failure;Files.delete(dir);return FileVisitResult.CONTINUE;}
    });
   }}
  }
 }
}
