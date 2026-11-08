package dev.abros.rivet.core.pack;
import dev.abros.rivet.core.*;
import java.nio.file.*;
import java.util.concurrent.*;

/** Serves only the running official bundle, after signature and byte verification. */
public final class CoreRelay implements AutoCloseable {
 public record Snapshot(CoreUpdater.Update update,Path file){}
 private volatile Snapshot snapshot;
 private boolean closed;
 private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"Rivet signed update relay");t.setDaemon(true);return t;});
 public CoreRelay(Path directory,Path bundle,String version,String loader){
  worker.execute(()->{try{Path proof=directory.resolve(version+".json");if(Files.isRegularFile(proof)&&Files.size(proof)<=1024*1024)accept(Json.read(proof),bundle,version,loader);}catch(Exception ignored){}});
  worker.scheduleWithFixedDelay(()->{try{var remote=new Remote();remote.cacheMetadata(directory.resolve("http"));var update=new CoreUpdater(remote,directory.resolve("metadata")).signedRelease(version,"1.21.1",loader).orElseThrow();accept(update.proof(),bundle,version,loader);Json.write(directory.resolve(version+".json"),update.proof());}catch(Exception failure){System.getLogger(CoreRelay.class.getName()).log(System.Logger.Level.DEBUG,"Signed update relay not ready",failure);}},0,10,TimeUnit.MINUTES);
 }
 public void accept(com.google.gson.JsonObject proof,Path bundle,String version,String loader)throws Exception{
  var update=SignedUpdates.update(proof,"1.21.1",loader).orElseThrow();if(!update.version().equals(version)||!Files.isRegularFile(bundle,LinkOption.NOFOLLOW_LINKS)||Files.size(bundle)!=update.artifact().size()||!Hashes.sha256(bundle).equals(update.artifact().sha256()))throw new java.io.IOException("Running bundle does not match signed release");synchronized(this){if(closed)throw new java.io.IOException("Update relay stopped");snapshot=new Snapshot(update,bundle.toRealPath());}
 }
 public Snapshot snapshot(){return snapshot;}
 public void close(){synchronized(this){closed=true;snapshot=null;}worker.shutdownNow();try{if(!worker.awaitTermination(5,TimeUnit.SECONDS))System.getLogger(CoreRelay.class.getName()).log(System.Logger.Level.WARNING,"Update relay worker is still stopping");}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
