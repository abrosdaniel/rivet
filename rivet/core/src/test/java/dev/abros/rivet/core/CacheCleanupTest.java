package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
class CacheCleanupTest {
 @TempDir Path game;
 Path object(Cache cache,String content,boolean old)throws Exception{
  String hash=Hashes.sha256(content.getBytes());Path file=cache.path(hash);Files.createDirectories(file.getParent());Files.writeString(file,content);if(old)age(file);return file;
 }
 void age(Path file)throws Exception{Files.setLastModifiedTime(file,FileTime.from(Instant.now().minus(Duration.ofDays(8))));}
 @Test void startupRemovesOnlyOldUnusedObjectsAndPartials()throws Exception{
  var cache=new Cache(game,new Remote());var active=object(cache,"active",true);var unused=object(cache,"unused",true);var recent=object(cache,"recent",false);
  var partial=unused.resolveSibling(unused.getFileName()+".pack.part");Files.writeString(partial,"part");age(partial);
  var saved=game.resolve("rivet/pack-settings/saved");Files.createDirectories(saved.getParent());Files.writeString(saved,"preference");
  Json.write(game.resolve("rivet/state.json"),Map.of("ownership",Map.of("mods/active.jar",Map.of("hash",active.getFileName().toString()))));
  new Hub(game,"1.3.2","21.1.250");
  assertTrue(Files.exists(active));assertTrue(Files.exists(recent));assertTrue(Files.exists(saved));assertFalse(Files.exists(unused));assertFalse(Files.exists(partial));
 }
 @Test void pendingOrRecoverySkipsCleanupAndPreservesBackups()throws Exception{
  var cache=new Cache(game,new Remote());var unused=object(cache,"unused",true);
  Json.write(game.resolve("rivet/pending.json"),Map.of("id",UUID.randomUUID().toString()));assertEquals(0,cache.cleanup(game));assertTrue(Files.exists(unused));Files.delete(game.resolve("rivet/pending.json"));
  Path dir=game.resolve("rivet/transactions/"+UUID.randomUUID());Files.createDirectories(dir.resolve("preimages"));Files.writeString(dir.resolve("preimages/0"),"backup");Json.write(dir.resolve("journal.json"),Map.of("status","RECOVERY_REQUIRED"));
  assertEquals(0,cache.cleanup(game));assertTrue(Files.exists(unused));assertEquals("backup",Files.readString(dir.resolve("preimages/0")));
 }
 @Test void corruptStateFailsClosedWithoutPreventingHubStartup()throws Exception{
  var cache=new Cache(game,new Remote());var unused=object(cache,"unused",true);Files.writeString(game.resolve("rivet/state.json"),"invalid");
  assertThrows(java.io.IOException.class,()->cache.cleanup(game));assertDoesNotThrow(()->new Hub(game,"1.3.2","21.1.250"));assertTrue(Files.exists(unused));
 }
 @Test void cleanupDoesNotDeleteAnObjectHeldByPackDownloadLease()throws Exception{
  var cache=new Cache(game,new Remote());var file=object(cache,"leased",true);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  try(var executor=Executors.newSingleThreadExecutor()){
   var download=executor.submit(()->{try{cache.write(file.getFileName().toString(),new AtomicBoolean(),path->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new java.io.IOException("timeout");}catch(InterruptedException ex){throw new java.io.IOException(ex);}});}catch(Exception ex){throw new RuntimeException(ex);}});
   try{assertTrue(entered.await(5,TimeUnit.SECONDS));assertEquals(0,cache.cleanup(game));assertTrue(Files.exists(file));}finally{release.countDown();}download.get(5,TimeUnit.SECONDS);
  }
 }
}
