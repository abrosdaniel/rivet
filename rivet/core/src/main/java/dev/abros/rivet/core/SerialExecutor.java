package dev.abros.rivet.core;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.*;
/** Bounded ordering per session on a shared worker pool. */
public final class SerialExecutor implements Executor {
 private final Executor executor;private final int limit;private final ArrayDeque<Runnable> queue=new ArrayDeque<>();
 private boolean running,active,closed;private Runnable cleanup;
 public SerialExecutor(Executor executor,int limit){this.executor=Objects.requireNonNull(executor);if(limit<1)throw new IllegalArgumentException("Session queue limit");this.limit=limit;}
 public synchronized void execute(Runnable task){
  if(closed)throw new RejectedExecutionException("Session queue closed");
  if(queue.size()>=limit)throw new RejectedExecutionException("Session queue full");
  Runnable localized=Messages.capture(Objects.requireNonNull(task));queue.add(localized);if(running)return;running=true;
  try{executor.execute(this::drain);}catch(RejectedExecutionException e){running=false;queue.remove(localized);throw e;}
 }
 /** Discard pending work; clean up once after the active task, without another pool submission. */
 public void close(Runnable afterActive){
  Objects.requireNonNull(afterActive);Runnable finish=null;
  synchronized(this){if(closed)return;closed=true;queue.clear();if(active)cleanup=afterActive;else finish=afterActive;}
  if(finish!=null)finish.run();
 }
 private void drain(){while(true){Runnable task;synchronized(this){task=queue.poll();if(task==null){running=false;return;}active=true;}
  try{task.run();}
  catch(RuntimeException e){System.getLogger(SerialExecutor.class.getName()).log(System.Logger.Level.WARNING,"Session task failed: "+e.getClass().getSimpleName());}
  catch(Error fatal){close(()->{});throw fatal;}
  finally{Runnable finish;synchronized(this){active=false;finish=cleanup;cleanup=null;if(closed)running=false;}if(finish!=null)finish.run();}
 }}
}
