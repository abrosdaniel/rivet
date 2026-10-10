package dev.abros.rivet.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class CoalescingExecutorTest {
 @Test void repeatedRequestsUseOneSlotAndCanRefreshAgain(){
  var queue=new ArrayList<Runnable>();var work=new CoalescingExecutor<String>(queue::add);var runs=new AtomicInteger();
  assertTrue(work.execute("player",runs::incrementAndGet));
  for(int i=0;i<100;i++)assertFalse(work.execute("player",runs::incrementAndGet));
  assertTrue(work.execute("other",runs::incrementAndGet));assertEquals(2,queue.size());
  queue.removeFirst().run();assertEquals(1,runs.get());assertTrue(work.execute("player",runs::incrementAndGet));
  queue.forEach(Runnable::run);assertEquals(3,runs.get());
 }
 @Test void rejectionAndFailureAllowRetry(){
  var reject=new AtomicBoolean(true);var queue=new ArrayList<Runnable>();
  var work=new CoalescingExecutor<String>(task->{if(reject.get())throw new RejectedExecutionException();queue.add(task);});
  assertThrows(RejectedExecutionException.class,()->work.execute("player",()->{}));reject.set(false);
  assertTrue(work.execute("player",()->{throw new IllegalStateException("test");}));
  assertThrows(IllegalStateException.class,()->queue.removeFirst().run());assertTrue(work.execute("player",()->{}));
 }
 @Test void closingSkipsPendingAndNewLifetimeAcceptsSameKey(){
  var queue=new ArrayList<Runnable>();var old=new CoalescingExecutor<String>(queue::add);var runs=new AtomicInteger();
  old.execute("player",()->fail("closed owner's work"));old.close();old.close();
  assertThrows(RejectedExecutionException.class,()->old.execute("player",()->{}));
  var next=new CoalescingExecutor<String>(queue::add);assertTrue(next.execute("player",runs::incrementAndGet));
  queue.forEach(Runnable::run);assertEquals(1,runs.get());
 }
 @Test void runningKeyCoalescesAndOtherKeysStayIndependent()throws Exception{
  var pool=Executors.newFixedThreadPool(2);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var other=new CountDownLatch(1);
  var work=new CoalescingExecutor<String>(pool);
  try{
   work.execute("player",()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
   assertTrue(entered.await(2,TimeUnit.SECONDS));assertFalse(work.execute("player",()->fail("duplicate")));
   assertTrue(work.execute("other",other::countDown));assertTrue(other.await(2,TimeUnit.SECONDS));
   work.close();assertThrows(RejectedExecutionException.class,()->work.execute("new",()->{}));
  }finally{release.countDown();work.close();pool.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));}
 }
}
