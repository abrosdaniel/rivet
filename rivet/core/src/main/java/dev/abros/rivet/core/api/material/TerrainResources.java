package dev.abros.rivet.core.api.material;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Local assets only, no extraction, URLs or symlink traversal. Last source wins. */
public final class TerrainResources implements AutoCloseable {
 private interface Entry {InputStream open()throws IOException;}
 private final Map<String,Entry> files=new TreeMap<>();private final List<ZipFile> zips=new ArrayList<>();
 private String fingerprint;private final Map<String,String> digests=new HashMap<>();
 public TerrainResources(List<Path> sources)throws IOException {
  try {for(Path source:sources){if(Files.isSymbolicLink(source))throw new IOException("Symlink resource source");
   if(Files.isDirectory(source,LinkOption.NOFOLLOW_LINKS)){
    Path assets=source.resolve("assets");if(!Files.isDirectory(assets,LinkOption.NOFOLLOW_LINKS))continue;
    try(var walk=Files.walk(assets)){var it=walk.iterator();int visited=0;while(it.hasNext()){Path p=it.next();if(++visited>200000)throw new IOException("Resource entry limit");if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS))continue;String name=source.relativize(p).toString().replace('\\','/');if(accepted(name))put(name,()->Files.newInputStream(p));}}
   }else if(Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)){
    ZipFile zip=new ZipFile(source.toFile());zips.add(zip);var entries=zip.entries();int visited=0;while(entries.hasMoreElements()){var e=entries.nextElement();if(++visited>200000)throw new IOException("Resource entry limit");if(!e.isDirectory()&&accepted(e.getName()))put(e.getName(),()->zip.getInputStream(e));}
   }
  }
  MessageDigest hash=MessageDigest.getInstance("SHA-256");long total=0;for(var e:files.entrySet()){byte[] data=read(e.getKey());total+=data.length;if(total>512L*1024*1024)throw new IOException("Resource size limit");hash.update(e.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));hash.update((byte)0);hash.update(data);digests.put(e.getKey(),dev.abros.rivet.core.Hashes.sha256(data));}fingerprint=HexFormat.of().formatHex(hash.digest());
  }catch(Exception failure){close();if(failure instanceof IOException e)throw e;throw new IOException("Invalid terrain resources",failure);}
 }
 private void put(String name,Entry entry)throws IOException{if(files.size()>=100000&&!files.containsKey(name))throw new IOException("Resource count limit");files.put(name,entry);}
 private static boolean accepted(String p){return p.matches("assets/[a-z0-9_.-]+/(blockstates|models|textures)/[a-z0-9_./-]+\\.(json|png|mcmeta)")&&!p.contains("..");}
 public byte[] read(String name)throws IOException{var entry=files.get(name);if(entry==null)throw new FileNotFoundException(name);try(var in=entry.open()){byte[] data=in.readNBytes(4*1024*1024+1);if(data.length>4*1024*1024)throw new IOException("Resource too large");String expected=digests.get(name);if(expected!=null&&!expected.equals(dev.abros.rivet.core.Hashes.sha256(data)))throw new IOException("Resources changed; restart renderer");return data;}}
 public boolean contains(String name){return files.containsKey(name);}
 public String fingerprint(){return fingerprint;}public int size(){return files.size();}
 @Override public void close(){for(var zip:zips)try{zip.close();}catch(IOException ignored){}zips.clear();files.clear();}
}
