package dev.abros.rivet.core.api;

import dev.abros.rivet.api.TerrainApi;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TerrainScheduleTest {
 @TempDir Path root;
 TerrainJournal.Chunk chunk(int x){return new TerrainJournal.Chunk("minecraft:overworld",x,0);}
 @Test void debounceAndChangesDuringRenderRemainPending(){var q=new TerrainDirtyQueue();q.mark(chunk(1),0);q.mark(chunk(1),10);assertEquals(1,q.size());assertNull(q.poll(19));var first=q.poll(20);q.mark(chunk(1),21);q.complete(first);assertEquals(1,q.size());var second=q.poll(21);q.complete(second);assertEquals(0,q.size());}
 @Test void boundedOverflowRequestsSweepAndRetryAllowsOtherWork(){var q=new TerrainDirtyQueue();for(int x=0;x<4100;x++)q.mark(chunk(x),0);assertEquals(4096,q.size());assertTrue(q.sweep());var first=q.poll(20);q.retry(first,20);assertNotEquals(first.chunk(),q.poll(21).chunk());var copy=new TerrainDirtyQueue();copy.restore(q.snapshot(),q.sweep());assertEquals(4096,copy.size());assertTrue(copy.sweep());}
 @Test void acknowledgedJobsAndDirtyLeasesSurviveReopen()throws Exception{
  UUID id=UUID.randomUUID();var job=new TerrainJournal.Job(id,null,new TerrainJournal.Cursor("minecraft:overworld",-3,2,123),0,-1,7,80,"WRITING");var state=new TerrainJournal.State(1,"generation",List.of(job),List.of(chunk(2)),true);
  try(var journal=new TerrainJournal(root)){journal.save(state).get(3,TimeUnit.SECONDS);}try(var journal=new TerrainJournal(root)){assertEquals(state,journal.load());assertEquals(id,journal.load().jobs().getFirst().id());}
 }
 @Test void cancelledAndQueuedJobsRetainedAndCoalescedSaveAcknowledged()throws Exception{
  var r=new TerrainApi.Region("minecraft:overworld",0,0,1,0);var a=new TerrainJournal.Job(UUID.randomUUID(),r,null,1,2,1,0,"CAPTURING");var b=new TerrainJournal.Job(UUID.randomUUID(),r,null,0,2,0,0,"CANCELLED");
  try(var journal=new TerrainJournal(root)){var first=journal.save(new TerrainJournal.State(1,"g",List.of(a),List.of(),false));var last=journal.save(new TerrainJournal.State(1,"g",List.of(a,b),List.of(chunk(3)),false));CompletableFuture.allOf(first,last).get(3,TimeUnit.SECONDS);assertEquals(2,journal.load().jobs().size());}
 }
 @Test void damagedJournalIsNotSilentlyDiscarded()throws Exception{Files.writeString(root.resolve("schedule.json"),"{broken");try(var journal=new TerrainJournal(root)){assertThrows(java.io.IOException.class,journal::load);}assertEquals("{broken",Files.readString(root.resolve("schedule.json")));}
 @Test void unknownVersionAndInvalidBoundsRejected()throws Exception{Files.writeString(root.resolve("schedule.json"),"{\"version\":2,\"generation\":\"x\",\"jobs\":[],\"dirty\":[],\"sweep\":false}");try(var journal=new TerrainJournal(root)){assertThrows(java.io.IOException.class,journal::load);assertThrows(IllegalArgumentException.class,()->journal.save(new TerrainJournal.State(1,"g",List.of(),List.of(new TerrainJournal.Chunk("../bad",0,0)),false)));}}
 @Test void continuouslyChangingChunkDoesNotStarveNeighbours(){var q=new TerrainDirtyQueue();q.mark(chunk(1),0);q.mark(chunk(2),0);var first=q.poll(20);q.mark(chunk(1),21);q.complete(first);assertEquals(chunk(2),q.poll(21).chunk());}

}
