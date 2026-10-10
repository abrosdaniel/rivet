package dev.abros.rivet.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class KeyedSerialExecutorTest {
 @Test void preservesOwnerOrderWithoutBlockingAnotherOwner()throws Exception{
  var pool=Executors.newFixedThreadPool(2);var work=new KeyedSerialExecutor<String>(pool,4);
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var other=new CountDownLatch(1);var done=new CountDownLatch(2);var order=new CopyOnWriteArrayList<Integer>();
  try{
   work.execute("a",()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}order.add(1);done.countDown();});
   assertTrue(entered.await(2,TimeUnit.SECONDS));work.execute("a",()->{order.add(2);done.countDown();});
   work.execute("b",other::countDown);assertTrue(other.await(2,TimeUnit.SECONDS));assertTrue(order.isEmpty());
   release.countDown();assertTrue(done.await(2,TimeUnit.SECONDS));assertEquals(List.of(1,2),order);
  }finally{release.countDown();work.close();pool.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));}
 }
 @Test void capacityIsSharedAcrossOwnersAndReleasedAfterDrain(){
  var queue=new ArrayList<Runnable>();var work=new KeyedSerialExecutor<String>(queue::add,3);var runs=new AtomicInteger();
  work.execute("a",runs::incrementAndGet);work.execute("a",runs::incrementAndGet);work.execute("b",runs::incrementAndGet);
  assertEquals(2,queue.size());assertThrows(RejectedExecutionException.class,()->work.execute("c",()->{}));
  queue.removeFirst().run();assertEquals(2,runs.get());work.execute("a",runs::incrementAndGet);work.execute("c",runs::incrementAndGet);
  assertThrows(RejectedExecutionException.class,()->work.execute("b",()->{}));queue.forEach(Runnable::run);assertEquals(5,runs.get());
 }
 @Test void rejectionAndTaskFailureDoNotStrandOwner(){
  var queue=new ArrayList<Runnable>();var reject=new AtomicBoolean(true);var work=new KeyedSerialExecutor<String>(r->{if(reject.get())throw new RejectedExecutionException();queue.add(r);},2);
  assertThrows(RejectedExecutionException.class,()->work.execute("a",()->{}));reject.set(false);
  work.execute("a",()->{throw new IllegalArgumentException("test");});var ran=new AtomicBoolean();work.execute("a",()->ran.set(true));queue.removeFirst().run();assertTrue(ran.get());
  work.execute("a",()->{});assertEquals(1,queue.size());queue.removeFirst().run();
 }
 @Test void closeDropsPendingButLetsActiveFinish()throws Exception{
  var pool=Executors.newSingleThreadExecutor();var work=new KeyedSerialExecutor<String>(pool,3);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new AtomicBoolean();var pending=new AtomicInteger();
  try{
   work.execute("a",()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}finished.set(true);});
   assertTrue(entered.await(2,TimeUnit.SECONDS));work.execute("a",pending::incrementAndGet);work.execute("b",pending::incrementAndGet);work.close();work.close();
   assertThrows(RejectedExecutionException.class,()->work.execute("c",()->{}));release.countDown();pool.shutdown();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));assertTrue(finished.get());assertEquals(0,pending.get());
  }finally{release.countDown();work.close();pool.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));}
 }
}
