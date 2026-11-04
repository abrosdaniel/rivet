package dev.abros.rivet.core;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Pause an owner after a failed durable write; resume only after a successful retry. */
public final class CheckpointRecovery {
 private boolean paused;private long next;private int attempts;
 private CompletableFuture<Void> retry;
 public boolean ready(CompletableFuture<Void> checkpoint,long now,Supplier<CompletableFuture<Void>> write,Consumer<Throwable> failure){
  if(!paused){
   if(checkpoint==null||!checkpoint.isCompletedExceptionally())return true;
   paused=true;attempts=0;next=now+5000;
   try{checkpoint.join();}catch(RuntimeException error){failure.accept(error);}
  }
  if(retry!=null){
   if(!retry.isDone())return false;
   if(!retry.isCompletedExceptionally()&&!retry.isCancelled()){var completed=retry;paused=false;retry=null;return checkpoint==completed||ready(checkpoint,now,write,failure);}
   retry=null;next=now+Math.min(300000L,5000L<<Math.min(6,++attempts));
  }
  if(now>=next){try{retry=write.get();}catch(RuntimeException error){retry=CompletableFuture.failedFuture(error);}}
  return false;
 }
 public void reset(){paused=false;retry=null;attempts=0;next=0;}
}
