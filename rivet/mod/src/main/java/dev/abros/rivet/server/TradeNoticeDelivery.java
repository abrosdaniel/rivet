package dev.abros.rivet.server;
import dev.abros.rivet.core.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
/** Coalesced durable projection of the trade journal into the personal notification inbox. */
final class TradeNoticeDelivery implements AutoCloseable {
 private final Path checkpoint;
 private final Map<String,Long> delivered=new HashMap<>();
 private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"Rivet trade notifications");t.setDaemon(true);return t;});
 private boolean running,closed;private long retryAt,settledSequence=-1;
 TradeNoticeDelivery(Path checkpoint)throws Exception{this.checkpoint=checkpoint;if(Files.exists(checkpoint))for(var e:Json.read(checkpoint).entrySet())delivered.put(e.getKey(),e.getValue().getAsLong());}
 synchronized void offer(Map<String,TradeBook.Notice> notices,long sequence){
  if(closed||running||sequence==settledSequence||System.currentTimeMillis()<retryAt)return;
  var pending=notices.entrySet().stream().filter(e->e.getValue().sequence()>delivered.getOrDefault(e.getKey(),0L)).limit(32).map(e->Map.entry(e.getKey(),e.getValue())).toList();
  if(pending.isEmpty()){settledSequence=sequence;return;}running=true;
  worker.execute(()->{try{
   var store=new NotificationStore(ServerDatabase.get());
   for(var entry:pending){store.trade(entry.getValue());synchronized(this){delivered.put(entry.getKey(),entry.getValue().sequence());Json.write(checkpoint,delivered);}}
   synchronized(this){if(pending.size()<32)settledSequence=sequence;}
  }catch(Exception failure){synchronized(this){retryAt=System.currentTimeMillis()+10000;}com.mojang.logging.LogUtils.getLogger().warn("Trade notifications will retry from the journal",failure);
  }finally{synchronized(this){running=false;}}});
 }
 public void close(){synchronized(this){closed=true;}worker.shutdown();try{worker.awaitTermination(30,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
