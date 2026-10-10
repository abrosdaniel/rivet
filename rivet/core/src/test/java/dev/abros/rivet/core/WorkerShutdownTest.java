package dev.abros.rivet.core;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class WorkerShutdownTest {
 private static final Duration WAIT=Duration.ofSeconds(2);
 @Test void drainsAcceptedWorkAndRejectsNewSubmissions(){
  var pool=Executors.newSingleThreadExecutor();var ran=new AtomicBoolean();
  try{pool.execute(()->ran.set(true));assertTrue(WorkerShutdown.stop(WAIT,WAIT,null,pool));assertTrue(ran.get());assertTrue(pool.isTerminated());assertThrows(RejectedExecutionException.class,()->pool.execute(()->{}));}
  finally{pool.shutdownNow();}
 }
 @Test void waitsForCleanupAfterForcedInterruption()throws Exception{
  var pool=Executors.newSingleThreadExecutor();var stopper=Executors.newSingleThreadExecutor();
  var entered=new CountDownLatch(1);var cleaning=new CountDownLatch(1);var releaseCleanup=new CountDownLatch(1);var cleanup=new AtomicBoolean();
  try{
   pool.execute(()->{entered.countDown();try{new CountDownLatch(1).await();}catch(InterruptedException e){
    cleaning.countDown();try{releaseCleanup.await();cleanup.set(true);}catch(InterruptedException again){Thread.currentThread().interrupt();}
   }});
   assertTrue(entered.await(2,TimeUnit.SECONDS));var stopped=stopper.submit(()->WorkerShutdown.stop(Duration.ZERO,WAIT,pool));
   assertTrue(cleaning.await(2,TimeUnit.SECONDS));assertFalse(stopped.isDone());
   releaseCleanup.countDown();assertTrue(stopped.get(3,TimeUnit.SECONDS));assertTrue(cleanup.get());assertTrue(pool.isTerminated());
  }finally{releaseCleanup.countDown();pool.shutdownNow();stopper.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));assertTrue(stopper.awaitTermination(2,TimeUnit.SECONDS));}
 }
 @Test void alreadyInterruptedCallerStillWaitsAndRestoresFlag()throws Exception{
  var pool=Executors.newSingleThreadExecutor();var entered=new CountDownLatch(1);var cleanup=new AtomicBoolean();
  try{
   pool.execute(()->{entered.countDown();try{new CountDownLatch(1).await();}catch(InterruptedException e){cleanup.set(true);}});
   assertTrue(entered.await(2,TimeUnit.SECONDS));Thread.currentThread().interrupt();
   assertTrue(WorkerShutdown.stop(WAIT,WAIT,pool));assertTrue(Thread.currentThread().isInterrupted());assertTrue(cleanup.get());
  }finally{Thread.interrupted();pool.shutdownNow();}
 }
 @Test void reportsUncooperativeWorkerWithoutWaitingForever()throws Exception{
  var pool=Executors.newSingleThreadExecutor();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
  try{
   pool.execute(()->{entered.countDown();while(release.getCount()>0)try{release.await();}catch(InterruptedException ignored){}});
   assertTrue(entered.await(2,TimeUnit.SECONDS));assertFalse(WorkerShutdown.stop(Duration.ZERO,Duration.ofMillis(20),pool));
  }finally{release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(2,TimeUnit.SECONDS));}
 }
}
