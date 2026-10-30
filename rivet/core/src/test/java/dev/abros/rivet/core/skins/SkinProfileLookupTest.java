package dev.abros.rivet.core.skins;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkinProfileLookupTest {
 @Test void expiredReplyCannotPopulateCacheAndFailedSendCanRetry(){
  var lookup=new SkinProfileLookup();var sent=new ArrayList<String>();
  var first=lookup.resolve("one",0,(k,r)->sent.add(r));
  assertFalse(lookup.receive(sent.getFirst(),Optional.of(new SkinProfileLookup.Identity(UUID.randomUUID(),"Player")),5000));assertTrue(first.join().isEmpty());
  assertTrue(lookup.resolve("one",5001,(k,r)->{throw new IllegalStateException("Disconnected");}).join().isEmpty());
  var retry=lookup.resolve("one",5002,(k,r)->sent.add(r));assertFalse(retry.isDone());assertEquals(2,sent.size());
 }
 @Test void coalescesCompletesAndRejectsLateRepliesAfterReconnect(){
  var lookup=new SkinProfileLookup();var sent=new ArrayList<String>();var first=lookup.resolve("name:Player",0,(k,r)->sent.add(r));
  assertSame(first,lookup.resolve("name:Player",1,(k,r)->sent.add(r)));assertEquals(1,sent.size());
  lookup.clear();assertTrue(first.join().isEmpty());assertFalse(lookup.receive(sent.getFirst(),Optional.of(new SkinProfileLookup.Identity(UUID.randomUUID(),"Player")),2));
  var next=lookup.resolve("name:Player",3,(k,r)->sent.add(r));var id=new SkinProfileLookup.Identity(UUID.randomUUID(),"Player");
  assertTrue(lookup.receive(sent.getLast(),Optional.of(id),4));assertEquals(id,next.join().orElseThrow());
 }
 @Test void timeoutAndNegativeResultsAllowRetryWithoutAnUnboundedQueue(){
  var lookup=new SkinProfileLookup();var ids=new ArrayList<String>();var first=lookup.resolve("one",0,(k,r)->ids.add(r));
  for(int i=0;i<63;i++)lookup.resolve("other"+i,0,(k,r)->ids.add(r));
  assertTrue(lookup.resolve("overflow",1,(k,r)->fail()).join().isEmpty());assertEquals(64,ids.size());
  lookup.tick(5000);assertTrue(first.join().isEmpty());var retry=lookup.resolve("one",5001,(k,r)->ids.add(r));
  lookup.receive(ids.getLast(),Optional.empty(),5002);assertTrue(retry.join().isEmpty());
  lookup.resolve("one",6000,(k,r)->fail());lookup.resolve("one",7002,(k,r)->ids.add(r));assertEquals(66,ids.size());
 }
}
