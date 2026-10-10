package dev.abros.rivet.server;

import java.util.HashSet;
import java.util.Set;

/** Game-thread bookkeeping; worker completions must return to the game thread. */
final class TaskStockWork {
 private final java.util.concurrent.Executor executor;
 private dev.abros.rivet.core.KeyedSerialExecutor<String> requests;
 TaskStockWork(){this(Runnable::run);}
 TaskStockWork(java.util.concurrent.Executor executor){this.executor=executor;requests=new dev.abros.rivet.core.KeyedSerialExecutor<>(executor,256);}
 void schedule(String location,Runnable action){requests.execute(location,action);}
 private final Set<String> pending=new HashSet<>();
 private long generation;
 long generation(){return generation;}
 void reset(){requests.close();requests=new dev.abros.rivet.core.KeyedSerialExecutor<>(executor,256);generation++;pending.clear();}
 boolean begin(String key){return pending.add(key);}
 boolean pending(String key){return pending.contains(key);}
 void cancel(String key){pending.remove(key);}
 void complete(long token,String key,Runnable result){
  if(token!=generation)return;
  try{result.run();}finally{pending.remove(key);}
 }
}
