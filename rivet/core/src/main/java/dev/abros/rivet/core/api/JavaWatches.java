package dev.abros.rivet.core.api;

import dev.abros.rivet.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Bounded current-state subscriptions; no consumer code, retained event history or blocking IO here. */
final class JavaWatches implements AutoCloseable {
 private final LongSupplier revision,nanos;private final Supplier<ScheduledExecutorService> timers;
 private final Set<Watch<?>> watches=new HashSet<>();
 private ScheduledExecutorService timer;private boolean closed;
 JavaWatches(LongSupplier revision){this(revision,System::nanoTime);}
 JavaWatches(LongSupplier revision,LongSupplier nanos){this(revision,nanos,()->Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"Rivet Java subscriptions");t.setDaemon(true);return t;}));}
 JavaWatches(LongSupplier revision,LongSupplier nanos,Supplier<ScheduledExecutorService> timers){this.revision=revision;this.nanos=nanos;this.timers=timers;}
 synchronized <T> DomainSubscription<T> add(String mod,Supplier<CompletableFuture<T>> read){
  if(closed)throw new ApiException(ApiException.Code.UNAVAILABLE,"API stopped");
  if(watches.size()>=64||watches.stream().filter(w->w.mod.equals(mod)).count()>=8)
   throw new ApiException(ApiException.Code.BUSY,"Subscription capacity unavailable");
  var watch=new Watch<T>(mod,read);watches.add(watch);
  if(timer==null){timer=timers.get();timer.scheduleWithFixedDelay(this::tick,0,1,TimeUnit.SECONDS);}
  return watch;
 }
 void tick(){
  List<Watch<?>> current;synchronized(this){if(closed)return;current=List.copyOf(watches);}
  for(var watch:current)watch.refresh();
 }
 private synchronized void remove(Watch<?> watch){watches.remove(watch);if(watches.isEmpty()&&timer!=null){timer.shutdownNow();timer=null;}}
 @Override public void close(){
  List<Watch<?>> current;synchronized(this){closed=true;current=List.copyOf(watches);if(timer!=null){timer.shutdownNow();timer=null;}}
  for(var watch:current)watch.finish(Optional.of(ApiException.Code.UNAVAILABLE));
 }
 private final class Watch<T> implements DomainSubscription<T> {
  final String mod;final UUID stream=UUID.randomUUID();Supplier<CompletableFuture<T>> read;
  CompletableFuture<T> inFlight;Event<T> pending;T previous;long sequence,seenRevision,lastRead;boolean stopped,reading;
  Watch(String mod,Supplier<CompletableFuture<T>> read){this.mod=mod;this.read=read;}
  void refresh(){
   CompletableFuture<T> request;
   synchronized(this){
    long now=nanos.getAsLong(),version=revision.getAsLong();
    if(stopped||reading||sequence>0&&version==seenRevision&&now-lastRead<TimeUnit.SECONDS.toNanos(5))return;
    reading=true;seenRevision=version;lastRead=now;
    try{request=Objects.requireNonNull(read.get());inFlight=request;}
    catch(RuntimeException error){reading=false;request=CompletableFuture.failedFuture(error);}
   }
   request.whenComplete((value,error)->{
    if(error!=null){Throwable cause=error;while((cause instanceof CompletionException||cause instanceof ExecutionException)&&cause.getCause()!=null)cause=cause.getCause();finish(Optional.of(cause instanceof ApiException api?api.code():ApiException.Code.UNAVAILABLE));return;}
    synchronized(this){
     inFlight=null;reading=false;if(stopped)return;
     if(value==null){finish(Optional.of(ApiException.Code.UNAVAILABLE));return;}
     if(sequence==0||!Objects.equals(previous,value)){previous=value;pending=new Snapshot<>(new ApiCursor(stream,++sequence),value);}
    }
   });
  }
  public synchronized Optional<Event<T>> poll(){var event=pending;pending=null;return Optional.ofNullable(event);}
  public synchronized boolean isClosed(){return stopped;}
  public void close(){finish(Optional.empty());}
  void finish(Optional<ApiException.Code> error){
   CompletableFuture<T> cancel;
   synchronized(this){if(stopped)return;stopped=true;pending=new Closed<>(error);previous=null;read=null;cancel=inFlight;inFlight=null;}
   remove(this);if(cancel!=null)cancel.cancel(false);
  }
 }
}
