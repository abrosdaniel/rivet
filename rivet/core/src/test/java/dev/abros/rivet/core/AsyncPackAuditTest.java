package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
class AsyncPackAuditTest {
 @Test void openingMenusNeverReadsOnCallerAndCoalescesReads(){
  var queued=new ArrayDeque<Runnable>();var now=new AtomicLong();int[] reads={0};
  var audit=new AsyncPackAudit(queued::add,()->{reads[0]++;return "valid";},now::get);
  for(int i=0;i<100;i++)assertEquals("checking",audit.snapshot());
  assertEquals(0,reads[0]);assertEquals(1,queued.size());queued.remove().run();
  for(int i=0;i<100;i++)assertEquals("valid",audit.snapshot());assertEquals(1,reads[0]);
  now.set(30000);audit.snapshot();assertEquals(1,queued.size());
 }
 @Test void invalidationRejectsOldCompletionAndFailureCanRecover(){
  var queued=new ArrayDeque<Runnable>();var now=new AtomicLong();int[] reads={0};
  var audit=new AsyncPackAudit(queued::add,()->{if(++reads[0]==2)throw new java.io.IOException();return "good";},now::get);
  audit.snapshot();audit.invalidate();audit.snapshot();queued.remove().run();assertEquals("checking",audit.snapshot());
  queued.remove().run();assertEquals("invalid",audit.snapshot());now.set(30000);audit.snapshot();queued.remove().run();assertEquals("good",audit.snapshot());
 }
 @Test void connectionVerificationSeedsSnapshotAndRejectsOlderBackgroundResult(){
  var queued=new ArrayDeque<Runnable>();var audit=new AsyncPackAudit(queued::add,()->"old",()->0L);
  audit.snapshot();audit.confirmed("verified");queued.remove().run();assertEquals("verified",audit.snapshot());assertTrue(queued.isEmpty());
 }
}
