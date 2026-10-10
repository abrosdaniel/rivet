package dev.abros.rivet.core.map;

import dev.abros.rivet.core.Hashes;
import dev.abros.rivet.core.Json;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned, bounded files; callers schedule disk work outside the game thread. */
public final class MapRepository {
    private final Path root;
    public MapRepository(Path maps,UUID world,UUID player) {
        root=maps.resolve(world.toString()).resolve(player.toString());
    }
    public Path storageDirectory(){return root.toAbsolutePath().normalize();}
    private Path dimension(String dimension) {
        if(!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid dimension");
        return root.resolve(Hashes.sha256(dimension.getBytes(StandardCharsets.UTF_8)));
    }
    private Path layer(MapLayer layer){var folder=dimension(layer.dimension());return layer.cave()?folder.resolve("caves").resolve(layer.band()==MapLayer.FULL?"full":Integer.toString(layer.band())):folder;}
    private Path tile(MapLayer layer,int x,int z) { return layer(layer).resolve(x+"_"+z+".tile"); }
    public List<MapLayer> layers(Collection<String> dimensions,boolean caves)throws IOException {
        var result=new ArrayList<MapLayer>();for(String dimension:dimensions){result.add(MapLayer.surface(dimension));Path folder=dimension(dimension).resolve("caves");if(caves&&Files.isDirectory(folder))try(var files=Files.list(folder)){for(var file:files.toList()){String name=file.getFileName().toString();try{result.add(new MapLayer(dimension,name.equals("full")?MapLayer.FULL:Integer.parseInt(name)));}catch(IllegalArgumentException ignored){}}}}return List.copyOf(result);
    }
    public record Chunk(int x,int z) {}
    public Set<Chunk> chunks(String dimension)throws IOException {return chunks(MapLayer.surface(dimension));}
    public Set<Chunk> chunks(MapLayer layer)throws IOException {
        Path folder=layer(layer);if(!Files.isDirectory(folder))return Set.of();
        var result=new HashSet<Chunk>();
        try(var files=Files.list(folder)) {
            var iterator=files.iterator();while(iterator.hasNext()) {
                String name=iterator.next().getFileName().toString();if(!name.matches("-?[0-9]+_-?[0-9]+\\.tile"))continue;
                String[] parts=name.substring(0,name.length()-5).split("_");
                try{result.add(new Chunk(Integer.parseInt(parts[0]),Integer.parseInt(parts[1])));}catch(NumberFormatException ex){throw new IOException("Invalid map tile filename",ex);}
                if(result.size()>100000)throw new IOException("Too many map chunks");
            }
        }return Set.copyOf(result);
    }
    public Optional<MapTile> read(String dimension,int x,int z)throws IOException {return read(MapLayer.surface(dimension),x,z);}
    public Optional<MapTile> read(MapLayer layer,int x,int z)throws IOException {
        Path file=tile(layer,x,z);if(!Files.exists(file))return Optional.empty();
        if(Files.size(file)<8||Files.size(file)>196608)throw new IOException("Invalid map tile size");
        try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if(in.readInt()!=0x524d4150)throw new IOException("Unsupported map tile");
            int version=in.readInt();if(version!=1&&version!=2&&version!=3&&version!=4&&version!=5)throw new IOException("Unsupported map tile");
            if(version<3&&Files.size(file)!=(version==1?2056:4360))throw new IOException("Invalid map tile size");
            int[] colors=new int[256],heights=new int[256],ground=new int[256],overlays=new int[256];boolean[] glow=new boolean[256];String[] biomes=new String[256];int[] blockLight=new int[256],skyLight=new int[256];java.util.Arrays.fill(blockLight,-1);java.util.Arrays.fill(skyLight,-1);
            @SuppressWarnings("unchecked") var layers=(java.util.List<MapOverlay>[])new java.util.List<?>[256];
            for(int i=0;i<256;i++){
                colors[i]=in.readInt();heights[i]=in.readInt();
                ground[i]=version==1?heights[i]:in.readInt();overlays[i]=version==1?0xff000000:in.readInt();glow[i]=version>=2&&in.readBoolean();
                if(version>=3){int count=in.readUnsignedByte();if(count!=255){if(count>10)throw new IOException("Too many map overlays");var list=new ArrayList<MapOverlay>();for(int n=0;n<count;n++){int color=in.readInt(),light=in.readUnsignedByte(),opacity=in.readUnsignedByte();if(light>15||opacity>15)throw new IOException("Invalid overlay lighting");int sky=version>=5?in.readByte():-1;if(sky< -1||sky>15)throw new IOException("Invalid sky light");list.add(new MapOverlay(color,light,opacity,sky));}layers[i]=List.copyOf(list);}biomes[i]=in.readUTF();if(biomes[i].length()>256||!biomes[i].isEmpty()&&!biomes[i].matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IOException("Invalid biome");}if(version>=4){blockLight[i]=in.readByte();if(blockLight[i]< -1||blockLight[i]>15)throw new IOException("Invalid block light");}if(version>=5){skyLight[i]=in.readByte();if(skyLight[i]< -1||skyLight[i]>15)throw new IOException("Invalid sky light");}
            }
            if(in.read()!=-1)throw new IOException("Trailing map tile data");
            var tile=new MapTile(colors,heights,ground,overlays,glow,layers);if(version>=3)for(int i=0;i<256;i++){tile.biome(i%16,i/16,biomes[i]);tile.blockLight(i%16,i/16,blockLight[i]);tile.skyLight(i%16,i/16,skyLight[i]);}return Optional.of(tile);
        }
    }
    public void write(String dimension,int x,int z,MapTile tile)throws IOException {write(MapLayer.surface(dimension),x,z,tile);}
    public void write(MapLayer layer,int x,int z,MapTile tile)throws IOException {
        Path file=tile(layer,x,z);Files.createDirectories(file.getParent());
        Path pending=Files.createTempFile(file.getParent(),".tile-",".tmp");
        try {
            try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(pending)))) {
                out.writeInt(0x524d4150);out.writeInt(5);
                for(int z0=0;z0<16;z0++)for(int x0=0;x0<16;x0++){out.writeInt(tile.color(x0,z0));out.writeInt(tile.height(x0,z0));out.writeInt(tile.groundHeight(x0,z0));out.writeInt(tile.overlay(x0,z0));out.writeBoolean(tile.glowing(x0,z0));var layers=tile.layers(x0,z0);out.writeByte(layers==null?255:layers.size());if(layers!=null)for(var overlay:layers){out.writeInt(overlay.color());out.writeByte(overlay.light());out.writeByte(overlay.opacity());out.writeByte(overlay.skyLight());}out.writeUTF(tile.biome(x0,z0));out.writeByte(tile.blockLight(x0,z0));out.writeByte(tile.skyLight(x0,z0));}
            }
            replace(pending,file);
        }finally{Files.deleteIfExists(pending);}
    }
    public List<MapMarker> markers()throws IOException {
        Path file=root.resolve("markers.json");if(!Files.exists(file))return List.of();
        if(Files.size(file)>2*1024*1024)throw new IOException("Map markers too large");
        try {
            var value=Json.read(file);if(value.get("version").getAsInt()!=1)throw new IOException("Unsupported map markers");
            var list=value.getAsJsonArray("markers");if(list.size()>4096)throw new IOException("Too many map markers");
            var result=new ArrayList<MapMarker>();var ids=new HashSet<UUID>();
            for(var element:list){var j=element.getAsJsonObject();var marker=new MapMarker(UUID.fromString(Json.str(j,"id")),Json.str(j,"dimension"),Json.str(j,"name"),j.get("x").getAsInt(),j.get("y").getAsInt(),j.get("z").getAsInt(),j.get("color").getAsInt(),Json.str(j,"icon"),j.has("deathAt")?j.get("deathAt").getAsLong():0,j.has("category")?Json.str(j,"category"):"",!j.has("mapVisible")||j.get("mapVisible").getAsBoolean(),!j.has("minimapVisible")||j.get("minimapVisible").getAsBoolean());if(!ids.add(marker.id()))throw new IOException("Duplicate marker");result.add(marker);}
            return List.copyOf(result);
        }catch(IllegalArgumentException|IllegalStateException|NullPointerException ex){throw new IOException("Invalid map markers",ex);}
    }
    public void markers(List<MapMarker> markers)throws IOException {
        if(markers.size()>4096)throw new IOException("Too many map markers");
        var j=new JsonObject();j.addProperty("version",1);var rows=new JsonArray();var ids=new HashSet<UUID>();
        for(var m:markers){if(!ids.add(m.id()))throw new IOException("Duplicate marker");var row=new JsonObject();row.addProperty("id",m.id().toString());row.addProperty("dimension",m.dimension());row.addProperty("name",m.name());row.addProperty("x",m.x());row.addProperty("y",m.y());row.addProperty("z",m.z());row.addProperty("color",m.color());row.addProperty("icon",m.icon());if(m.death())row.addProperty("deathAt",m.deathAt());row.addProperty("category",m.category());row.addProperty("mapVisible",m.mapVisible());row.addProperty("minimapVisible",m.minimapVisible());rows.add(row);}
        j.add("markers",rows);if(Json.GSON.toJson(j).getBytes(StandardCharsets.UTF_8).length>2*1024*1024)throw new IOException("Map markers too large");Json.write(root.resolve("markers.json"),j);
    }
    /** Compare against the preview base and preserve an exact pre-import backup. */
    public void importMarkers(List<MapMarker> expected,List<MapMarker> additions)throws IOException {
        if(additions.isEmpty())return;
        if(!markers().equals(expected))throw new IOException("Markers changed; reopen import preview");
        var next=new ArrayList<>(expected);next.addAll(additions);
        if(next.size()>4096||next.stream().map(MapMarker::id).distinct().count()!=next.size())throw new IOException("Invalid marker batch");
        Path file=root.resolve("markers.json");
        if(Files.exists(file)){Path backup=root.resolve("backups").resolve("markers-before-import-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".json");Files.createDirectories(backup.getParent());Files.copy(file,backup);}
        markers(next);
    }
    public List<MapCategory> categories()throws IOException{
        Path file=root.resolve("categories.json");if(!Files.exists(file))return List.of();
        if(Files.size(file)>65536)throw new IOException("Map categories too large");
        try{var j=Json.read(file);if(j.get("version").getAsInt()!=1)throw new IOException("Unsupported map categories");var rows=j.getAsJsonArray("categories");if(rows.size()>128)throw new IOException("Too many map categories");var result=new ArrayList<MapCategory>();var ids=new HashSet<UUID>();var names=new HashSet<String>();for(var element:rows){var row=element.getAsJsonObject();var c=new MapCategory(UUID.fromString(Json.str(row,"id")),Json.str(row,"name"),row.get("mapVisible").getAsBoolean(),row.get("minimapVisible").getAsBoolean());if(!ids.add(c.id())||!names.add(c.name().toLowerCase(Locale.ROOT)))throw new IOException("Duplicate map category");result.add(c);}return List.copyOf(result);}catch(IllegalArgumentException|IllegalStateException|NullPointerException ex){throw new IOException("Invalid map categories",ex);}
    }
    public void categories(List<MapCategory> categories)throws IOException{
        if(categories.size()>128)throw new IOException("Too many map categories");var j=new JsonObject();j.addProperty("version",1);var rows=new JsonArray();var ids=new HashSet<UUID>();var names=new HashSet<String>();for(var c:categories){if(!ids.add(c.id())||!names.add(c.name().toLowerCase(Locale.ROOT)))throw new IOException("Duplicate map category");var row=new JsonObject();row.addProperty("id",c.id().toString());row.addProperty("name",c.name());row.addProperty("mapVisible",c.mapVisible());row.addProperty("minimapVisible",c.minimapVisible());rows.add(row);}j.add("categories",rows);Json.write(root.resolve("categories.json"),j);
    }
    public static UUID worldId(Path world)throws IOException {
        Path file=world.resolve("rivet/map-world.json");
        if(Files.exists(file)) {
            if(Files.size(file)>1024)throw new IOException("Invalid map world identity");
            try{return UUID.fromString(Json.str(Json.read(file),"id"));}catch(IllegalArgumentException ex){throw new IOException("Invalid map world identity",ex);}
        }
        UUID id=UUID.randomUUID();var j=new JsonObject();j.addProperty("id",id.toString());Json.write(file,j);return id;
    }
    private static void replace(Path from,Path to)throws IOException {
        try{Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException ex){Files.move(from,to,StandardCopyOption.REPLACE_EXISTING);}
    }
}
