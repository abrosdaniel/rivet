package dev.abros.rivet.server;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class TaskStockCompletionTest {
 private final TaskStockWork work=new TaskStockWork();
 @Test void secondSignEditIsQueuedAndCannotBeOvertakenByStockRemoval(){
  var workers=new java.util.ArrayList<Runnable>();var lane=new TaskStockWork(workers::add);var saved=new java.util.ArrayList<String>();
  lane.schedule("sign",()->saved.add("first binding"));lane.schedule("sign",()->saved.add("second binding"));lane.schedule("sign",()->saved.add("removed"));
  assertEquals(1,workers.size());workers.getFirst().run();assertEquals(java.util.List.of("first binding","second binding","removed"),saved);
 }
 @Test void lateCompletionCannotReleaseNewRunsPendingStockOrDeliverMessage()throws Exception{
  work.reset();long old=work.generation();work.reset();work.begin("stock");var delivered=new AtomicInteger();
  try{
   work.complete(old,"stock",delivered::incrementAndGet);
   assertTrue(work.pending("stock"));assertEquals(0,delivered.get());
   work.complete(work.generation(),"stock",delivered::incrementAndGet);
   assertFalse(work.pending("stock"));assertEquals(1,delivered.get());
  }finally{work.reset();}
 }
 @Test void failedDeliveryStillReleasesCurrentPendingStock()throws Exception{
  work.reset();work.begin("stock");long token=work.generation();
  try{
   assertThrows(IllegalStateException.class,()->work.complete(token,"stock",()->{throw new IllegalStateException("delivery failed");}));
   assertFalse(work.pending("stock"));
  }finally{work.reset();}
 }
}
