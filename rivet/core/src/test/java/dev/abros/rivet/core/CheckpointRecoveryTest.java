package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class CheckpointRecoveryTest {
 @Test void failedCheckpointLogsOnceAndResumesOnlyAfterDurableRetry(){
  var state=new CheckpointRecovery();var broken=CompletableFuture.<Void>failedFuture(new java.io.IOException());var retry=new CompletableFuture<Void>();int[] writes={0},logs={0};
  for(int i=0;i<1000;i++)assertFalse(state.ready(broken,i,()->{writes[0]++;return retry;},e->logs[0]++));
  assertEquals(1,logs[0]);assertEquals(0,writes[0]);
  assertFalse(state.ready(broken,5000,()->{writes[0]++;return retry;},e->logs[0]++));
  assertFalse(state.ready(broken,6000,()->{throw new AssertionError();},e->fail()));
  retry.complete(null);assertTrue(state.ready(retry,6001,()->{throw new AssertionError();},e->fail()));assertEquals(1,writes[0]);
 }
 @Test void repeatedDiskFailureBacksOff(){
  var state=new CheckpointRecovery();var bad=CompletableFuture.<Void>failedFuture(new java.io.IOException());int[] writes={0};
  for(int i=0;i<30000;i++)state.ready(bad,i,()->{writes[0]++;return bad;},e->{});
  assertEquals(2,writes[0]);
 }
 @Test void successfulRetryCannotHideNewerFailedCheckpoint(){
  var state=new CheckpointRecovery();var bad=CompletableFuture.<Void>failedFuture(new java.io.IOException());var retry=new CompletableFuture<Void>();
  assertFalse(state.ready(bad,0,()->retry,e->{}));
  assertFalse(state.ready(bad,5000,()->retry,e->{}));retry.complete(null);
  var newer=CompletableFuture.<Void>failedFuture(new java.io.IOException());
  assertFalse(state.ready(newer,5001,()->{throw new AssertionError();},e->{}));
 }
 @Test void fileFailureRecoversQueuedJobsAndSurvivesReopening(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root)throws Exception{
  var path=root.resolve("schedule.json");java.nio.file.Files.createDirectory(path);java.nio.file.Files.writeString(path.resolve("block"),"blocked");
  var job=new dev.abros.rivet.core.api.TerrainJournal.Job(java.util.UUID.randomUUID(),null,null,0,-1,0,0,"QUEUED");
  var snapshot=new dev.abros.rivet.core.api.TerrainJournal.State(1,"generation",java.util.List.of(job),java.util.List.of(new dev.abros.rivet.core.api.TerrainJournal.Chunk("minecraft:overworld",1,2)),false);
  try(var journal=new dev.abros.rivet.core.api.TerrainJournal(root)){
   var failed=journal.save(snapshot);assertThrows(ExecutionException.class,()->failed.get(5,TimeUnit.SECONDS));
   var state=new CheckpointRecovery();assertFalse(state.ready(failed,0,()->journal.save(snapshot),e->{}));
   java.nio.file.Files.delete(path.resolve("block"));java.nio.file.Files.delete(path);
   var retried=new java.util.concurrent.atomic.AtomicReference<CompletableFuture<Void>>();
   assertFalse(state.ready(failed,5000,()->{var f=journal.save(snapshot);retried.set(f);return f;},e->fail()));
   retried.get().get(5,TimeUnit.SECONDS);assertTrue(state.ready(retried.get(),5001,()->{throw new AssertionError();},e->fail()));
  }
  try(var reopened=new dev.abros.rivet.core.api.TerrainJournal(root)){assertEquals(snapshot,reopened.load());}
 }
}
