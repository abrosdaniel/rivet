package dev.abros.rivet.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Persistent metadata only; downloaded JARs still require their declared hash. */
final class UpdateMetadataCache {
 static final long FRESH=15*60*1000L,RETRY=5*60*1000L,MAX_AGE=7*24*60*60*1000L;
 private final Path directory;private final LongSupplier clock;
 private final Map<String,Object> locks=new ConcurrentHashMap<>();
 private record Snapshot(long fetched,long retry,byte[] body) {}
 private final Map<Path,Snapshot> memory=new ConcurrentHashMap<>();
 UpdateMetadataCache(Path directory){this(directory,System::currentTimeMillis);}
 UpdateMetadataCache(Path directory,LongSupplier clock){this.directory=directory;this.clock=clock;}
 byte[] read(Remote remote,String url,int limit)throws IOException,InterruptedException {
  String key=Hashes.sha256(url.getBytes(StandardCharsets.UTF_8));
  synchronized(locks.computeIfAbsent(key,ignored->new Object())){
   Path file=directory.resolve(key+".json");long now=clock.getAsLong(),fetched=0,retry=0;byte[] cached=null;
   try{
    var snapshot=memory.get(file);
    if(snapshot!=null){fetched=snapshot.fetched();retry=snapshot.retry();byte[] bytes=snapshot.body();if(bytes!=null&&bytes.length<=limit&&fetched<=now&&now-fetched<=MAX_AGE)cached=bytes;}
    else if(Files.exists(file)&&Files.size(file)<=2L*limit+4096){var record=Json.read(file);Json.keys(record,"url","fetched","retry","body");
     if(!Json.str(record,"url").equals(url))throw new IOException("Cache source mismatch");
     fetched=record.get("fetched").getAsLong();retry=record.get("retry").getAsLong();String body=Json.str(record,"body");
     if(!body.isEmpty()){var bytes=Base64.getDecoder().decode(body);if(bytes.length<=limit&&fetched<=now&&now-fetched<=MAX_AGE)cached=bytes;}
    }
   }catch(IOException|RuntimeException invalid){cached=null;fetched=0;retry=0;}
   if(cached!=null&&now-fetched<FRESH)return cached;
   if(retry>now&&retry-now<=RETRY){if(cached!=null)return cached;throw new Remote.Unavailable("Проверка обновлений временно недоступна. Повторите через несколько минут.");}
   try{
    byte[] bytes=remote.bytes(url,limit);
    com.google.gson.JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8));
    save(file,url,clock.getAsLong(),0,bytes);return bytes;
   }catch(InterruptedException cancelled){throw cancelled;}
   catch(IOException|RuntimeException failure){
    save(file,url,fetched,clock.getAsLong()+RETRY,cached);
    if(cached!=null)return cached;
    if(failure instanceof IOException io)throw io;throw new IOException("Invalid update metadata",failure);
   }
  }
 }
 private void save(Path file,String url,long fetched,long retry,byte[] bytes){
  memory.put(file,new Snapshot(fetched,retry,bytes==null?null:bytes.clone()));
  try{Json.write(file,Map.of("url",url,"fetched",fetched,"retry",retry,"body",bytes==null?"":Base64.getEncoder().encodeToString(bytes)));}catch(IOException ignored){/* Read-only storage must not prevent an update check. */}
 }
}
