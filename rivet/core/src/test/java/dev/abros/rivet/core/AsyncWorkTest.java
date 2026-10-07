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
}
