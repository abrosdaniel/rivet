package dev.abros.rivet.server;

import java.time.Duration;
import java.util.concurrent.*;
import dev.abros.rivet.core.WorkerShutdown;

/** Stop submissions and await interrupted workers while PostgreSQL is still open. */
final class ModuleWorkers {
 static void stop(ExecutorService... workers){
  if(!WorkerShutdown.stop(Duration.ofSeconds(5),Duration.ofSeconds(5),workers))
   com.mojang.logging.LogUtils.getLogger().warn("Rivet workers still running after forced shutdown timeout");
 }
}
