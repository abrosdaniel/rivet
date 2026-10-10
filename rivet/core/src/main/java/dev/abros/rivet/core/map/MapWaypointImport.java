package dev.abros.rivet.core.map;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Read-only interchange. Preview and commit share validation and duplicate detection. */
public final class MapWaypointImport {
 public static final int MAX_BYTES=2*1024*1024, MAX_FILES=4096, MAX_ENTRIES=4096;
 public record Entry(String source,String dimension,String name,int x,int y,int z,int color,boolean visible) {
  public Entry { new MapMarker(UUID.randomUUID(),"minecraft:overworld",name,x,y,z,color,"pin"); }
  public String mappingKey(){return dimension.isEmpty()?"?"+source:dimension;}
  public MapMarker marker(String target){return new MapMarker(UUID.randomUUID(),target,name,x,y,z,color,"pin",0,"",visible,visible);}
 }
 public record Preview(List<Entry> entries,List<String> issues){public Preview{entries=List.copyOf(entries);issues=List.copyOf(issues);}}
 public record Plan(List<MapMarker> additions,int duplicates) {public Plan{additions=List.copyOf(additions);}}
 @FunctionalInterface public interface DatReader {JsonObject read(byte[] bytes)throws IOException;}
 private static final int[] COLORS={0x000000,0x0000aa,0x00aa00,0x00aaaa,0xaa0000,0xaa00aa,0xffaa00,0xaaaaaa,0x555555,0x5555ff,0x55ff55,0x55ffff,0xff5555,0xff55ff,0xffff55,0xffffff,0xff681f,0x9ac0cd,0xbfff00,0xff69b4,0x8b4513};
 public static Preview read(List<Path> paths,DatReader dat)throws IOException {
  var entries=new ArrayList<Entry>();var issues=new ArrayList<String>();var files=new LinkedHashSet<Path>();
  for(Path selected:paths){if(Files.isDirectory(selected,LinkOption.NOFOLLOW_LINKS)){try(var stream=Files.list(selected)){var it=stream.iterator();int examined=0;while(it.hasNext()){if(++examined>8192)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.folder_is_too_large_select_waypoint_7aff1679"));Path p=it.next();if(supported(p)&&Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))files.add(p);}}}else files.add(selected);if(files.size()>MAX_FILES)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.too_many_waypoint_files_86bb00de"));}
  if(files.isEmpty())throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.no_txt_or_dat_waypoints_in_b9efc630"));long total=0;
  for(Path file:files){String source=file.toAbsolutePath().normalize().toString().replaceAll("[\\p{Cntrl}]","");
   if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.a_regular_waypoint_file_is_required_7cdef487"));
   long size=Files.size(file);if(size>MAX_BYTES||(total+=size)>8L*MAX_BYTES)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.waypoint_files_are_too_large_2_805c94ef"));
   byte[] bytes;try(var in=Files.newInputStream(file)){bytes=in.readNBytes(MAX_BYTES+1);}if(bytes.length>MAX_BYTES)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.waypoint_file_is_too_large_e97d3550"));
   try{String lower=source.toLowerCase(Locale.ROOT);Preview p;
    if(lower.endsWith(".txt"))p=xaero(StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString(),source);
    else if(lower.endsWith(".dat"))p=journey(dat.read(bytes),source);
    else throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.xaero_s_txt_and_journeymap_dat_ab3dafd0"));
    entries.addAll(p.entries());issues.addAll(p.issues());
   }catch(IOException|IllegalArgumentException|IllegalStateException ex){issues.add(label(source)+": неверный или неподдерживаемый формат");}
   if(entries.size()>MAX_ENTRIES||issues.size()>MAX_ENTRIES)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.up_to_4096_waypoints_per_operation_d1b031fd"));
  }return new Preview(entries,issues);
 }
 public static String label(String source){Path p=Path.of(source);return p.getParent()==null?p.toString():p.getParent().getFileName()+"/"+p.getFileName();}
 private static boolean supported(Path p){return p.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(txt|dat)");}
 public static Preview xaero(String text,String source)throws IOException {
  var entries=new ArrayList<Entry>();var issues=new ArrayList<String>();int line=0;
  for(String raw:text.replace("\ufeff","").split("\\R")){line++;String s=raw.strip();if(s.isEmpty()||s.startsWith("#")||s.startsWith("sets:"))continue;
   if(!s.startsWith("waypoint:")){issues.add(label(source)+":"+line+": неизвестная запись");continue;}
   try{String[] v=s.split(":",-1);if(v.length<10)throw new IllegalArgumentException();int type=Integer.parseInt(v[8]);
    if(type!=0){issues.add(label(source)+":"+line+": временная метка или точка смерти пропущена");continue;}
    if(v[4].equals("~")){issues.add(label(source)+":"+line+": высота не задана; задайте её в исходной метке");continue;}
    int color=Integer.parseInt(v[6]);if(color<0||color>=COLORS.length)throw new IllegalArgumentException();
    entries.add(new Entry(source,"",v[1].replace("§§",":"),Integer.parseInt(v[3]),Integer.parseInt(v[4]),Integer.parseInt(v[5]),0xff000000|COLORS[color],!bool(v[7])));
   }catch(IllegalArgumentException ex){issues.add(label(source)+":"+line+": неверная метка");}
   if(entries.size()+issues.size()>MAX_ENTRIES)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.too_many_waypoints_fa6cbada"));
  }return new Preview(entries,issues);
 }
 public static Preview journey(JsonObject root,String source)throws IOException {
  var entries=new ArrayList<Entry>();var issues=new ArrayList<String>();
  if(!root.has("waypoints")||!root.get("waypoints").isJsonObject())throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.a_journeymap_dat_file_is_required_2899fe41"));
  Collection<JsonElement> rows=root.getAsJsonObject("waypoints").asMap().values();
  if(rows.size()>MAX_ENTRIES)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.too_many_waypoints_fa6cbada"));int row=0;
  for(var element:rows){row++;try{var j=element.getAsJsonObject();var pos=j.getAsJsonObject("pos");
    if(j.has("type")&&j.get("type").getAsString().equalsIgnoreCase("Death")||j.has("groupId")&&j.get("groupId").getAsString().equals("journeymap_death")){issues.add(label(source)+":"+row+": точка смерти пропущена");continue;}
    if(j.has("version")&&!j.get("version").getAsString().equals("1"))throw new IllegalArgumentException();
    int color=integer(j,"color");var settings=j.getAsJsonObject("settings");
    boolean visible=flag(settings,"enable",true)&&flag(settings,"showOnMap",true);
    var dims=new LinkedHashSet<String>();if(j.has("dimensions")){for(var d:j.getAsJsonArray("dimensions"))dims.add(dimension(d.getAsString()));}
    if(pos.has("dimension"))dims.add(dimension(pos.get("dimension").getAsString()));if(dims.isEmpty())dims.add("");
    for(String dim:dims)entries.add(new Entry(source,dim,j.get("name").getAsString(),integer(pos,"x"),integer(pos,"y"),integer(pos,"z"),0xff000000|color,visible));
   }catch(RuntimeException ex){issues.add(label(source)+":"+row+": неверная метка");}
   if(entries.size()>MAX_ENTRIES)throw new IOException(dev.abros.rivet.core.Messages.text("rivet.core.too_many_waypoints_fa6cbada"));
  }return new Preview(entries,issues);
 }
 private static int integer(JsonObject j,String key){return j.get(key).getAsBigDecimal().intValueExact();}
 private static boolean flag(JsonObject j,String key,boolean fallback){return j==null||!j.has(key)?fallback:bool(j.get(key).getAsString());}
 private static boolean bool(String value){return switch(value){case "true","1"->true;case "false","0"->false;default->throw new IllegalArgumentException();};}
 public static String dimension(String dim){return switch(dim){case "0","overworld"->"minecraft:overworld";case "-1","the_nether"->"minecraft:the_nether";case "1","the_end"->"minecraft:the_end";default->dim;};}
 private record Key(String dimension,String name,int x,int y,int z){}
 private static Key key(MapMarker m){return new Key(m.dimension(),m.name().strip().toLowerCase(Locale.ROOT),m.x(),m.y(),m.z());}
 public static Plan plan(List<Entry> selected,Map<String,String> dimensions,List<MapMarker> existing){
  var keys=new HashSet<Key>();existing.forEach(m->keys.add(key(m)));var add=new ArrayList<MapMarker>();int duplicates=0;
  for(var e:selected){String target=dimensions.getOrDefault(e.mappingKey(),e.dimension());if(target.isEmpty())throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.select_a_dimension_for_waypoints_without_9fb021e2"));var marker=e.marker(target);if(keys.add(key(marker)))add.add(marker);else duplicates++;}
  if(existing.size()+add.size()>MAX_ENTRIES)throw new IllegalArgumentException(dev.abros.rivet.core.Messages.text("rivet.core.rivet_supports_up_to_4096_personal_7ba8df99"));return new Plan(add,duplicates);
 }
 private MapWaypointImport(){}
}
