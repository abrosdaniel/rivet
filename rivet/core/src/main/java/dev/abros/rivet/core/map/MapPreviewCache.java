package dev.abros.rivet.core.map;

import dev.abros.rivet.core.Hashes;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;

/** Disposable region previews, never a source of exploration. All calls belong on the map IO worker. */
public final class MapPreviewCache {
    private static final int PIXELS=4096, MAGIC=0x52505631, LIMIT=2048;
    private final Path root;
    private int writes;
    public MapPreviewCache(Path mapDirectory){root=mapDirectory.resolve("previews-v1");}
    private Path file(MapLayer layer,int x,int z,int level){
        if(level<0||level>4)throw new IllegalArgumentException("Invalid preview level");
        String key=layer.dimension()+"|"+layer.band()+"|"+level+"|"+x+"|"+z;
        return root.resolve(Hashes.sha256(key.getBytes(StandardCharsets.UTF_8))+".preview");
    }
    public Optional<int[]> read(MapLayer layer,int x,int z,int level)throws IOException{
        Path file=file(layer,x,z,level);
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)!=12+PIXELS*4)return Optional.empty();
        byte[] bytes;
        try(var in=Files.newInputStream(file)){bytes=in.readNBytes(13+PIXELS*4);}
        if(bytes.length!=12+PIXELS*4)return Optional.empty();
        var data=ByteBuffer.wrap(bytes);if(data.getInt()!=MAGIC)return Optional.empty();
        var crc=new CRC32();crc.update(bytes,4,PIXELS*4);
        int[] pixels=new int[PIXELS];for(int i=0;i<PIXELS;i++)pixels[i]=data.getInt();
        return data.getLong()==crc.getValue()?Optional.of(pixels):Optional.empty();
    }
    public void write(MapLayer layer,int x,int z,int level,int[] pixels)throws IOException{
        if(pixels.length!=PIXELS)throw new IllegalArgumentException("Invalid preview size");
        Path file=file(layer,x,z,level);Files.createDirectories(root);
        var data=ByteBuffer.allocate(12+PIXELS*4);data.putInt(MAGIC);for(int pixel:pixels)data.putInt(pixel);
        var crc=new CRC32();crc.update(data.array(),4,PIXELS*4);data.putLong(crc.getValue());
        Path temporary=Files.createTempFile(root,".preview-",".tmp");
        try{Files.write(temporary,data.array());try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}}
        finally{Files.deleteIfExists(temporary);}
        // At most 31 additional pages (~0.5 MiB) between bounded cleanups.
        if(writes++%32==0)trim();
    }
    private void trim()throws IOException{
        record Entry(Path path,long modified){}
        var files=new ArrayList<Entry>();
        try(var stream=Files.newDirectoryStream(root,"*.preview")){for(var path:stream)files.add(new Entry(path,Files.getLastModifiedTime(path,LinkOption.NOFOLLOW_LINKS).toMillis()));}
        files.sort(Comparator.comparingLong(Entry::modified));
        for(int i=0;i<files.size()-LIMIT;i++)Files.deleteIfExists(files.get(i).path());
    }
}
