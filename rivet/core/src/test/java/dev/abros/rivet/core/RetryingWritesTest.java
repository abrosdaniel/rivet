package dev.abros.rivet.core;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RetryingWritesTest {
 @Test void failedEvictedSnapshotSurvivesAndRetriesAfterBackoff(){
  var writes=new RetryingWrites<String,String>(2);var saved=new ArrayList<String>();int[] attempts={0};
  assertTrue(writes.put("world/tile","explored",v->{if(attempts[0]++==0)throw new IOException();saved.add(v);}));
  writes.drain(0,10,false,(k,e)->{});assertEquals("explored",writes.pending("world/tile"));
  writes.drain(4999,10,false,(k,e)->fail());assertEquals(1,attempts[0]);
  writes.drain(5000,10,false,(k,e)->fail());assertEquals(List.of("explored"),saved);assertEquals(0,writes.size());
 }
 @Test void newerEditIsNotAcknowledgedOrOverwrittenByOldCompletion(){
  var writes=new RetryingWrites<String,String>(2);var saved=new ArrayList<String>();
  writes.put("markers","old",v->{saved.add(v);writes.put("markers","new",saved::add);});
  writes.drain(0,10,false,(k,e)->fail());assertEquals("new",writes.pending("markers"));
  writes.drain(1,10,false,(k,e)->fail());assertEquals(List.of("old","new"),saved);assertEquals(0,writes.size());
 }
 @Test void repositoryKeysStayIsolatedAndCapacityNeverDropsAcceptedData(){
  var writes=new RetryingWrites<String,String>(2);var saved=new ArrayList<String>();
  writes.put("a/markers","A",saved::add);writes.put("b/markers","B",saved::add);
  assertFalse(writes.put("c/markers","C",saved::add));assertTrue(writes.put("a/markers","A2",saved::add));
  writes.drain(0,10,true,(k,e)->fail());assertEquals(List.of("A2","B"),saved);
 }
}
