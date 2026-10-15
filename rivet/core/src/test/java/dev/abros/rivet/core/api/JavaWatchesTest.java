package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JavaWatchesTest {
 private static JavaWatches manual(java.util.function.LongSupplier revision,java.util.function.LongSupplier clock){
  return new JavaWatches(revision,clock,()->new ScheduledThreadPoolExecutor(1){
   @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task,long initial,long delay,TimeUnit unit){return super.scheduleWithFixedDelay(task,1,1,TimeUnit.DAYS);}
  });
 }
 @Test void firstSnapshotCoalescesAndFallbackDetectsUnsignalledChanges(){
  var revision=new AtomicLong();var time=new AtomicLong();var value=new AtomicInteger(1);var reads=new AtomicInteger();
  try(var engine=manual(revision::get,time::get)){
   var watch=engine.add("example_mod",()->{reads.incrementAndGet();return CompletableFuture.completedFuture(value.get());});engine.tick();
   var first=(DomainSubscription.Snapshot<Integer>)watch.poll().orElseThrow();assertEquals(1,first.value());
   engine.tick();assertEquals(1,reads.get());assertTrue(watch.poll().isEmpty());
   value.set(2);revision.incrementAndGet();engine.tick();value.set(3);revision.incrementAndGet();engine.tick();
   var latest=(DomainSubscription.Snapshot<Integer>)watch.poll().orElseThrow();assertEquals(3,latest.value());assertEquals(3,latest.cursor().sequence());assertEquals(first.cursor().stream(),latest.cursor().stream());
   value.set(4);time.set(TimeUnit.SECONDS.toNanos(5));engine.tick();assertEquals(4,((DomainSubscription.Snapshot<Integer>)watch.poll().orElseThrow()).value());
  }
 }
 @Test void accessLossReplacesUndeliveredPrivateSnapshotAndFailureDoesNotStopOthers(){
  var revision=new AtomicLong();var permitted=new AtomicBoolean(true);
  try(var engine=manual(revision::get,()->0)){
   var privateWatch=engine.add("example_mod",()->permitted.get()?CompletableFuture.completedFuture("secret"):CompletableFuture.<String>failedFuture(new ApiException(ApiException.Code.NOT_FOUND,"Hidden")));
   var other=engine.add("other_mod",()->CompletableFuture.completedFuture(7));engine.tick();
   permitted.set(false);revision.incrementAndGet();engine.tick();
   var ended=(DomainSubscription.Closed<String>)privateWatch.poll().orElseThrow();assertEquals(Optional.of(ApiException.Code.NOT_FOUND),ended.error());assertTrue(privateWatch.poll().isEmpty());assertTrue(privateWatch.isClosed());
   assertEquals(7,((DomainSubscription.Snapshot<Integer>)other.poll().orElseThrow()).value());assertFalse(other.isClosed());
  }
 }
 @Test void singleInflightReadAndCloseSuppressLateResults(){
  var future=new CompletableFuture<String>();var calls=new AtomicInteger();var revision=new AtomicLong();
  try(var engine=manual(revision::get,()->0)){
   var watch=engine.add("example_mod",()->{calls.incrementAndGet();return future;});engine.tick();revision.incrementAndGet();engine.tick();assertEquals(1,calls.get());
   watch.close();assertTrue(future.isCancelled());assertFalse(future.complete("late"));assertEquals(Optional.empty(),((DomainSubscription.Closed<String>)watch.poll().orElseThrow()).error());
   watch.close();engine.tick();assertTrue(watch.poll().isEmpty());assertEquals(1,calls.get());
  }
 }
 @Test void perModAndGlobalLimitsAreReleasedAndShutdownInvalidatesHandles(){
  var engine=manual(()->0,()->0);var all=new ArrayList<DomainSubscription<Integer>>();
  try{
   for(int mod=0;mod<8;mod++)for(int i=0;i<8;i++)all.add(engine.add("mod_"+mod,CompletableFuture::new));
   assertEquals(ApiException.Code.BUSY,assertThrows(ApiException.class,()->engine.add("new_mod",CompletableFuture::new)).code());
   all.getFirst().close();all.set(0,engine.add("mod_0",CompletableFuture::new));
   assertEquals(ApiException.Code.BUSY,assertThrows(ApiException.class,()->engine.add("mod_0",CompletableFuture::new)).code());
   engine.close();for(var watch:all){assertTrue(watch.isClosed());assertEquals(Optional.of(ApiException.Code.UNAVAILABLE),((DomainSubscription.Closed<Integer>)watch.poll().orElseThrow()).error());}
   assertEquals(ApiException.Code.UNAVAILABLE,assertThrows(ApiException.class,()->engine.add("new_mod",CompletableFuture::new)).code());
  }finally{engine.close();}
 }
 @Test void lastCloseAllowsFreshIndependentSubscription(){
  try(var engine=manual(()->0,()->0)){
   var first=engine.add("example_mod",()->CompletableFuture.completedFuture(1));engine.tick();var a=(DomainSubscription.Snapshot<Integer>)first.poll().orElseThrow();first.close();
   var second=engine.add("example_mod",()->CompletableFuture.completedFuture(1));engine.tick();var b=(DomainSubscription.Snapshot<Integer>)second.poll().orElseThrow();assertNotEquals(a.cursor().stream(),b.cursor().stream());assertEquals(1,b.cursor().sequence());
  }
 }
}
