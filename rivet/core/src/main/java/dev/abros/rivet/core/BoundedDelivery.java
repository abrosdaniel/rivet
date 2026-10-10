package dev.abros.rivet.core;
import java.util.concurrent.*;
/** Keeps a lease alive until delivery completes; expired queued callbacks cannot deliver later. */
public final class BoundedDelivery {
 private BoundedDelivery(){}
 public static void run(Executor target,Runnable delivery,long timeoutMillis)throws Exception{
  var guard=new Object();var done=new CompletableFuture<Void>();var cancelled=new boolean[1];
  long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
  target.execute(()->{synchronized(guard){
   if(cancelled[0]||System.nanoTime()>=deadline){done.completeExceptionally(new TimeoutException());return;}
   try{delivery.run();done.complete(null);}catch(Throwable error){done.completeExceptionally(error);}
  }});
  try{done.get(timeoutMillis,TimeUnit.MILLISECONDS);}finally{synchronized(guard){cancelled[0]=true;}}
 }
}
