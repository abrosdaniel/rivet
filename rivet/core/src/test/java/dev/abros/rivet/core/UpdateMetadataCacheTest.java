package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class UpdateMetadataCacheTest {
 @TempDir Path root;
 final String url="https://api.github.com/repos/abrosdaniel/rivet/releases";
 static class Source extends Remote {
  final AtomicInteger calls=new AtomicInteger();volatile boolean failed,interrupted;volatile String body="[]";
  @Override public byte[] bytes(String url,int limit)throws IOException,InterruptedException {calls.incrementAndGet();if(interrupted)throw new InterruptedException();if(failed)throw new HttpFailure(429,java.net.URI.create(url));return body.getBytes(java.nio.charset.StandardCharsets.UTF_8);}
 }
 @Test void freshCacheSurvivesRestartAndExpires()throws Exception{
  var now=new AtomicLong(1_000_000);var source=new Source();var cache=new UpdateMetadataCache(root,now::get);
  cache.read(source,url,1024);new UpdateMetadataCache(root,now::get).read(source,url,1024);assertEquals(1,source.calls.get());
  now.addAndGet(UpdateMetadataCache.FRESH);source.body="[1]";assertEquals("[1]",new String(cache.read(source,url,1024)));assertEquals(2,source.calls.get());
 }
 @Test void failureUsesStaleDataAndBacksOffAcrossRestart()throws Exception{
  var now=new AtomicLong(1_000_000);var source=new Source();var cache=new UpdateMetadataCache(root,now::get);cache.read(source,url,1024);now.addAndGet(UpdateMetadataCache.FRESH);source.failed=true;
  assertEquals("[]",new String(cache.read(source,url,1024)));new UpdateMetadataCache(root,now::get).read(source,url,1024);assertEquals(2,source.calls.get());
  now.addAndGet(UpdateMetadataCache.RETRY);cache.read(source,url,1024);assertEquals(3,source.calls.get());
 }
 @Test void emptyCacheFailureBacksOffThenRecovers()throws Exception{
  var now=new AtomicLong(1_000_000);var source=new Source();source.failed=true;var cache=new UpdateMetadataCache(root,now::get);
  assertThrows(IOException.class,()->cache.read(source,url,1024));assertThrows(IOException.class,()->new UpdateMetadataCache(root,now::get).read(source,url,1024));assertEquals(1,source.calls.get());
  now.addAndGet(UpdateMetadataCache.RETRY);source.failed=false;cache.read(source,url,1024);assertEquals(2,source.calls.get());
 }
 @Test void concurrentReadersShareOneRequest()throws Exception{
  var source=new Source();var cache=new UpdateMetadataCache(root,()->1_000_000L);var executor=Executors.newFixedThreadPool(4);
  try{var jobs=new java.util.ArrayList<Future<byte[]>>();for(int i=0;i<8;i++)jobs.add(executor.submit(()->cache.read(source,url,1024)));for(var job:jobs)assertEquals("[]",new String(job.get()));assertEquals(1,source.calls.get());}finally{executor.shutdownNow();}
 }
 @Test void expiredCacheAndCancellationAreNotHidden()throws Exception{
  var now=new AtomicLong(1_000_000);var source=new Source();var cache=new UpdateMetadataCache(root,now::get);cache.read(source,url,1024);now.addAndGet(UpdateMetadataCache.MAX_AGE+1);source.failed=true;assertThrows(IOException.class,()->cache.read(source,url,1024));
  now.addAndGet(UpdateMetadataCache.RETRY);source.failed=false;source.interrupted=true;assertThrows(InterruptedException.class,()->cache.read(source,url,1024));
 }
 @Test void malformedCacheIsReplaced()throws Exception{
  var now=new AtomicLong(1_000_000);var source=new Source();var cache=new UpdateMetadataCache(root,now::get);cache.read(source,url,1024);try(var files=Files.list(root)){Files.writeString(files.findFirst().orElseThrow(),"broken");}new UpdateMetadataCache(root,now::get).read(source,url,1024);assertEquals(2,source.calls.get());
 }
 @Test void unwritableCacheStillThrottlesFailuresInMemory()throws Exception{
  Path file=root.resolve("not-a-directory");Files.writeString(file,"keep");var source=new Source();source.failed=true;var cache=new UpdateMetadataCache(file,()->1_000_000L);
  assertThrows(IOException.class,()->cache.read(source,url,1024));assertThrows(IOException.class,()->cache.read(source,url,1024));assertEquals(1,source.calls.get());assertEquals("keep",Files.readString(file));
 }
 @Test void unwritableCacheKeepsSuccessfulResultInMemory()throws Exception{
  Path file=root.resolve("not-a-directory");Files.writeString(file,"keep");var source=new Source();var cache=new UpdateMetadataCache(file,()->1_000_000L);
  cache.read(source,url,1024);cache.read(source,url,1024);assertEquals(1,source.calls.get());
 }

}
