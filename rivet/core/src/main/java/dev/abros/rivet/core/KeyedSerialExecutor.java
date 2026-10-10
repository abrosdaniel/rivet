package dev.abros.rivet.core;

import java.util.*;
import java.util.concurrent.*;

/** Ordered operations per key with a single bound across every queued and active operation. */
public final class KeyedSerialExecutor<K> implements AutoCloseable {
 private final Executor executor;private final int limit;
 private final Map<K,ArrayDeque<Runnable>> lanes=new HashMap<>();
 private int accepted;private boolean closed;
 public KeyedSerialExecutor(Executor executor,int limit){this.executor=Objects.requireNonNull(executor);if(limit<1)throw new IllegalArgumentException("Queue limit");this.limit=limit;}
 public synchronized void execute(K key,Runnable task){
  Objects.requireNonNull(key);Objects.requireNonNull(task);
  if(closed||accepted>=limit)throw new RejectedExecutionException("Work queue unavailable");
  var lane=lanes.get(key);boolean fresh=lane==null;
  if(fresh){lane=new ArrayDeque<>();lanes.put(key,lane);}lane.add(Messages.capture(task));accepted++;
  if(fresh){var submitted=lane;try{executor.execute(()->drain(key,submitted));}
   catch(RuntimeException|Error failure){accepted-=submitted.size();submitted.clear();lanes.remove(key,submitted);throw failure;}}
 }
 private void drain(K key,ArrayDeque<Runnable> lane){
  while(true){Runnable task;synchronized(this){task=lane.poll();if(task==null){lanes.remove(key,lane);return;}}
   try{task.run();}
   catch(RuntimeException failure){System.getLogger(KeyedSerialExecutor.class.getName()).log(System.Logger.Level.WARNING,"Ordered task failed: "+failure.getClass().getSimpleName());}
   catch(Error fatal){synchronized(this){accepted-=lane.size();lane.clear();lanes.remove(key,lane);}throw fatal;}
   finally{synchronized(this){accepted--;}}
  }
 }
 /** Pending work is discarded; active operations finish without admitting another submission. */
 @Override public synchronized void close(){closed=true;for(var lane:lanes.values()){accepted-=lane.size();lane.clear();}lanes.clear();}
}
