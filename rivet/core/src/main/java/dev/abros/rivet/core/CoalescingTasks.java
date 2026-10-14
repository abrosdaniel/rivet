package dev.abros.rivet.core;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
/** At most one queued worker, with the latest operation retained per bounded key. */
public final class CoalescingTasks<K> {
 private final Executor executor;private final int capacity;private final Consumer<RuntimeException> errors;
 private final LinkedHashMap<K,Runnable> pending=new LinkedHashMap<>();private boolean scheduled;
 public CoalescingTasks(Executor executor,int capacity,Consumer<RuntimeException> errors){this.executor=Objects.requireNonNull(executor);if(capacity<1)throw new IllegalArgumentException("Queue capacity");this.capacity=capacity;this.errors=Objects.requireNonNull(errors);}
 public synchronized void submit(K key,Runnable task){
  Objects.requireNonNull(key);Objects.requireNonNull(task);
  if(!pending.containsKey(key)&&pending.size()>=capacity)throw new RejectedExecutionException("Draft queue is full");
  pending.put(key,task);if(scheduled)return;scheduled=true;
  try{executor.execute(this::drain);}catch(RuntimeException|Error failure){scheduled=false;pending.remove(key);throw failure;}
 }
 private void drain(){try{while(true){Runnable task;synchronized(this){if(pending.isEmpty()){scheduled=false;return;}var key=pending.keySet().iterator().next();task=pending.remove(key);}try{task.run();}catch(RuntimeException failure){errors.accept(failure);}}}catch(RuntimeException|Error failure){
  // An aborted worker must not leave future saves believing a drain is still running.
  synchronized(this){scheduled=false;}throw failure;
 }}
}
