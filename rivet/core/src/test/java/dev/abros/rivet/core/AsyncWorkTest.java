package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class AsyncWorkTest {
 @Test void separateSessionsRunWhileOneIsSlowAndPreserveOrder()throws Exception{
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var done=new CountDownLatch(3);var order=new CopyOnWriteArrayList<Integer>();
  try(var pool=Executors.newFixedThreadPool(2)){
   var first=new SerialExecutor(pool,4);var second=new SerialExecutor(pool,4);
   first.execute(()->{entered.countDown();try{release.await(3,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}order.add(1);done.countDown();});
   assertTrue(entered.await(1,TimeUnit.SECONDS));first.execute(()->{order.add(2);done.countDown();});
   var fast=new CountDownLatch(1);second.execute(()->{fast.countDown();done.countDown();});assertTrue(fast.await(1,TimeUnit.SECONDS));release.countDown();assertTrue(done.await(2,TimeUnit.SECONDS));assertEquals(List.of(1,2),order);
  }finally{release.countDown();}
 }
 @Test void boundedSessionRejectsOverloadWithoutDroppingAcceptedWork(){
  var workers=new ArrayList<Runnable>();var serial=new SerialExecutor(workers::add,2);var done=new AtomicInteger();serial.execute(done::incrementAndGet);serial.execute(done::incrementAndGet);assertThrows(RejectedExecutionException.class,()->serial.execute(done::incrementAndGet));workers.getFirst().run();assertEquals(2,done.get());serial.execute(done::incrementAndGet);workers.getLast().run();assertEquals(3,done.get());
 }
 @Test void closeDiscardsFullQueueAndCleansOnceBeforeWorkerStarts(){
  var workers=new ArrayList<Runnable>();var serial=new SerialExecutor(workers::add,2);var ran=new AtomicInteger();var cleaned=new AtomicInteger();
  serial.execute(ran::incrementAndGet);serial.execute(ran::incrementAndGet);
  serial.close(cleaned::incrementAndGet);serial.close(()->fail("duplicate cleanup"));
  assertEquals(1,cleaned.get());assertThrows(RejectedExecutionException.class,()->serial.execute(ran::incrementAndGet));
  workers.getFirst().run();assertEquals(0,ran.get());assertEquals(1,cleaned.get());
 }
 @Test void closeWaitsForActiveTaskAndDropsLateCallback()throws Exception{
  var pool=Executors.newSingleThreadExecutor();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var cleaned=new CountDownLatch(1);var order=new CopyOnWriteArrayList<String>();
  try{
   var serial=new SerialExecutor(pool,1);
   serial.execute(()->{entered.countDown();try{assertTrue(release.await(3,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}order.add("active");});
   assertTrue(entered.await(2,TimeUnit.SECONDS));serial.execute(()->order.add("pending"));
   serial.close(()->{order.add("cleanup");cleaned.countDown();});assertEquals(1,cleaned.getCount());
   assertThrows(RejectedExecutionException.class,()->serial.execute(()->order.add("late")));
   release.countDown();assertTrue(cleaned.await(2,TimeUnit.SECONDS));assertEquals(List.of("active","cleanup"),order);
  }finally{release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(3,TimeUnit.SECONDS));}
 }
 @Test void closedQueueCleansEvenWhenUnderlyingPoolRejects(){
  var pool=Executors.newSingleThreadExecutor();pool.shutdown();var serial=new SerialExecutor(pool,1);var cleaned=new AtomicInteger();
  assertThrows(RejectedExecutionException.class,()->serial.execute(()->fail("rejected work")));
  serial.close(cleaned::incrementAndGet);assertEquals(1,cleaned.get());
 }
 @Test void taskCanCloseItsOwnQueueAndStillRunCleanupAfterFailure(){
  var workers=new ArrayList<Runnable>();var serial=new SerialExecutor(workers::add,2);var order=new ArrayList<String>();
  serial.execute(()->{serial.close(()->order.add("cleanup"));order.add("active");throw new IllegalStateException("test");});
  serial.execute(()->order.add("pending"));workers.getFirst().run();assertEquals(List.of("active","cleanup"),order);
 }
}
