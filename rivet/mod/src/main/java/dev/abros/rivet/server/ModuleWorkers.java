package dev.abros.rivet.server;

import java.util.concurrent.*;

/** Stop submissions before draining workers, while PostgreSQL is still open. */
final class ModuleWorkers {
 static void stop(ExecutorService... workers){
  for(var worker:workers)if(worker!=null)worker.shutdown();
  boolean interrupted=false;
  for(var worker:workers)if(worker!=null){
   try{if(!worker.awaitTermination(5,TimeUnit.SECONDS)){worker.shutdownNow();com.mojang.logging.LogUtils.getLogger().warn("Rivet worker shutdown timed out");}}
   catch(InterruptedException stop){worker.shutdownNow();interrupted=true;}
  }
  if(interrupted)Thread.currentThread().interrupt();
 }
}
