package dev.abros.rivet.core;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** At most one queued or running operation per key, for one owner's lifetime. */
public final class CoalescingExecutor<K> implements AutoCloseable {
 private final Executor executor;
 private final Set<K> pending=new HashSet<>();
 private boolean closed;
 public CoalescingExecutor(Executor executor){this.executor=Objects.requireNonNull(executor);}
 /** False means an existing operation already covers this key. Rejection never reserves the key. */
 public synchronized boolean execute(K key,Runnable task){
  Objects.requireNonNull(key);Objects.requireNonNull(task);
  if(closed)throw new RejectedExecutionException("Work owner closed");
  if(!pending.add(key))return false;
  try{executor.execute(()->{
   try{synchronized(this){if(closed)return;}task.run();}
   finally{synchronized(this){pending.remove(key);}}
  });}catch(RuntimeException|Error failure){pending.remove(key);throw failure;}
  return true;
 }
 /** Accepted active work may finish; queued work is skipped and new submissions rejected. */
 @Override public synchronized void close(){closed=true;pending.clear();}
}
