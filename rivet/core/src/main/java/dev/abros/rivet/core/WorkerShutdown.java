package dev.abros.rivet.core;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Two bounded phases, each sharing one deadline across all module workers. */
public final class WorkerShutdown {
 private WorkerShutdown(){}
 public static boolean stop(Duration grace,Duration forced,ExecutorService... workers){
  long graceNanos=grace.toNanos(),forcedNanos=forced.toNanos();
  if(graceNanos<0||forcedNanos<0)throw new IllegalArgumentException("Negative shutdown timeout");
  boolean interrupted=Thread.interrupted();
  try{
   for(var worker:workers)if(worker!=null)worker.shutdown();
   long deadline=System.nanoTime()+graceNanos;
   if(!interrupted)for(var worker:workers)if(worker!=null){
    try{worker.awaitTermination(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);}
    catch(InterruptedException e){interrupted=true;break;}
   }
   for(var worker:workers)if(worker!=null&&!worker.isTerminated())worker.shutdownNow();
   deadline=System.nanoTime()+forcedNanos;
   for(var worker:workers)if(worker!=null)while(!worker.isTerminated()){
    long remaining=deadline-System.nanoTime();if(remaining<=0)break;
    try{worker.awaitTermination(remaining,TimeUnit.NANOSECONDS);}
    catch(InterruptedException e){interrupted=true;}
   }
   for(var worker:workers)if(worker!=null&&!worker.isTerminated())return false;
   return true;
  }finally{if(interrupted)Thread.currentThread().interrupt();}
 }
}
